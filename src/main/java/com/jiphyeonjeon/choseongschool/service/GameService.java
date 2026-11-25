package com.jiphyeonjeon.choseongschool.service;

import com.jiphyeonjeon.choseongschool.entity.Word;
import com.jiphyeonjeon.choseongschool.lobby.model.Difficulty;
import com.jiphyeonjeon.choseongschool.lobby.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.lobby.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.lobby.server.GameEvents;
import com.jiphyeonjeon.choseongschool.timer.RoundTimer;
import com.jiphyeonjeon.choseongschool.timer.TimerListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 방(LobbyServer) 하나당 게임 한 판을 진행한다.
 * <p>
 * 스레드 모델: 방 이벤트 스레드(onStart/onAnswer/onLeave), 라운드 타이머 스레드,
 * 다음 라운드 스케줄러 스레드가 모두 같은 상태를 건드린다. 상태 변경은 전부 this 락 안에서 하고,
 * epoch 값으로 "이미 끝난 라운드/게임"에서 늦게 도착한 콜백을 무시한다.
 */
@Service
public class GameService implements GameEvents, DisposableBean {

    private static final int BASE_SCORE = 100;
    private static final int NEXT_ROUND_DELAY_SEC = 3;


    private static final class PlayerState {
        final ParticipantInfo info;
        int score;

        PlayerState(ParticipantInfo info) {
            this.info = info;
        }
    }

