package com.jiphyeonjeon.choseongschool.server.service;

import com.jiphyeonjeon.choseongschool.common.GameConstants;
import com.jiphyeonjeon.choseongschool.common.GameConstants.Protocol;
import com.jiphyeonjeon.choseongschool.server.entity.Word;
import com.jiphyeonjeon.choseongschool.common.model.GameMode;
import com.jiphyeonjeon.choseongschool.common.model.ItemKind;
import com.jiphyeonjeon.choseongschool.common.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.common.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.common.network.LineMessages;
import com.jiphyeonjeon.choseongschool.server.GameEvents;
import com.jiphyeonjeon.choseongschool.common.model.Item;
import com.jiphyeonjeon.choseongschool.server.timer.RoundTimer;
import com.jiphyeonjeon.choseongschool.server.timer.TimerListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * 방(LobbyServer) 하나당 게임 한 판을 진행한다. 메시지 형식은 GameConstants.Protocol을 따른다.
 * <p>
 * 스레드 모델: 게임 상태는 synchronized(this)로 보호한다.
 * 단, 잠근 채로 broadcast하지 않는다. 먼저 잠근 채 보낼 줄을 목록에 모으고, 잠금을 놓은 뒤에 보낸다.
 * LobbyServer는 방을 잠근 채 이 클래스를 부르고 broadcast도 방을 잠그기 때문에,
 * 이 클래스의 잠금을 쥔 채 broadcast하면 서로 상대의 잠금을 기다리며 멈출 수 있다(교착 상태).
 * (순서는 항상 방 잠금 → 이 클래스 잠금이고, 이 클래스 잠금 → 방 잠금은 만들지 않는다.)
 * DB 조회는 방을 막지 않도록 별도 Thread에서 한다.
 * epoch 값으로 "이미 끝난 라운드/게임"에서 늦게 도착한 일을 무시한다.
 */
@Service
public class GameService implements GameEvents, DisposableBean {

    private static final int BASE_SCORE = 100;
    private static final int NEXT_ROUND_DELAY_MS = 3000;

    private static final class PlayerState {
        final ParticipantInfo info;
        int score;

        PlayerState(ParticipantInfo info) {
            this.info = info;
        }
    }

    private final WordService wordService;
    private final Random random = new Random();

    // 아래 상태는 모두 synchronized(this) 안에서만 읽고 쓴다
    // 플레이어와 점수를 한 맵으로 관리 (두 맵이 어긋나는 문제 방지)
    private final Map<String, PlayerState> players = new LinkedHashMap<>();

    private boolean finished = true;
    private LobbySettings settings;
    private List<Word> roundWords = List.of();
    private int currentRoundIndex;
    private Word currentWord;
    private String currentAnswer;          // 정규화된 정답 (라운드 시작 때 한 번만 계산)
    private RoundTimer currentTimer;
    private Thread nextRoundThread;
    private Consumer<String> broadcast;
    private boolean roundActive;
    private long epoch;                    // 라운드 시작/게임 종료/방 비움마다 증가

    private final ItemService itemService; // 아이템전 관리자

    public GameService(WordService wordService, ItemService itemService) {
        this.wordService = wordService;
        this.itemService = itemService;
    }

    @Override
    public void onStart(List<ParticipantInfo> participants, LobbySettings settings, Consumer<String> broadcast) {
        final long startEpoch;
        synchronized (this) {
            resetRound();
            finished = false;
            this.settings = settings;
            this.broadcast = broadcast;
            this.currentRoundIndex = 0;
            this.roundWords = List.of();
            this.currentWord = null;
            this.currentAnswer = null;
            players.clear();
            for (ParticipantInfo p : participants) {
                players.put(p.id(), new PlayerState(p));
            }
            startEpoch = epoch;
        }
        // LobbyServer가 방을 잠근 채 부르므로, DB 조회는 별도 Thread에서 한다
        runInThread("game-start", () -> loadWordsAndStart(settings, startEpoch));
    }

