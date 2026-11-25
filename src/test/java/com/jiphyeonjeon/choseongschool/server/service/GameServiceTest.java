package com.jiphyeonjeon.choseongschool.server.service;

import com.jiphyeonjeon.choseongschool.server.entity.Word;
import com.jiphyeonjeon.choseongschool.common.model.Difficulty;
import com.jiphyeonjeon.choseongschool.common.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.common.model.ParticipantInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GameService 단위 테스트. DB·소켓 없이 WordService/Word를 목으로 대체하고,
 * broadcast 로 나간 문자열을 리스트에 모아서 검증한다.
 * 타이머/예약 작업을 실제 시간으로 돌리기 때문에 전체 실행에 20초 안팎이 걸린다.
 */
class GameServiceTest {

    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final Consumer<String> broadcast = lines::add;
    private final Map<String, String> answerByExample = new HashMap<>();

    private WordService wordService;
    private GameService game;

    private final ParticipantInfo alice = new ParticipantInfo("1", "영희", 0);
    private final ParticipantInfo bob = new ParticipantInfo("2", "철수", 1);
    private final ParticipantInfo carol = new ParticipantInfo("3", "민수", 2);

    @BeforeEach
    void setUp() {
        wordService = mock(WordService.class);
        game = new GameService(wordService);
        useWords(words("중", 10, "단어"));
    }

    @AfterEach
    void tearDown() {
        game.destroy();
    }

    // ---------------------------------------------------------------- 라운드 시작

    @Test
    @DisplayName("플레이어가 여러 명이어도 첫 라운드는 한 번만 출제된다")
    void startsOnlyOneRoundRegardlessOfPlayerCount() throws Exception {
        start(settings(Difficulty.MEDIUM, 3, 30), alice, bob, carol);

        awaitLine("ROUND|1|3", 1000);
        Thread.sleep(500);

        assertEquals(1, count("ROUND|"));
    }

    @Test
    @DisplayName("요청한 라운드 수가 단어 수보다 많으면 단어 수만큼만 출제한다")
    void totalRoundsAreCappedByAvailableWords() {
        useWords(words("중", 4, "단어"));
        start(settings(Difficulty.MEDIUM, 10, 30), alice);

        awaitLine("ROUND|1|4", 1000);
    }

    @Test
    @DisplayName("해당 난이도 단어가 충분하면 그 난이도만 출제한다")
    void usesOnlySameLevelWhenEnoughWords() {
        List<Word> all = new ArrayList<>(words("하", 5, "쉬운"));
        all.addAll(words("상", 5, "어려운"));
        useWords(all);

        start(settings(Difficulty.LOW, 3, 30), alice);

        awaitLine("ROUND|1|3", 1000);
        assertTrue(answer(1).startsWith("쉬운"), "하 난이도 단어여야 함");
    }

    // ---------------------------------------------------------------- 정답 / 오답

    @Test
    @DisplayName("정답을 맞히면 O가 나가고 점수가 100점 오른다")
    void correctAnswerBroadcastsOAndAddsScore() {
        start(settings(Difficulty.MEDIUM, 3, 30), alice, bob);
        String answer = answer(1);

        game.onAnswer(alice, answer, broadcast);

        assertEquals("ANSWER|영희|" + answer + "|O", awaitLine("ANSWER|", 1000));
        awaitLine("SCORE|영희|100", 1000);
        assertEquals("ROUND_END|" + answer + "|뜻-" + answer, awaitLine("ROUND_END|", 1000));
    }

    @Test
    @DisplayName("오답은 X로 전달되고 점수는 오르지 않는다")
    void wrongAnswerIsBroadcastAsX() {
        start(settings(Difficulty.MEDIUM, 3, 30), alice, bob);
        awaitLine("ROUND|1|", 1000);

        game.onAnswer(alice, "틀린답", broadcast);

        assertEquals("ANSWER|영희|틀린답|X", awaitLine("ANSWER|", 1000));
        assertEquals(0, count("SCORE|영희|100"));
    }