    private final WordService wordService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "game-round-scheduler");
        t.setDaemon(true);
        return t;
    });

    // 플레이어와 점수를 한 맵으로 관리 (두 맵이 어긋나는 문제 방지)
    private final Map<String, PlayerState> players = new LinkedHashMap<>();

    private boolean finished;
    private LobbySettings settings;
    private List<Word> roundWords = List.of();
    private int currentRoundIndex;
    private Word currentWord;
    private String currentAnswer;          // 정규화된 정답 (라운드 시작 때 한 번만 계산)
    private RoundTimer currentTimer;
    private ScheduledFuture<?> nextRoundTask;

    // 타이머 스레드가 락 없이 읽으므로 volatile
    private volatile Consumer<String> broadcast;
    private volatile boolean roundActive;
    private volatile long epoch;           // 라운드 시작/게임 종료/방 비움마다 증가

    public GameService(WordService wordService) {
        this.wordService = wordService;
    }

    @Override
    public synchronized void onStart(List<ParticipantInfo> participants, LobbySettings settings, Consumer<String> broadcast) {
        resetRound();
        finished = false;
        this.settings = settings;
        this.broadcast = broadcast;
        this.currentRoundIndex = 0;

        players.clear();
        for (ParticipantInfo p : participants) {
            players.put(p.id(), new PlayerState(p));
        }

        // 게임당 한 번만 문제를 뽑고 첫 라운드를 시작한다
        this.roundWords = pickWords(settings.difficulty(), settings.rounds());
        sendScores();
        startNextRound();
    }

    private List<Word> pickWords(Difficulty difficulty, int count) {
        if (count <= 0) return List.of();

        String targetLevel = toKoreanLevel(difficulty);
        List<Word> sameLevel = new ArrayList<>();
        List<Word> others = new ArrayList<>();
        for (Word w : wordService.read()) {
            (targetLevel.equals(w.getLevel()) ? sameLevel : others).add(w);
        }

        Collections.shuffle(sameLevel);
        if (sameLevel.size() >= count) {
            return new ArrayList<>(sameLevel.subList(0, count));
        }

        // 해당 난이도 단어를 전부 쓰고, 모자란 만큼만 다른 난이도에서 보충
        Collections.shuffle(others);
        List<Word> picked = new ArrayList<>(sameLevel);
        picked.addAll(others.subList(0, Math.min(count - sameLevel.size(), others.size())));
        Collections.shuffle(picked);
        return picked;
    }

    private String toKoreanLevel(Difficulty difficulty) {
        return switch (difficulty) {
            case LOW -> "하";
            case MEDIUM -> "중";
            case HIGH -> "상";
        };
    }

    /**
     * 호출하는 쪽이 this 락을 잡고 있어야 한다.
     */
    private void startNextRound() {
        while (currentRoundIndex < roundWords.size()) {
            Word word = roundWords.get(currentRoundIndex++);
            try {
                // 문제 출제: ROUND|현재라운드|총라운드|초성포함예문
                broadcast.accept("ROUND|" + currentRoundIndex + "|" + roundWords.size() + "|" + word.getExample());
            } catch (RuntimeException e) {
                // 예문이 프로토콜 검증에 걸리는 등 출제에 실패하면 그 단어만 건너뛴다
                System.err.println("출제 실패로 건너뜀: " + e.getMessage());
                continue;
            }

            currentWord = word;
            currentAnswer = normalize(word.getWord());
            roundActive = true;
            final long roundEpoch = ++epoch;

            currentTimer = new RoundTimer(settings.seconds(), new TimerListener() {
                @Override
                public void onTick(int remainingSeconds) {
                    sendIfCurrent(roundEpoch, "TIMER|" + remainingSeconds);
                }

                @Override
                public void onHintTime() {
                    sendIfCurrent(roundEpoch, "HINT|" + word.getMeaning());
                }

                @Override
                public void onTimeOut() {
                    handleTimeOut(roundEpoch);
                }
            });
            currentTimer.start();
            return;
        }
        finishGame();
    }

    // 플레이어 정답 & 채팅 입력
    @Override
    public synchronized void onAnswer(ParticipantInfo player, String answer, Consumer<String> unused) {
        // 게임에 없는 사람(이미 나간 사람 등)의 입력은 무시
        PlayerState state = players.get(player.id());
        if (state == null) return;

        String cleaned = answer.trim();
        if (roundActive && normalize(cleaned).equals(currentAnswer)) {
            roundActive = false;
            stopCurrentTimer();

            state.score += BASE_SCORE;
            // 정답 알림: CORRECT|닉네임|정답단어|획득점수|누적점수
            sendQuietly("CORRECT|" + player.nickname() + "|" + currentWord.getWord() + "|" + BASE_SCORE + "|" + state.score);
            sendScores();
            scheduleNextRound();
        } else {
            // 오답이거나 라운드 사이 대기 중인 입력은 일반 채팅으로 전달
            sendQuietly("CHAT|" + player.nickname() + "|" + cleaned);
        }
    }

    // 시간 초과
    private synchronized void handleTimeOut(long roundEpoch) {
        if (!isCurrent(roundEpoch)) return;
        roundActive = false;
        scheduleNextRound();
        sendQuietly("TIMEOUT|" + currentWord.getWord());
    }

    /**
     * 현재 점수판 전송: SCORE|인원|id|점수|id|점수... 호출하는 쪽이 this 락을 잡고 있어야 한다.
     */
    private void sendScores() {
        StringBuilder sb = new StringBuilder("SCORE|").append(players.size());
        for (PlayerState p : players.values()) {
            sb.append('|').append(p.info.id()).append('|').append(p.score);
        }
        sendQuietly(sb.toString());
    }

    /**
     * 호출하는 쪽이 this 락을 잡고 있어야 한다.
     */
    private void scheduleNextRound() {
        final long scheduledEpoch = epoch;
        nextRoundTask = scheduler.schedule(() -> {
            synchronized (this) {
                // 대기 중에 새 게임이 시작되었거나 방이 비었다면 아무것도 하지 않는다
                if (epoch != scheduledEpoch) return;
                nextRoundTask = null;
                try {
                    startNextRound();
                } catch (RuntimeException e) {
                    System.err.println("다음 라운드 시작 실패: " + e);
                    finishGame();
                }
            }
        }, NEXT_ROUND_DELAY_SEC, TimeUnit.SECONDS);
    }

    /**
     * 호출하는 쪽이 this 락을 잡고 있어야 한다.
     */
    private void finishGame() {
        resetRound();
        finished = true;

        List<PlayerState> ranking = new ArrayList<>(players.values());
        ranking.sort(Comparator.comparingInt((PlayerState p) -> p.score).reversed());

        StringBuilder sb = new StringBuilder("GAME_OVER|").append(ranking.size());
        for (PlayerState p : ranking) {
            sb.append('|').append(p.info.nickname()).append('|').append(p.score);
        }
        sendQuietly(sb.toString());
    }

    @Override
    public synchronized void onLeave(ParticipantInfo player) {
        players.remove(player.id());
        // 방이 비는 경우는 LobbyServer가 onRoomEmpty()를 따로 호출한다
        if (!finished && !players.isEmpty()) sendScores();
    }

    @Override
    public synchronized void onRoomEmpty() {
        resetRound();
        finished = false;
        players.clear();
        roundWords = List.of();
        currentRoundIndex = 0;
        currentWord = null;
        currentAnswer = null;
    }

    @Override
    public synchronized void destroy() {
        resetRound();
        scheduler.shutdownNow();
    }

    /**
     * 진행 중인 라운드·타이머·예약 작업을 모두 무효화한다. 호출하는 쪽이 this 락을 잡고 있어야 한다.
     */
    private void resetRound() {
        epoch++;
        roundActive = false;
        stopCurrentTimer();
        if (nextRoundTask != null) {
            nextRoundTask.cancel(false);
            nextRoundTask = null;
        }
    }

    private void stopCurrentTimer() {
        if (currentTimer != null) {
            currentTimer.stop();
            currentTimer = null;
        }
    }

    private boolean isCurrent(long roundEpoch) {
        return roundActive && epoch == roundEpoch;
    }

    private void sendIfCurrent(long roundEpoch, String line) {
        if (isCurrent(roundEpoch)) sendQuietly(line);
    }

    /**
     * 전송 중 예외가 타이머·방 이벤트 스레드를 죽이거나 플레이어 연결을 끊지 않도록 막는다.
     */
    private void sendQuietly(String line) {
        try {
            broadcast.accept(line);
        } catch (RuntimeException e) {
            System.err.println("전송 실패: " + e.getMessage());
        }
    }

    /**
     * 맥 등에서 들어오는 분리형(NFD) 한글을 조합형(NFC)으로 맞춰 비교한다.
     */
    private static String normalize(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFC);
    }
}