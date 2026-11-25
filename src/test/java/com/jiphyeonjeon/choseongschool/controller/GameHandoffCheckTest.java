package com.jiphyeonjeon.choseongschool.controller;
import com.jiphyeonjeon.choseongschool.model.*;
import com.jiphyeonjeon.choseongschool.view.*;
import com.jiphyeonjeon.choseongschool.client.*;
import com.jiphyeonjeon.choseongschool.network.*;
import com.jiphyeonjeon.choseongschool.server.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** 소켓 수신 콜백과 같은 방식으로 연속 메시지를 전달하여 전환 순서를 검증합니다. */
final class GameHandoffCheckTest {
    @org.junit.jupiter.api.Test
    void verifiesBehavior() throws Exception {
        AtomicReference<LobbyView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> view.set(new LobbyView()));
        List<String> events = new ArrayList<>();
        AtomicReference<GameSession> started = new AtomicReference<>();
        GameScreenListener listener = new GameScreenListener() {
            private void record(String event) {
                if (!SwingUtilities.isEventDispatchThread()) throw new AssertionError("EDT required");
                events.add(event);
            }
            public void onStart(GameSession session) { started.set(session); record("START"); }
            public void onMessage(String line) { record(line); }
            public void onPlayers(List<ParticipantInfo> players) { record("PLAYERS:" + players.size()); }
            public void onDisconnected(String message) { record("CLOSED"); }
        };
        NetworkLobbyController controller = new NetworkLobbyController(view.get(), listener);
        controller.handle("WELCOME|1");
        controller.handle("PLAYERS|1|2|1|jisu|0|2|yuna|2");
        controller.handle("SETTINGS|HIGH|20|15");
        controller.handle("QUESTION|시작 전 무시");
        controller.handle("GAME_START"); // 유효한 카운트다운을 거치지 않음
        controller.handle("COUNTDOWN|3");
        controller.handle("START_CANCELLED|PLAYER_LEFT");
        controller.handle("GAME_START"); // 취소된 시작도 무시
        controller.handle("COUNTDOWN|3");
        controller.handle("COUNTDOWN|2");
        controller.handle("COUNTDOWN|1");
        controller.handle("GAME_START");
        controller.handle("QUESTION|ㅎㄱ"); // 전환 직후 메시지가 유실되면 안 됨
        controller.handle("HINT|배우는 곳");
        controller.handle("ANSWER|jisu|학교|O");
        controller.handle("GAME_START"); // 중복 전환 금지
        controller.handle("PLAYERS|1|1|1|jisu|0");
        controller.onClosed();
        controller.onClosed();
        controller.handle("QUESTION|종료 후 무시");
        SwingUtilities.invokeAndWait(() -> {
            List<String> expected = List.of("START", "QUESTION|ㅎㄱ", "HINT|배우는 곳",
                    "ANSWER|jisu|학교|O", "PLAYERS:1", "CLOSED");
            if (!events.equals(expected)) throw new AssertionError(events);
            GameSession session = started.get();
            if (!session.localId().equals("1") || !session.hostId().equals("1")
                    || session.players().size() != 2 || session.settings().rounds() != 20
                    || session.settings().seconds() != 15 || session.settings().difficulty() != Difficulty.HIGH) {
                throw new AssertionError("시작 정보 보존 실패");
            }
            try {
                session.players().clear();
                throw new AssertionError("시작 목록이 변경 가능함");
            } catch (UnsupportedOperationException expectedException) { }
        });
        System.out.println("GAME HANDOFF CHECK OK");
    }
}
