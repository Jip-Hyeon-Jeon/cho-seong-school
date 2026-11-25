package com.jiphyeonjeon.choseongschool.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiphyeonjeon.choseongschool.lobby.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.lobby.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.lobby.server.GameEvents;
import com.jiphyeonjeon.choseongschool.lobby.server.LobbyServer;
import com.jiphyeonjeon.choseongschool.lobby.view.LobbyContainer;
import com.jiphyeonjeon.choseongschool.lobby.view.LobbyView;
import com.jiphyeonjeon.choseongschool.view.GameRoomView;
import com.jiphyeonjeon.choseongschool.view.LoginView;
import com.jiphyeonjeon.choseongschool.view.ResultView;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** 창 없이 화면 전환만 기록해 로그인 → 대기실 → 게임 흐름을 실제 소켓으로 확인한다 */
class ClientFlowTest {

    /** 보여 준 화면을 순서대로 기록하는 가짜 창 */
    private static final class RecordingHost implements ScreenHost {
        final List<JComponent> shown = new CopyOnWriteArrayList<>();

        @Override
        public void show(JComponent screen) {
            shown.add(screen);
        }

        JComponent last() {
            return shown.get(shown.size() - 1);
        }
    }

    /** 게임이 시작되면 문제 하나를 보내는 서버 쪽 게임 로직 */
    private static final class OneQuestionGame implements GameEvents {
        final AtomicBoolean started = new AtomicBoolean();

        @Override
        public void onStart(List<ParticipantInfo> players, LobbySettings settings, Consumer<String> broadcast) {
            started.set(true);
            broadcast.accept("QUESTION|ㅊㅅ");
        }

        @Override
        public void onAnswer(ParticipantInfo player, String answer, Consumer<String> broadcast) {
        }
    }

    @Test
    void loginGoesToLobbyAndStartGoesToGameScreen() throws Exception {
        OneQuestionGame game = new OneQuestionGame();
        try (LobbyServer server = new LobbyServer(InetAddress.getLoopbackAddress(), 0, game)) {
            RecordingHost host = new RecordingHost();
            AtomicReference<ClientFlow> flow = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                flow.set(new ClientFlow(host, server.getPort()));
                flow.get().showLogin();
                flow.get().enterLobby("127.0.0.1", "지수", 1);
            });
            assertInstanceOf(LoginView.class, host.shown.get(0));
            LobbyView lobby = (LobbyView) ((LobbyContainer) host.last()).getComponent(0);

            // 서버가 학칙을 보내 반장의 시작 버튼이 켜지면 누른다
            waitOnEdt(() -> lobby.getStartButton().isEnabled());
            SwingUtilities.invokeAndWait(() -> lobby.getStartButton().doClick());

            // 3·2·1 카운트다운 뒤 같은 창이 게임 화면으로 바뀐다
            waitOnEdt(() -> host.last() instanceof GameRoomView);
            assertTrue(game.started.get());

            SwingUtilities.invokeAndWait(() -> flow.get().close());
        }
    }

    /** 시작하면 바로 게임을 끝내는 서버 쪽 게임 로직 */
    private static final class InstantGame implements GameEvents {
        @Override
        public void onStart(List<ParticipantInfo> players, LobbySettings settings, Consumer<String> broadcast) {
            broadcast.accept("QUESTION|ㅊㅅ");
            broadcast.accept("GAME_END|지수|10");
        }

        @Override
        public void onAnswer(ParticipantInfo player, String answer, Consumer<String> broadcast) {
        }
    }

    @Test
    void gameEndShowsResultAndWaitingRoomButtonReturnsToLobby() throws Exception {
        try (LobbyServer server = new LobbyServer(InetAddress.getLoopbackAddress(), 0, new InstantGame())) {
            RecordingHost host = new RecordingHost();
            AtomicReference<ClientFlow> flow = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                flow.set(new ClientFlow(host, server.getPort()));
                flow.get().enterLobby("127.0.0.1", "지수", 3);
            });
            LobbyContainer lobbyScreen = (LobbyContainer) host.last();
            LobbyView lobby = (LobbyView) lobbyScreen.getComponent(0);
            waitOnEdt(() -> lobby.getStartButton().isEnabled());
            SwingUtilities.invokeAndWait(() -> lobby.getStartButton().doClick());

            // 게임 화면을 거쳐 GAME_END를 받으면 결과 화면으로 바뀐다
            waitOnEdt(() -> host.last() instanceof ResultView);
            assertTrue(host.shown.stream().anyMatch(GameRoomView.class::isInstance));
            ResultView result = (ResultView) host.last();

            // "서당 마루로" → 같은 연결의 대기실로 돌아가고, 반장은 다시 시작할 수 있다
            SwingUtilities.invokeAndWait(() -> button(result, "서당 마루로").doClick());
            assertTrue(host.last() == lobbyScreen);
            waitOnEdt(() -> lobby.getStartButton().isEnabled());
            int before = host.shown.size();
            SwingUtilities.invokeAndWait(() -> lobby.getStartButton().doClick());
            waitOnEdt(() -> host.shown.size() > before && host.last() instanceof ResultView);

            // 결과 화면 "나가기" → 로그인 화면
            ResultView second = (ResultView) host.last();
            SwingUtilities.invokeAndWait(() -> button(second, "나가기").doClick());
            assertInstanceOf(LoginView.class, host.last());
        }
    }

    private static JButton button(java.awt.Container container, String text) {
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) {
                return b;
            }
        }
        throw new AssertionError("버튼 없음: " + text);
    }

    @Test
    void exitInLobbyReturnsToLogin() throws Exception {
        try (LobbyServer server = new LobbyServer(0)) {
            RecordingHost host = new RecordingHost();
            AtomicReference<ClientFlow> flow = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                flow.set(new ClientFlow(host, server.getPort()));
                flow.get().showLogin();
                flow.get().enterLobby("127.0.0.1", "유나", 0);
            });
            LobbyView lobby = (LobbyView) ((LobbyContainer) host.last()).getComponent(0);
            waitOnEdt(() -> !lobby.getParticipants().isEmpty());

            SwingUtilities.invokeAndWait(() -> lobby.getExitButton().doClick());
            assertInstanceOf(LoginView.class, host.last());
            // 처음 로그인 화면을 다시 보여 주므로 입력한 값이 남는다
            assertTrue(host.last() == host.shown.get(0));
        }
    }

    @Test
    void serverNotRunningStillLetsExitReturnToLogin() throws Exception {
        int unusedPort;
        try (LobbyServer server = new LobbyServer(0)) {
            unusedPort = server.getPort();
        }
        RecordingHost host = new RecordingHost();
        AtomicReference<ClientFlow> flow = new AtomicReference<>();
        int port = unusedPort;
        SwingUtilities.invokeAndWait(() -> {
            flow.set(new ClientFlow(host, port));
            flow.get().enterLobby("127.0.0.1", "서영", 2);
        });
        LobbyView lobby = (LobbyView) ((LobbyContainer) host.last()).getComponent(0);
        Thread.sleep(500); // 접속 실패 안내가 대기실에 뜰 시간

        SwingUtilities.invokeAndWait(() -> lobby.getExitButton().doClick());
        assertInstanceOf(LoginView.class, host.last());
    }

    /** 조건이 참이 될 때까지 Swing 스레드에서 확인한다 (최대 10초) */
    private static void waitOnEdt(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        AtomicBoolean ok = new AtomicBoolean();
        while (System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait(() -> ok.set(condition.getAsBoolean()));
            if (ok.get()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("시간 안에 화면이 바뀌지 않았습니다.");
    }
}