    @Test
    @DisplayName("분리형(NFD) 한글로 입력해도 정답으로 인정한다")
    void decomposedHangulIsAccepted() {
        start(settings(Difficulty.MEDIUM, 3, 30), alice);
        String answer = answer(1);

        game.onAnswer(alice, Normalizer.normalize(answer, Normalizer.Form.NFD), broadcast);

        awaitLine("ROUND_END|", 1000);
    }

    @Test
    @DisplayName("두 명이 동시에 정답을 보내도 O는 한 번만 나간다")
    void simultaneousCorrectAnswersScoreOnlyOnce() throws Exception {
        start(settings(Difficulty.MEDIUM, 3, 30), alice, bob);
        String answer = answer(1);

        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (ParticipantInfo p : List.of(alice, bob)) {
            pool.submit(() -> {
                await(gate);
                game.onAnswer(p, answer, broadcast);
            });
        }
        gate.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));

        awaitCount("ANSWER|", 2);
        assertEquals(1, lines.stream().filter(l -> l.startsWith("ANSWER|") && l.endsWith("|O")).count());
        assertEquals(1, lines.stream().filter(l -> l.startsWith("SCORE|") && l.endsWith("|100")).count());
    }

    @Test
    @DisplayName("게임에 없는 사람의 입력은 무시한다")
    void answerFromNonParticipantIsIgnored() throws Exception {
        start(settings(Difficulty.MEDIUM, 3, 30), alice);
        String answer = answer(1);

        game.onAnswer(carol, answer, broadcast);
        Thread.sleep(300);

        assertEquals(0, count("ANSWER|"));
    }

    @Test
    @DisplayName("나간 플레이어의 입력은 무시한다")
    void answerFromLeftPlayerIsIgnored() throws Exception {
        start(settings(Difficulty.MEDIUM, 3, 30), alice, bob);
        String answer = answer(1);

        game.onLeave(bob);
        game.onAnswer(bob, answer, broadcast);
        Thread.sleep(300);

        assertEquals(0, count("ANSWER|"));
    }

    // ---------------------------------------------------------------- 타이머 / 다음 라운드

    @Test
    @DisplayName("정답 후 약 3초 뒤에 다음 라운드가 시작된다")
    void nextRoundStartsAfterDelay() {
        start(settings(Difficulty.MEDIUM, 3, 30), alice);
        game.onAnswer(alice, answer(1), broadcast);
        awaitLine("ROUND_END|", 1000);

        assertEquals(0, count("ROUND|2|"), "대기 시간 없이 바로 시작되면 안 됨");
        awaitLine("ROUND|2|3", 5000);
    }

    @Test
    @DisplayName("시간이 다 되면 정답이 공개되고 다음 라운드로 넘어간다")
    void timeoutRevealsAnswerAndMovesOn() {
        start(settings(Difficulty.MEDIUM, 2, 1), alice);
        String answer = answer(1);

        assertEquals("ROUND_END|" + answer + "|뜻-" + answer, awaitLine("ROUND_END|", 3000));
        awaitLine("TIME|0|1", 1000);
        awaitLine("ROUND|2|2", 6000);
    }

    @Test
    @DisplayName("제한 시간의 절반에 힌트(뜻)가 나간다")
    void hintIsBroadcastAtHalfTime() {
        start(settings(Difficulty.MEDIUM, 1, 4), alice);
        String answer = answer(1);

        assertEquals("HINT|뜻-" + answer, awaitLine("HINT|", 4000));
    }

    @Test
    @DisplayName("정답을 맞힌 라운드는 시간이 지나도 다시 끝나지 않는다")
    void noTimeoutAfterCorrectAnswer() throws Exception {
        start(settings(Difficulty.MEDIUM, 3, 2), alice);
        game.onAnswer(alice, answer(1), broadcast);

        Thread.sleep(2500);

        assertEquals(1, count("ROUND_END|"));
    }

    // ---------------------------------------------------------------- 종료 / 정리

    @Test
    @DisplayName("마지막 라운드가 끝나면 점수 순으로 GAME_END가 나간다")
    void gameEndRanksByScore() {
        start(settings(Difficulty.MEDIUM, 2, 30), alice, bob);

        game.onAnswer(bob, answer(1), broadcast);
        game.onAnswer(bob, answer(2), broadcast);

        assertEquals("GAME_END|철수|200|영희|0", awaitLine("GAME_END|", 5000));
    }

    @Test
    @DisplayName("방이 비면 예약된 다음 라운드가 실행되지 않는다")
    void roomEmptyCancelsScheduledNextRound() throws Exception {
        start(settings(Difficulty.MEDIUM, 3, 30), alice);
        game.onAnswer(alice, answer(1), broadcast);
        awaitLine("ROUND_END|", 1000);

        game.onRoomEmpty();
        Thread.sleep(4000);

        assertEquals(0, count("ROUND|2|"));
        assertEquals(0, count("GAME_END|"));
    }

    @Test
    @DisplayName("새 게임을 시작하면 이전 게임의 예약된 라운드는 무효가 된다")
    void newGameInvalidatesPreviousScheduledRound() throws Exception {
        start(settings(Difficulty.MEDIUM, 2, 30), alice);
        game.onAnswer(alice, answer(1), broadcast);
        awaitLine("ROUND_END|", 1000);

        start(settings(Difficulty.MEDIUM, 2, 30), alice);   // 3초 대기 중에 새 게임 시작
        Thread.sleep(4000);

        assertEquals(2, count("ROUND|1|"), "각 게임의 1라운드만 있어야 함");
        assertEquals(0, count("ROUND|2|"), "이전 게임의 예약이 새 게임에 끼어들면 안 됨");
    }

    // ---------------------------------------------------------------- 헬퍼

    private void start(LobbySettings settings, ParticipantInfo... players) {
        game.onStart(List.of(players), settings, broadcast);
    }

    /**
     * LobbySettings 생성자는 허용된 학칙 값만 받기 때문에(예: 라운드 3, 제한 시간 1초는 거부됨),
     * 테스트에서는 빠른 실행을 위해 목으로 만들어 검증을 우회한다. GameService는 세 접근자만 쓴다.
     */
    private static LobbySettings settings(Difficulty difficulty, int rounds, int seconds) {
        LobbySettings settings = mock(LobbySettings.class);
        when(settings.difficulty()).thenReturn(difficulty);
        when(settings.rounds()).thenReturn(rounds);
        when(settings.seconds()).thenReturn(seconds);
        return settings;
    }

    private void useWords(List<Word> words) {
        when(wordService.readByLevel(anyString(), anyInt())).thenAnswer(call -> {
            String level = call.getArgument(0);
            int count = call.getArgument(1);
            return words.stream().filter(w -> w.getLevel().equals(level)).limit(count).toList();
        });
    }

    private List<Word> words(String level, int count, String prefix) {
        List<Word> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String answer = prefix + i;
            String example = "예문-" + answer;
            Word word = mock(Word.class);
            when(word.getWord()).thenReturn(answer);
            when(word.getExample()).thenReturn(example);
            when(word.getMeaning()).thenReturn("뜻-" + answer);
            when(word.getLevel()).thenReturn(level);
            answerByExample.put(example, answer);
            result.add(word);
        }
        return result;
    }

    /** round번째 문제(QUESTION|예문)가 올 때까지 기다려 예문으로 정답 단어를 찾는다. */
    private String answer(int round) {
        awaitCount("QUESTION|", round);
        String line = lines.stream().filter(l -> l.startsWith("QUESTION|")).skip(round - 1).findFirst().orElseThrow();
        String answer = answerByExample.get(line.substring("QUESTION|".length()));
        if (answer == null) fail("알 수 없는 예문: " + line);
        return answer;
    }

    /** prefix로 시작하는 메시지가 count개 올 때까지 기다린다. */
    private void awaitCount(String prefix, int count) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (count(prefix) < count) {
            if (System.nanoTime() > deadline) fail("메시지가 모자람: " + prefix + " / 받은 메시지: " + lines);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private long count(String prefix) {
        return lines.stream().filter(l -> l.startsWith(prefix)).count();
    }

    /** prefix로 시작하는 메시지가 올 때까지 기다린다. */
    private String awaitLine(String prefix, long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            for (String line : lines) {
                if (line.startsWith(prefix)) return line;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return fail("메시지가 오지 않음: " + prefix + " / 받은 메시지: " + lines);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("게임 시작 시 전원 0점 SCORE가 첫 ROUND보다 먼저 나간다")
    void initialScoreIsSentBeforeFirstRound() {
        start(settings(Difficulty.MEDIUM, 3, 30), alice, bob);
        awaitLine("ROUND|1|", 1000);

        assertEquals(List.of("SCORE|영희|0", "SCORE|철수|0"), lines.subList(0, 2));
        assertTrue(lines.get(2).startsWith("ROUND|1|"));
    }

    @Test
    @DisplayName("정답 후 ANSWER 다음에 갱신된 SCORE가 나간다")
    void scoreIsSentAfterAnswer() {
        start(settings(Difficulty.MEDIUM, 3, 30), alice, bob);
        game.onAnswer(bob, answer(1), broadcast);

        String correct = awaitLine("ANSWER|철수|", 1000);
        String score = awaitLine("SCORE|철수|100", 1000);

        assertTrue(lines.indexOf(correct) < lines.indexOf(score));
    }

    @Test
    @DisplayName("플레이어가 나가면 남은 사람의 SCORE가 나간다")
    void scoreIsSentWhenPlayerLeaves() {
        start(settings(Difficulty.MEDIUM, 3, 30), alice, bob);
        awaitLine("ROUND|1|", 1000);

        game.onLeave(bob);

        awaitCount("SCORE|영희|0", 2); // 시작할 때 한 번, 나갔을 때 한 번
    }

    @Test
    @DisplayName("게임이 끝난 뒤 플레이어가 나가도 SCORE는 나가지 않는다")
    void noScoreAfterGameOver() throws Exception {
        start(settings(Difficulty.MEDIUM, 1, 30), alice, bob);
        game.onAnswer(alice, answer(1), broadcast);
        awaitLine("GAME_END|", 5000);
        long before = count("SCORE|");

        game.onLeave(bob);
        Thread.sleep(300);

        assertEquals(before, count("SCORE|"));
    }

    @Test
    @DisplayName("문제가 하나도 없으면 바로 GAME_END가 나가 방이 대기 상태로 돌아갈 수 있다")
    void noWordsEndsGameImmediately() {
        useWords(List.of());
        start(settings(Difficulty.MEDIUM, 3, 30), alice);

        assertEquals("GAME_END|영희|0", awaitLine("GAME_END|", 1000));
    }

    @Test
    @DisplayName("방을 잠근 채 호출하고 broadcast도 방을 잠가도(LobbyServer 방식) 서로 기다리며 멈추지 않는다")
    void doesNotDeadlockWithRoomLock() throws Exception {
        Object room = new Object();
        Consumer<String> lockingBroadcast = line -> {
            synchronized (room) {
                lines.add(line);
            }
        };
        LobbySettings quick = settings(Difficulty.MEDIUM, 1, 1);
        synchronized (room) {
            game.onStart(List.of(alice), quick, lockingBroadcast);
            Thread.sleep(200); // 게임 스레드가 방 잠금을 기다리는 동안에도
            Thread caller = new Thread(() -> game.onAnswer(alice, "오답", lockingBroadcast));
            caller.start();
            caller.join(1000);
            assertTrue(!caller.isAlive(), "onAnswer가 막히면 안 됨");
        }
        awaitLine("GAME_END|", 7000);
    }
}