    /** 게임당 한 번만 문제를 뽑고 첫 라운드를 시작한다 */
    private void loadWordsAndStart(LobbySettings settings, long startEpoch) {
        List<Word> words;
        try {
            words = wordService.readByLevel(settings.difficulty().getLabel(), settings.rounds());
        } catch (RuntimeException e) {
            System.err.println("문제를 가져오지 못함: " + e);
            words = List.of(); // 문제가 없으면 바로 게임을 끝내 방이 대기 상태로 돌아가게 한다
        }
        List<String> wordsForItems = List.of();
        if (settings.mode() == GameMode.ITEM) {
            wordsForItems = wordService.readItemWords();
        }
        List<String> out = new ArrayList<>();
        RoundTimer timer;
        long roundEpoch;
        synchronized (this) {
            if (epoch != startEpoch || finished) return; // 조회하는 동안 방이 비었거나 새 게임이 시작됨
            roundWords = words;
            itemService.startGame(wordsForItems);
            addScores(out);
            timer = prepareNextRound(out);
            roundEpoch = epoch;
        }
        flush(out);
        startTimer(timer, roundEpoch);
    }

    /**
     * 다음 문제를 골라 출제 줄을 out에 담고 타이머를 만든다(시작은 줄을 보낸 뒤). 문제가 없으면 게임을 끝낸다.
     * synchronized(this) 안에서만 부른다.
     */
    private RoundTimer prepareNextRound(List<String> out) {
        while (currentRoundIndex < roundWords.size()) {
            Word word = roundWords.get(currentRoundIndex++);
            // 라운드 번호를 먼저, 문제(초성이 포함된 예문)를 다음에 보낸다
            String roundLine = line(Protocol.ROUND, currentRoundIndex, roundWords.size());
            String questionLine = line(Protocol.QUESTION, word.getExample());
            try {
                LineMessages.validate(roundLine);
                LineMessages.validate(questionLine);
            } catch (RuntimeException e) {
                // 예문이 프로토콜 검증에 걸리는 등 출제에 실패하면 그 단어만 건너뛴다
                System.err.println("출제 실패로 건너뜀: " + e.getMessage());
                continue;
            }
            out.add(roundLine);
            out.add(questionLine);

            currentWord = word;
            currentAnswer = normalize(word.getWord());
            roundActive = true;
            final long roundEpoch = ++epoch;
            final int totalSeconds = settings.seconds();

            currentTimer = new RoundTimer(totalSeconds, new TimerListener() {
                @Override
                public void onTick(int remainingSeconds) {
                    sendIfCurrent(roundEpoch, line(Protocol.TIME, remainingSeconds, totalSeconds));
                }

                @Override
                public void onHintTime() {
                    sendIfCurrent(roundEpoch, line(Protocol.HINT, word.getMeaning()));
                }

                @Override
                public void onTimeOut() {
                    timeOut(roundEpoch);
                }
            });
            planItem(roundEpoch);
            return currentTimer;
        }
        finishGame(out);
        return null;
    }

    /** 출제 줄을 보낸 뒤에 타이머를 시작한다. 그 사이에 라운드가 끝났다면 시작하지 않는다 */
    private void startTimer(RoundTimer timer, long roundEpoch) {
        if (timer == null) return;
        synchronized (this) {
            if (roundActive && epoch == roundEpoch && currentTimer == timer) {
                timer.start();
            }
        }
    }

    // 사용자 정답 입력
    @Override
    public void onAnswer(ParticipantInfo player, String answer, Consumer<String> unused) {
        List<String> out = new ArrayList<>();
        synchronized (this) {
            // 게임에 없는 사람(이미 나간 사람 등)의 입력은 무시
            PlayerState state = players.get(player.id());
            if (finished || state == null || currentWord == null) return;
            
            // 약과에 막힌 사용자의 답안은 버림
            if (itemService.isBlocked(player.id())) return; 
            
            String trimmed = answer.trim();
            Item item = itemService.take(trimmed); // 사용자가 아이템을 획득했는지 확인
            if (item != null) {
                useItem(item, player, state, out); // 아이템 단어면 정답 판정 대신 아이템 사용
            } else {
                judge(player, state, trimmed, out);
            }
        }
        flush(out);
    }

    /** 정답 판정. O는 이번 라운드의 첫 정답자뿐이다. 오답이거나 이미 끝난 라운드의 답은 X. synchronized(this) 안에서만 부른다 */
    private void judge(ParticipantInfo player, PlayerState state, String trimmed, List<String> out) {
        boolean correct = roundActive && normalize(trimmed).equals(currentAnswer);
        out.add(line(Protocol.ANSWER, player.nickname(), trimmed, correct ? Protocol.CORRECT : Protocol.WRONG));
        if (correct) {
            state.score += BASE_SCORE;
            out.add(line(Protocol.SCORE, player.nickname(), state.score));
            endRound(out);
        }
    }

