package com.jiphyeonjeon.choseongschool.service;

import com.jiphyeonjeon.choseongschool.common.GameConstants.Protocol;
import com.jiphyeonjeon.choseongschool.entity.Word;
import com.jiphyeonjeon.choseongschool.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.server.GameEvents;
import com.jiphyeonjeon.choseongschool.timer.RoundTimer;
import com.jiphyeonjeon.choseongschool.timer.TimerListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 방(LobbyServer) 하나당 게임 한 판을 진행한다. 메시지 형식은 GameConstants.Protocol을 따른다.
 * <p>
 * 스레드 모델: 방 이벤트(onStart/onAnswer/onLeave)와 라운드 타이머는 할 일을 게임 스레드에 넘기기만 하고,
 * 상태 변경과 전송은 전부 게임 스레드 하나에서 순서대로 한다.
 * LobbyServer는 방을 잠근 채로 호출하고 broadcast도 방을 잠그므로, 여기서 따로 락을 잡으면
 * 서로 상대의 락을 기다리며 멈출 수 있다(교착 상태). DB 조회도 게임 스레드에서 해 방을 막지 않는다.
 * epoch 값으로 "이미 끝난 라운드/게임"에서 늦게 도착한 일을 무시한다.
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
    private final ScheduledExecutorService gameThread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "game-service");
        t.setDaemon(true);
        return t;
    });

    // 아래 상태는 모두 게임 스레드에서만 읽고 쓴다
    // 플레이어와 점수를 한 맵으로 관리 (두 맵이 어긋나는 문제 방지)
    private final Map<String, PlayerState> players = new LinkedHashMap<>();

    private boolean finished = true;
    private LobbySettings settings;
    private List<Word> roundWords = List.of();
    private int currentRoundIndex;
    private Word currentWord;
    private String currentAnswer;          // 정규화된 정답 (라운드 시작 때 한 번만 계산)
    private RoundTimer currentTimer;
    private ScheduledFuture<?> nextRoundTask;
    private Consumer<String> broadcast;
    private boolean roundActive;
    private long epoch;                    // 라운드 시작/게임 종료/방 비움마다 증가

    public GameService(WordService wordService) {
        this.wordService = wordService;
    }

    /** 게임 스레드에 할 일을 넘긴다. 예외가 나도 게임 스레드는 계속 일한다 */
    private void runOnGameThread(Runnable task) {
        try {
            gameThread.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    System.err.println("게임 처리 실패: " + e);
                }
            });
        } catch (RejectedExecutionException e) {
            // 서버가 종료되는 중이면 무시
        }
    }

    @Override
    public void onStart(List<ParticipantInfo> participants, LobbySettings settings, Consumer<String> broadcast) {
        runOnGameThread(() -> startGame(participants, settings, broadcast));
    }

    private void startGame(List<ParticipantInfo> participants, LobbySettings settings, Consumer<String> broadcast) {
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
        try {
            roundWords = wordService.readByLevel(settings.difficulty().getLabel(), settings.rounds());
        } catch (RuntimeException e) {
            System.err.println("문제를 가져오지 못함: " + e);
            roundWords = List.of(); // 문제가 없으면 바로 게임을 끝내 방이 대기 상태로 돌아가게 한다
        }
        sendScores();
        startNextRound();
    }

    private void startNextRound() {
        while (currentRoundIndex < roundWords.size()) {
            Word word = roundWords.get(currentRoundIndex++);
            try {
                // 라운드 번호를 먼저, 문제(초성이 포함된 예문)를 다음에 보낸다
                send(Protocol.ROUND, currentRoundIndex, roundWords.size());
                send(Protocol.QUESTION, word.getExample());
            } catch (RuntimeException e) {
                // 예문이 프로토콜 검증에 걸리는 등 출제에 실패하면 그 단어만 건너뛴다
                System.err.println("출제 실패로 건너뜀: " + e.getMessage());
                continue;
            }

            currentWord = word;
            currentAnswer = normalize(word.getWord());
            roundActive = true;
            final long roundEpoch = ++epoch;
            final int totalSeconds = settings.seconds();

            currentTimer = new RoundTimer(totalSeconds, new TimerListener() {
                @Override
                public void onTick(int remainingSeconds) {
                    runIfCurrent(roundEpoch, () -> sendQuietly(Protocol.TIME, remainingSeconds, totalSeconds));
                }

                @Override
                public void onHintTime() {
                    runIfCurrent(roundEpoch, () -> sendQuietly(Protocol.HINT, word.getMeaning()));
                }

                @Override
                public void onTimeOut() {
                    runIfCurrent(roundEpoch, () -> endRound());
                }
            });
            currentTimer.start();
            return;
        }
        finishGame();
    }

    // 플레이어 정답 입력
    @Override
    public void onAnswer(ParticipantInfo player, String answer, Consumer<String> unused) {
        runOnGameThread(() -> judge(player, answer.trim()));
    }

    private void judge(ParticipantInfo player, String answer) {
        // 게임에 없는 사람(이미 나간 사람 등)의 입력은 무시
        PlayerState state = players.get(player.id());
        if (finished || state == null) return;

        // O는 이번 라운드의 첫 정답자뿐이다. 오답이거나 이미 끝난 라운드의 답은 X
        boolean correct = roundActive && normalize(answer).equals(currentAnswer);
        sendQuietly(Protocol.ANSWER, player.nickname(), answer, correct ? Protocol.CORRECT : Protocol.WRONG);
        if (correct) {
            state.score += BASE_SCORE;
            sendQuietly(Protocol.SCORE, player.nickname(), state.score);
            endRound();
        }
    }

    /** 정답이 나왔거나 시간이 다 됐을 때: 정답과 뜻을 공개하고 잠시 뒤 다음 라운드로 */
    private void endRound() {
        roundActive = false;
        stopCurrentTimer();
        sendQuietly(Protocol.ROUND_END, currentWord.getWord(), currentWord.getMeaning());
        scheduleNextRound();
    }

    /** 현재 점수를 한 명씩 전송 */
    private void sendScores() {
        for (PlayerState p : players.values()) {
            sendQuietly(Protocol.SCORE, p.info.nickname(), p.score);
        }
    }

    private void scheduleNextRound() {
        final long scheduledEpoch = epoch;
        nextRoundTask = gameThread.schedule(() -> {
            // 대기 중에 새 게임이 시작되었거나 방이 비었다면 아무것도 하지 않는다
            if (epoch != scheduledEpoch) return;
            nextRoundTask = null;
            try {
                startNextRound();
            } catch (RuntimeException e) {
                System.err.println("다음 라운드 시작 실패: " + e);
                finishGame();
            }
        }, NEXT_ROUND_DELAY_SEC, TimeUnit.SECONDS);
    }

    /** 점수 높은 순으로 최종 결과를 보낸다. LobbyServer는 GAME_END를 보고 방을 대기 상태로 되돌린다 */
    private void finishGame() {
        resetRound();
        finished = true;

        List<PlayerState> ranking = new ArrayList<>(players.values());
        ranking.sort(Comparator.comparingInt((PlayerState p) -> p.score).reversed());

        List<Object> fields = new ArrayList<>();
        for (PlayerState p : ranking) {
            fields.add(p.info.nickname());
            fields.add(p.score);
        }
        sendQuietly(Protocol.GAME_END, fields.toArray());
    }

    @Override
    public void onLeave(ParticipantInfo player) {
        runOnGameThread(() -> {
            players.remove(player.id());
            // 누가 나가면 화면이 학동 줄을 다시 그리므로 남은 사람의 점수를 다시 보낸다
            // 방이 비는 경우는 LobbyServer가 onRoomEmpty()를 따로 호출한다
            if (!finished && !players.isEmpty()) sendScores();
        });
    }

    @Override
    public void onRoomEmpty() {
        runOnGameThread(() -> {
            resetRound();
            finished = true;
            players.clear();
            roundWords = List.of();
            currentRoundIndex = 0;
            currentWord = null;
            currentAnswer = null;
        });
    }

    @Override
    public void destroy() {
        runOnGameThread(this::resetRound); // 라운드 타이머 정리
        gameThread.shutdown();
    }

    /**
     * 진행 중인 라운드·타이머·예약 작업을 모두 무효화한다.
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

    /** 타이머가 부른 일을 게임 스레드로 넘기되, 그 라운드가 아직 진행 중일 때만 실행한다 */
    private void runIfCurrent(long roundEpoch, Runnable task) {
        runOnGameThread(() -> {
            if (roundActive && epoch == roundEpoch) task.run();
        });
    }

    /** 이름|값|값... 한 줄로 만들어 보낸다 */
    private void send(String type, Object... fields) {
        StringBuilder line = new StringBuilder(type);
        for (Object field : fields) {
            line.append(Protocol.SEPARATOR).append(field);
        }
        broadcast.accept(line.toString());
    }

    /**
     * 전송 중 예외가 게임 스레드의 다음 작업을 막지 않도록 막는다.
     */
    private void sendQuietly(String type, Object... fields) {
        try {
            send(type, fields);
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
