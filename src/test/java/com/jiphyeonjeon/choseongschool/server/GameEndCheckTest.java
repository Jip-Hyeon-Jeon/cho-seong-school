package com.jiphyeonjeon.choseongschool.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jiphyeonjeon.choseongschool.common.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.common.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.client.network.TcpConnection;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** 게임 로직이 GAME_END를 보내면 방이 대기 상태로 돌아가 다시 시작·입장할 수 있는지 실제 소켓으로 확인한다 */
class GameEndCheckTest {

    /** 시작하자마자 게임을 끝내는 게임 로직. 끝난 뒤 보낼 수 있도록 전송 함수를 남겨 둔다 */
    private static final class InstantGame implements GameEvents {
        final AtomicReference<Consumer<String>> lastBroadcast = new AtomicReference<>();

        @Override
        public void onStart(List<ParticipantInfo> players, LobbySettings settings, Consumer<String> broadcast) {
            lastBroadcast.set(broadcast);
            broadcast.accept("GAME_END|지수|10");
        }

        @Override
        public void onAnswer(ParticipantInfo player, String answer, Consumer<String> broadcast) {
        }
    }

    @Test
    void gameEndReturnsRoomToWaiting() throws Exception {
        InstantGame game = new InstantGame();
        try (LobbyServer server = new LobbyServer(InetAddress.getLoopbackAddress(), 0, game);
             TcpConnection jisu = new TcpConnection("127.0.0.1", server.getPort())) {
            BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
            jisu.startReceiving(inbox::add, () -> { });
            jisu.send("JOIN|지수|0");
            expect(inbox, "WELCOME|1");
            expect(inbox, "PLAYERS|1|1|1|지수|0");
            expect(inbox, "SETTINGS|LOW|10|30|NORMAL");

            jisu.send("START");
            expect(inbox, "COUNTDOWN|3");
            expect(inbox, "COUNTDOWN|2");
            expect(inbox, "COUNTDOWN|1");
            expect(inbox, "GAME_START");
            expect(inbox, "GAME_END|지수|10");
            expect(inbox, "SETTINGS|LOW|10|30|NORMAL"); // 대기 상태 복귀 후 학칙 다시 공유

            // 끝난 게임이 늦게 보내는 메시지는 전달되지 않는다
            game.lastBroadcast.get().accept("QUESTION|늦은 문제");

            // 대기 상태라 새 학동이 입장할 수 있다
            try (TcpConnection yuna = new TcpConnection("127.0.0.1", server.getPort())) {
                BlockingQueue<String> yunaInbox = new LinkedBlockingQueue<>();
                yuna.startReceiving(yunaInbox::add, () -> { });
                yuna.send("JOIN|유나|2");
                expect(yunaInbox, "WELCOME|2");
                expect(inbox, "PLAYERS|1|2|1|지수|0|2|유나|2");
            }
            expect(inbox, "PLAYERS|1|1|1|지수|0");

            // 반장이 다시 시작할 수 있다 (NOT_WAITING 오류가 아니라 카운트다운)
            jisu.send("START");
            expect(inbox, "COUNTDOWN|3");
        }
    }

    private static void expect(BlockingQueue<String> inbox, String expected) throws InterruptedException {
        assertEquals(expected, inbox.poll(5, TimeUnit.SECONDS));
    }
}
