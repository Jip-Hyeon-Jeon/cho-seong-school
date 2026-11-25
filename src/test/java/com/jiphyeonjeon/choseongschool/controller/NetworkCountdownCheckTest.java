package com.jiphyeonjeon.choseongschool.controller;
import com.jiphyeonjeon.choseongschool.model.*;
import com.jiphyeonjeon.choseongschool.view.*;
import com.jiphyeonjeon.choseongschool.client.*;
import com.jiphyeonjeon.choseongschool.network.*;
import com.jiphyeonjeon.choseongschool.server.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.*;

/** 실제 서버 메시지와 Swing 화면의 시작/취소/재시작을 함께 검사합니다. */
class NetworkCountdownCheckTest {
    private static class Client implements AutoCloseable {
        final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        final LobbyView view;
        final TcpConnection connection;
        final NetworkLobbyController controller;

        Client(LobbyServer server, String name) throws Exception {
            view = edt(() -> new LobbyView());
            controller = new NetworkLobbyController(view);
            connection = new TcpConnection("127.0.0.1", server.getPort());
            edt(() -> {
                view.setSettingsRequest(s -> connection.send("SET_SETTINGS|" + s.fields()));
                view.setStartRequest(() -> connection.send("START"));
                return null;
            });
            connection.startReceiving(line -> { controller.handle(line); inbox.add(line); }, controller::onClosed);
            connection.send("JOIN|" + name + "|0");
        }

        void expect(String expected) throws Exception {
            String actual = inbox.poll(4, TimeUnit.SECONDS);
            if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
            edt(() -> null); // 앞서 등록한 화면 갱신이 끝날 때까지 기다립니다.
        }

        void denied(String reason) throws Exception {
            expect("ERROR|" + reason);
            expect("SETTINGS|LOW|10|30");
        }

        public void close() { connection.close(); }
    }

    @org.junit.jupiter.api.Test
    void verifiesBehavior() throws Exception {
        try (LobbyServer server = new LobbyServer(0);
             Client host = new Client(server, "jisu")) {
            host.expect("WELCOME|1");
            host.expect("PLAYERS|1|1|1|jisu|0");
            host.expect("SETTINGS|LOW|10|30");
            try (Client student = new Client(server, "yuna")) {
                student.expect("WELCOME|2");
                String pair = "PLAYERS|1|2|1|jisu|0|2|yuna|0";
                host.expect(pair);
                student.expect(pair);
                student.expect("SETTINGS|LOW|10|30");
                student.connection.send("START");
                student.denied("NOT_HOST");
                check(edt(() -> student.view.getViewState() == RoomState.WAITING), "학동 시작 거부");

                // 실제 시작 버튼을 눌러 요청합니다.
                edt(() -> { host.view.getStartButton().doClick(); return null; });
                host.expect("COUNTDOWN|3");
                student.expect("COUNTDOWN|3");
                check(edt(() -> student.view.isCountdownVisible()), "학동 오버레이 표시");
                check(edt(() -> !host.view.getStartButton().isVisible()), "시작 버튼 숨김");
                host.connection.send("START");
                host.denied("NOT_WAITING");
                host.connection.send("SET_SETTINGS|HIGH|20|15");
                host.denied("NOT_WAITING");
                try (Client late = new Client(server, "late")) {
                    late.expect("ERROR|ROOM_BUSY");
                }
                host.expect("COUNTDOWN|2");
                student.expect("COUNTDOWN|2");

                // 반장이 나가면 취소되고 학동이 새 반장이 됩니다.
                host.close();
                student.expect("PLAYERS|2|1|2|yuna|0");
                student.expect("START_CANCELLED|PLAYER_LEFT");
                check(edt(() -> !student.view.isCountdownVisible()
                        && student.view.getStartButton().isEnabled()), "취소 후 새 반장 재시작 가능");
                edt(() -> { student.view.getStartButton().doClick(); return null; });
                for (int number = 3; number >= 1; number--) {
                    student.expect("COUNTDOWN|" + number);
                    final int expected = number;
                    check(edt(() -> student.view.getCountdownNumber() == expected), "화면 숫자 일치");
                }
                student.expect("GAME_START");
                check(edt(() -> student.view.getViewState() == RoomState.PLAYING
                        && !student.view.isCountdownVisible()), "시작 확정 및 오버레이 제거");
                student.connection.send("START");
                student.denied("NOT_WAITING");
                if (student.inbox.poll(1200, TimeUnit.MILLISECONDS) != null) {
                    throw new AssertionError("취소된 예약 또는 중복 GAME_START 발생");
                }
                System.out.println("PASS: countdown order, host authorization, busy rejection, cancellation and restart");
            }
        }
        // 학동의 퇴장도 동일하게 시작을 취소해야 합니다.
        try (LobbyServer server = new LobbyServer(0); Client host = new Client(server, "jisu")) {
            host.expect("WELCOME|1"); host.expect("PLAYERS|1|1|1|jisu|0"); host.expect("SETTINGS|LOW|10|30");
            try (Client student = new Client(server, "yuna")) {
                student.expect("WELCOME|2");
                host.expect("PLAYERS|1|2|1|jisu|0|2|yuna|0");
                student.expect("PLAYERS|1|2|1|jisu|0|2|yuna|0"); student.expect("SETTINGS|LOW|10|30");
                host.connection.send("START");
                host.expect("COUNTDOWN|3"); student.expect("COUNTDOWN|3");
                student.close();
                host.expect("PLAYERS|1|1|1|jisu|0"); host.expect("START_CANCELLED|PLAYER_LEFT");
                check(edt(() -> host.view.getViewState() == RoomState.WAITING), "학동 퇴장 취소");
                if (host.inbox.poll(3200, TimeUnit.MILLISECONDS) != null) throw new AssertionError("취소 후 숫자가 남음");
                // 서버 종료 중 오버레이가 남는지도 검사합니다.
                host.connection.send("START"); host.expect("COUNTDOWN|3");
                server.close();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (edt(() -> !host.view.getParticipants().isEmpty())) {
                    if (System.nanoTime() > deadline) throw new AssertionError("연결 종료 처리 지연");
                    Thread.sleep(10);
                }
                check(edt(() -> !host.view.isCountdownVisible()), "연결 종료 시 오버레이 제거");
            }
        }
        System.out.println("NETWORK COUNTDOWN CHECK OK");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    private static <T> T edt(Supplier<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(action.get()));
        return result.get();
    }
}