    // ===================== 묘수전 아이템 (규칙은 ItemService) =====================

    /** 이번 라운드에 아이템을 낼지 정하고, 낸다면 잠시 뒤에 띄운다. synchronized(this) 안에서만 부른다 */
    private void planItem(long roundEpoch) {
        if (settings.mode() != GameMode.ITEM) {
            return;
        }
        boolean lastRound = currentRoundIndex == roundWords.size();
        if (!itemService.shouldShow(lastRound)) {
            return;
        }
        int delay = itemService.delayMillis(lastRound);
        runInThread("game-item", () -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                return;
            }
            showItem(roundEpoch);
        });
    }

    /** 아이템을 띄운다. 그 사이 라운드가 끝났으면 띄우지 않는다 */
    private void showItem(long roundEpoch) {
        String line;
        synchronized (this) {
            if (!roundActive || epoch != roundEpoch) {
                return;
            }
            line = itemService.show(currentWord.getWord());
        }
        if (line != null) {
            flush(List.of(line));
        }
    }

    /** 먹은 아이템의 효과를 적용한다. synchronized(this) 안에서만 부른다 */
    private void useItem(Item item, ParticipantInfo player, PlayerState state, List<String> out) {
        out.add(line(Protocol.ITEM_GET, player.nickname(), item.getKind().name()));

        if (item.getKind() == ItemKind.GOTGAM) {
            // 1등과 점수를 바꾼다
            PlayerState top = findTop();
            int temp = top.score;
            top.score = state.score;
            state.score = temp;
            out.add(line(Protocol.SCORE, player.nickname(), state.score));
            out.add(line(Protocol.SCORE, top.info.nickname(), top.score));
        } else {
            itemService.block(player.id());
            out.add(line(Protocol.BLOCK, player.nickname(), GameConstants.Item.BLOCK_SECONDS));
        }
    }

    /** 점수가 가장 높은 학동. 공동 1등이면 그중 무작위 한 명. synchronized(this) 안에서만 부른다 */
    private PlayerState findTop() {
        int best = -1;
        for (PlayerState p : players.values()) {
            best = Math.max(best, p.score);
        }
        List<PlayerState> tops = new ArrayList<>();
        for (PlayerState p : players.values()) {
            if (p.score == best) {
                tops.add(p);
            }
        }
        return tops.get(random.nextInt(tops.size()));
    }

    /** 시간이 다 됐을 때 (타이머 스레드에서 호출) */
    private void timeOut(long roundEpoch) {
        List<String> out = new ArrayList<>();
        synchronized (this) {
            if (!roundActive || epoch != roundEpoch) return;
            endRound(out);
        }
        flush(out);
    }

    /** 정답이 나왔거나 시간이 다 됐을 때: 정답과 뜻을 공개하고 잠시 뒤 다음 라운드로. synchronized(this) 안에서만 부른다 */
    private void endRound(List<String> out) {
        roundActive = false;
        stopCurrentTimer();
        itemService.hideItem(); // 라운드가 끝나면 떠 있던 아이템도 사라진다 (화면은 ROUND_END를 받고 지운다)
        out.add(line(Protocol.ROUND_END, currentWord.getWord(), currentWord.getMeaning()));
        scheduleNextRound();
    }

    /** 현재 점수를 한 명씩 담는다. synchronized(this) 안에서만 부른다 */
    private void addScores(List<String> out) {
        for (PlayerState p : players.values()) {
            out.add(line(Protocol.SCORE, p.info.nickname(), p.score));
        }
    }

    /** 3초 뒤 다음 라운드를 시작하는 Thread. 그 사이 방이 비거나 새 게임이 시작되면 아무것도 하지 않는다 */
    private void scheduleNextRound() {
        final long scheduledEpoch = epoch;
        nextRoundThread = runInThread("game-next-round", () -> {
            try {
                Thread.sleep(NEXT_ROUND_DELAY_MS);
            } catch (InterruptedException e) {
                return; // resetRound가 깨운 경우
            }
            startNextRound(scheduledEpoch);
        });
    }

    private void startNextRound(long scheduledEpoch) {
        List<String> out = new ArrayList<>();
        RoundTimer timer = null;
        long roundEpoch = 0;
        synchronized (this) {
            if (epoch != scheduledEpoch) return;
            nextRoundThread = null;
            try {
                timer = prepareNextRound(out);
            } catch (RuntimeException e) {
                System.err.println("다음 라운드 시작 실패: " + e);
                out.clear();
                finishGame(out);
            }
            roundEpoch = epoch;
        }
        flush(out);
        startTimer(timer, roundEpoch);
    }

    /** 점수 높은 순으로 최종 결과를 담는다. LobbyServer는 GAME_END를 보고 방을 대기 상태로 되돌린다. synchronized(this) 안에서만 부른다 */
    private void finishGame(List<String> out) {
        resetRound();
        finished = true;

        List<PlayerState> ranking = new ArrayList<>(players.values());
        ranking.sort(Comparator.comparingInt((PlayerState p) -> p.score).reversed());

        List<Object> fields = new ArrayList<>();
        for (PlayerState p : ranking) {
            fields.add(p.info.nickname());
            fields.add(p.score);
        }
        out.add(line(Protocol.GAME_END, fields.toArray()));
    }

    @Override
    public void onLeave(ParticipantInfo player) {
        synchronized (this) {
            players.remove(player.id());
        }
        // LobbyServer가 방을 잠근 채 부르고, 이 뒤에 PLAYERS를 보낸다. 화면은 PLAYERS를 받으면 학동 줄을 다시 그리므로
        // 남은 사람의 점수는 PLAYERS 다음에 가야 한다. 별도 Thread의 전송은 방 잠금이 풀린 뒤에야 나가므로 순서가 맞는다
        runInThread("game-leave", () -> {
            List<String> out = new ArrayList<>();
            synchronized (this) {
                if (!finished && !players.isEmpty()) addScores(out);
            }
            flush(out);
        });
    }

    @Override
    public void onRoomEmpty() {
        synchronized (this) {
            resetRound();
            finished = true;
            players.clear();
            roundWords = List.of();
            currentRoundIndex = 0;
            currentWord = null;
            currentAnswer = null;
        }
    }

    @Override
    public void destroy() {
        synchronized (this) {
            resetRound(); // 라운드 타이머 정리
        }
    }

    /**
     * 진행 중인 라운드·타이머·예약 작업을 모두 무효화한다. synchronized(this) 안에서만 부른다.
     */
    private void resetRound() {
        epoch++;
        roundActive = false;
        stopCurrentTimer();
        itemService.clear();
        if (nextRoundThread != null) {
            if (nextRoundThread != Thread.currentThread()) {
                nextRoundThread.interrupt();
            }
            nextRoundThread = null;
        }
    }

    private void stopCurrentTimer() {
        if (currentTimer != null) {
            currentTimer.stop();
            currentTimer = null;
        }
    }

    /** 타이머가 부른 줄을, 그 라운드가 아직 진행 중일 때만 보낸다 */
    private void sendIfCurrent(long roundEpoch, String line) {
        synchronized (this) {
            if (!roundActive || epoch != roundEpoch) return;
        }
        flush(List.of(line));
    }

    /** 이름|값|값... 한 줄로 만든다 */
    private static String line(String type, Object... fields) {
        StringBuilder line = new StringBuilder(type);
        for (Object field : fields) {
            line.append(Protocol.SEPARATOR).append(field);
        }
        return line.toString();
    }

    /**
     * 모은 줄을 순서대로 보낸다. synchronized(this) 밖에서 부른다.
     * 한 줄이 검증에 걸려 예외가 나도 나머지 줄은 보낸다.
     */
    private void flush(List<String> lines) {
        Consumer<String> target;
        synchronized (this) {
            target = broadcast;
        }
        if (target == null) return;
        for (String line : lines) {
            try {
                target.accept(line);
            } catch (RuntimeException e) {
                System.err.println("전송 실패: " + e.getMessage());
            }
        }
    }

    private static Thread runInThread(String name, Runnable task) {
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                System.err.println("게임 처리 실패: " + e);
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 맥 등에서 들어오는 분리형(NFD) 한글을 조합형(NFC)으로 맞춰 비교한다.
     */
    private static String normalize(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFC);
    }
}
