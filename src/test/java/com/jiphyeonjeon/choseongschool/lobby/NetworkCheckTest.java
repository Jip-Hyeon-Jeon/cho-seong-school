package com.jiphyeonjeon.choseongschool.lobby;
import com.jiphyeonjeon.choseongschool.lobby.model.*;
import com.jiphyeonjeon.choseongschool.lobby.view.*;
import com.jiphyeonjeon.choseongschool.lobby.client.*;
import com.jiphyeonjeon.choseongschool.lobby.network.*;
import com.jiphyeonjeon.choseongschool.lobby.server.*;

import java.util.concurrent.*;

/** 실제 루프백 소켓으로 두 클라이언트의 목록 공유와 반장 위임을 검사합니다. */
class NetworkCheckTest {
    @org.junit.jupiter.api.Test
    void verifiesBehavior() throws Exception {
        try (LobbyServer server = new LobbyServer(0);
             TcpConnection jisu = new TcpConnection("127.0.0.1", server.getPort());
             TcpConnection yuna = new TcpConnection("127.0.0.1", server.getPort())) {
            BlockingQueue<String> jisuInbox = new LinkedBlockingQueue<>();
            BlockingQueue<String> yunaInbox = new LinkedBlockingQueue<>();
            CountDownLatch jisuClosed = new CountDownLatch(1);
            jisu.startReceiving(jisuInbox::add, jisuClosed::countDown);
            yuna.startReceiving(yunaInbox::add, () -> { });
            jisu.send("JOIN|최지수|0");
            expect(jisuInbox, "WELCOME|1");
            expect(jisuInbox, "PLAYERS|1|1|1|최지수|0");
            expect(jisuInbox, "SETTINGS|LOW|10|30");
            yuna.send("JOIN|김유나|2");
            expect(yunaInbox, "WELCOME|2");
            String both = "PLAYERS|1|2|1|최지수|0|2|김유나|2";
            expect(jisuInbox, both);
            expect(yunaInbox, both);
            expect(yunaInbox, "SETTINGS|LOW|10|30");
            System.out.println("PASS: 두 클라이언트가 동일한 참가자 목록을 받음");
            jisu.send("SET_SETTINGS|HIGH|20|15");
            expect(jisuInbox, "SETTINGS|HIGH|20|15");
            expect(yunaInbox, "SETTINGS|HIGH|20|15");
            yuna.send("SET_SETTINGS|LOW|5|30");
            expect(yunaInbox, "ERROR|NOT_HOST");
            expect(yunaInbox, "SETTINGS|HIGH|20|15");
            for (String invalid : new String[]{"HIGH|7|15", "UNKNOWN|5|30", "LOW|5|99", "LOW|5", "LOW|5|30|extra"}) {
                jisu.send("SET_SETTINGS|" + invalid);
                expect(jisuInbox, "ERROR|BAD_SETTINGS");
                expect(jisuInbox, "SETTINGS|HIGH|20|15");
            }
            if (!yunaInbox.isEmpty()) throw new AssertionError("거부한 설정이 다른 참가자에게 전파됨");
            System.out.println("PASS: 반장 설정 공유, 학동 요청 거부, 잘못된 값의 전체 변경 거부");
            try (TcpConnection duplicate = new TcpConnection("127.0.0.1", server.getPort())) {
                BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
                duplicate.startReceiving(inbox::add, () -> { });
                duplicate.send("JOIN|최지수|1");
                expect(inbox, "ERROR|DUPLICATE_NICKNAME");
            }
            System.out.println("PASS: 중복 닉네임은 서버에서 거절");
            boolean rejected = false;
            try { jisu.send("QUIT\nJOIN|가짜|0"); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("줄바꿈 검증 실패");
            jisu.send("QUIT");
            expect(yunaInbox, "PLAYERS|2|1|2|김유나|2");
            if (!jisuClosed.await(3, TimeUnit.SECONDS)) throw new AssertionError("퇴장 후 연결이 남음");
            System.out.println("PASS: 반장 퇴장 후 남은 참가자에게 반장 위임");
            yuna.send("SET_SETTINGS|MEDIUM|15|30");
            expect(yunaInbox, "SETTINGS|MEDIUM|15|30");
            try (TcpConnection next = new TcpConnection("127.0.0.1", server.getPort())) {
                BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
                next.startReceiving(inbox::add, () -> { });
                next.send("JOIN|송서영|3");
                expect(inbox, "WELCOME|3");
                String pair = "PLAYERS|2|2|2|김유나|2|3|송서영|3";
                expect(inbox, pair);
                expect(inbox, "SETTINGS|MEDIUM|15|30");
                expect(yunaInbox, pair);
                next.close(); // QUIT 없이 끊어도 서버가 퇴장을 처리해야 합니다.
                expect(yunaInbox, "PLAYERS|2|1|2|김유나|2");
            }
            System.out.println("PASS: 갑작스러운 연결 종료도 목록에서 제거");
        }
        System.out.println("NETWORK CHECK OK");
    }

    private static void expect(BlockingQueue<String> inbox, String expected) throws Exception {
        String actual = inbox.poll(3, TimeUnit.SECONDS);
        if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }
}
