package com.jiphyeonjeon.choseongschool.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jiphyeonjeon.choseongschool.client.network.TcpConnection;
import com.jiphyeonjeon.choseongschool.server.entity.Word;
import com.jiphyeonjeon.choseongschool.server.service.GameService;
import com.jiphyeonjeon.choseongschool.server.service.ItemService;
import com.jiphyeonjeon.choseongschool.server.service.WordService;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 실제 LobbyServer와 GameService를 소켓으로 연결해, 두 사람이 같은 순간에 답을 보내고 타이머가 동시에 돌아도
 * 서로 기다리며 멈추지 않고 게임이 끝까지 진행되는지 확인한다 (실행에 15초 안팎).
 */
class GameServiceIntegrationTest {

    private static final int ROUNDS = 3;

    @Test
    void twoPlayersAnsweringAtTheSameTimeFinishTheGame() throws Exception {
        WordService wordService = mock(WordService.class);
        List<Word> words = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            Word word = mock(Word.class);
            when(word.getWord()).thenReturn("단어" + i);
            when(word.getExample()).thenReturn("예문-단어" + i);
            when(word.getMeaning()).thenReturn("뜻" + i);
            words.add(word);
        }
        when(wordService.readByLevel(anyString(), anyInt())).thenReturn(words);
        GameService game = new GameService(wordService, new ItemService());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (LobbyServer server = new LobbyServer(InetAddress.getLoopbackAddress(), 0, game);
             TcpConnection first = new TcpConnection("127.0.0.1", server.getPort());
             TcpConnection second = new TcpConnection("127.0.0.1", server.getPort())) {
            BlockingQueue<String> firstInbox = new LinkedBlockingQueue<>();
            BlockingQueue<String> secondInbox = new LinkedBlockingQueue<>();
            first.startReceiving(firstInbox::add, () -> { });
            second.startReceiving(secondInbox::add, () -> { });
            first.send("JOIN|지수|0");
            until(firstInbox, "WELCOME|1");
            second.send("JOIN|유나|1");
            until(secondInbox, "WELCOME|2");
            until(firstInbox, "PLAYERS|1|2|1|지수|0|2|유나|1");

            first.send("START");
            until(firstInbox, "GAME_START");

            for (int round = 0; round < ROUNDS; round++) {
                String question = untilPrefix(firstInbox, "QUESTION|");
                String answer = question.substring("QUESTION|예문-".length());
                // 같은 순간에 두 사람이 정답을 보낸다 (첫 정답자만 인정되어야 함)
                CountDownLatch gate = new CountDownLatch(1);
                pool.submit(() -> { await(gate); first.send("ANSWER|" + answer); });
                pool.submit(() -> { await(gate); second.send("ANSWER|" + answer); });
                gate.countDown();
                String roundEnd = untilPrefix(firstInbox, "ROUND_END|");
                assertEquals("ROUND_END|" + answer + "|뜻" + answer.substring(2), roundEnd);
            }

            String end = untilPrefix(firstInbox, "GAME_END|");
            // 문제마다 첫 정답자 한 명만 100점이라 점수 합은 문제 수 x 100
            String[] f = end.split("\\|");
            assertEquals(5, f.length);
            assertEquals(ROUNDS * 100, Integer.parseInt(f[2]) + Integer.parseInt(f[4]));
            untilPrefix(secondInbox, "GAME_END|");
            // 끝난 뒤 방이 대기 상태로 돌아와 학칙을 다시 받는다
            until(firstInbox, "SETTINGS|LOW|10|30|NORMAL");
        } finally {
            pool.shutdownNow();
            game.destroy();
        }
    }

    private static void until(BlockingQueue<String> inbox, String target) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            long remaining = deadline - System.nanoTime();
            assertTrue(remaining > 0, "수신 시간 초과: " + target);
            String line = inbox.poll(remaining, TimeUnit.NANOSECONDS);
            assertNotNull(line, "수신 시간 초과: " + target);
            if (line.equals(target)) return;
        }
    }

    private static String untilPrefix(BlockingQueue<String> inbox, String prefix) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            long remaining = deadline - System.nanoTime();
            assertTrue(remaining > 0, "수신 시간 초과: " + prefix);
            String line = inbox.poll(remaining, TimeUnit.NANOSECONDS);
            assertNotNull(line, "수신 시간 초과: " + prefix);
            if (line.startsWith(prefix)) return line;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
