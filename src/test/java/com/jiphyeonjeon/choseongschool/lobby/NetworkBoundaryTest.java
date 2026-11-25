package com.jiphyeonjeon.choseongschool.lobby;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.Socket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import com.jiphyeonjeon.choseongschool.lobby.model.LobbyConstants;
import com.jiphyeonjeon.choseongschool.lobby.network.LineMessages;
import com.jiphyeonjeon.choseongschool.lobby.network.TcpConnection;
import com.jiphyeonjeon.choseongschool.lobby.server.LobbyServer;
import com.jiphyeonjeon.choseongschool.lobby.server.GameEvents;
import com.jiphyeonjeon.choseongschool.lobby.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.lobby.model.LobbySettings;

class NetworkBoundaryTest {
    @Test
    void answerIdentityAndLateGameMessagesAreBoundToTheirConnectionAndGame() throws Exception {
        AtomicReference<Consumer<String>> broadcast = new AtomicReference<>();
        BlockingQueue<ParticipantInfo> answers = new LinkedBlockingQueue<>();
        CountDownLatch empty = new CountDownLatch(1);
        GameEvents game = new GameEvents() {
            public void onStart(List<ParticipantInfo> players, LobbySettings settings, Consumer<String> send) { broadcast.set(send); }
            public void onAnswer(ParticipantInfo player, String answer, Consumer<String> send) { answers.add(player); }
            public void onRoomEmpty() { empty.countDown(); }
        };
        try (LobbyServer server = new LobbyServer(InetAddress.getLoopbackAddress(), 0, game)) {
            Consumer<String> previousGame;
            try (TcpConnection first = new TcpConnection("127.0.0.1", server.getPort())) {
                BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
                first.startReceiving(inbox::add, () -> { });
                first.send("JOIN|jisu|0");
                until(inbox, "SETTINGS|LOW|10|30");
                first.send("START");
                until(inbox, "GAME_START");
                first.send("ANSWER|학교|가짜이름");
                until(inbox, "ERROR|BAD_ANSWER");
                assertTrue(answers.isEmpty());
                first.send("ANSWER|학교");
                ParticipantInfo player = answers.poll(3, TimeUnit.SECONDS);
                assertNotNull(player);
                assertEquals("jisu", player.nickname());
                assertEquals("1", player.id());
                previousGame = broadcast.get();
            }
            assertTrue(empty.await(3, TimeUnit.SECONDS));
            try (TcpConnection next = new TcpConnection("127.0.0.1", server.getPort())) {
                BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
                next.startReceiving(inbox::add, () -> { });
                next.send("JOIN|yuna|2");
                until(inbox, "SETTINGS|LOW|10|30");
                next.send("START");
                until(inbox, "GAME_START");
                // onStart 실행 완료를 보장하기 위해 같은 방 이벤트 경로로 답안 한 개를 왕복합니다.
                next.send("ANSWER|확인");
                assertNotNull(answers.poll(3, TimeUnit.SECONDS));
                previousGame.accept("QUESTION|이전게임");
                broadcast.get().accept("QUESTION|현재게임");
                assertEquals("QUESTION|현재게임", inbox.poll(3, TimeUnit.SECONDS));
            }
        }
    }

    private static void until(BlockingQueue<String> inbox, String target) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            long remaining = deadline - System.nanoTime();
            assertTrue(remaining > 0, "수신 시간 초과: " + target);
            String line = inbox.poll(remaining, TimeUnit.NANOSECONDS);
            assertNotNull(line, "수신 시간 초과: " + target);
            if (line.equals(target)) return;
        }
    }

    @Test
    void concurrentJoinsNeverExceedFivePlayers() throws Exception {
        List<TcpConnection> clients = new CopyOnWriteArrayList<>();
        try (LobbyServer server = new LobbyServer(0); var pool = Executors.newFixedThreadPool(10)) {
            CountDownLatch ready = new CountDownLatch(10);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final int number = i;
                results.add(pool.submit(() -> {
                    TcpConnection client = new TcpConnection("127.0.0.1", server.getPort());
                    clients.add(client);
                    BlockingQueue<String> lines = new LinkedBlockingQueue<>();
                    client.startReceiving(lines::add, () -> { });
                    ready.countDown();
                    assertTrue(go.await(3, TimeUnit.SECONDS));
                    client.send("JOIN|p" + number + "|0");
                    String first = lines.poll(3, TimeUnit.SECONDS);
                    assertNotNull(first);
                    return first;
                }));
            }
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            go.countDown();
            int joined = 0;
            int refused = 0;
            for (Future<String> result : results) {
                String line = result.get(4, TimeUnit.SECONDS);
                if (line.startsWith("WELCOME|")) joined++;
                else { assertEquals("ERROR|ROOM_FULL", line); refused++; }
            }
            assertEquals(5, joined);
            assertEquals(5, refused);
        } finally { clients.forEach(TcpConnection::close); }
    }

    @Test
    void oversizedRawInputClosesOnlyThatConnection() throws Exception {
        try (LobbyServer server = new LobbyServer(0); Socket malformed = new Socket("127.0.0.1", server.getPort())) {
            malformed.setSoTimeout(3000);
            malformed.getOutputStream().write(("x".repeat(LobbyConstants.MAX_LINE_LENGTH + 1) + "\n").getBytes(StandardCharsets.UTF_8));
            malformed.getOutputStream().flush();
            assertEquals(-1, malformed.getInputStream().read());
            try (TcpConnection good = new TcpConnection("127.0.0.1", server.getPort())) {
                BlockingQueue<String> lines = new LinkedBlockingQueue<>();
                good.startReceiving(lines::add, () -> { });
                good.send("JOIN|jisu|0");
                assertEquals("WELCOME|1", lines.poll(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void lineBoundariesAndInjectionAreValidated() throws Exception {
        assertEquals("JOIN|jisu|0", LineMessages.read(new BufferedReader(new StringReader("JOIN|jisu|0\r\n"))));
        assertThrows(IllegalArgumentException.class, () -> LineMessages.validate("ANSWER|a\nSTART"));
        assertThrows(IllegalArgumentException.class, () -> LineMessages.validate("x".repeat(4097)));
        assertThrows(IOException.class, () -> LineMessages.read(new BufferedReader(new StringReader("x".repeat(4097)))));
    }
}
