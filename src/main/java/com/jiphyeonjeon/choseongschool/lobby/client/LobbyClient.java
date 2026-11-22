package com.jiphyeonjeon.choseongschool.lobby.client;

import com.jiphyeonjeon.choseongschool.lobby.network.LineMessages;
import com.jiphyeonjeon.choseongschool.lobby.model.LobbyConstants;
import com.jiphyeonjeon.choseongschool.lobby.view.LobbyView;
import com.jiphyeonjeon.choseongschool.lobby.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.lobby.network.TcpConnection;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** 로그인 이후 연결 수명 관리 및 게임 화면과 공유하는 송신 기능 */
public final class LobbyClient implements AutoCloseable {
    private final NetworkLobbyController controller;
    private final AtomicReference<TcpConnection> connection = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(LobbyConstants.SEND_QUEUE_CAPACITY), runnable -> {
                Thread thread = new Thread(runnable, "lobby-sender");
                thread.setDaemon(true);
                return thread;
            });

    public LobbyClient(LobbyView view, GameScreenListener gameScreen) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Swing 스레드에서 생성하세요.");
        controller = new NetworkLobbyController(view, gameScreen);
        view.setSettingsRequest(settings -> send("SET_SETTINGS|" + settings.fields()));
        view.setStartRequest(() -> send("START"));
    }

    /** 로그인에서 선택한 캐릭터 번호(0~4) 수신 및 별도 스레드의 서버 연결 */
    public void connect(String host, int port, String nickname, int character) {
        if (host == null || host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("접속 주소를 확인하세요.");
        ParticipantInfo player = new ParticipantInfo("local", nickname == null ? null : nickname.trim(), character);
        if (closed.get() || !started.compareAndSet(false, true)) throw new IllegalStateException("연결은 한 번만 시작합니다.");
        Thread connector = new Thread(() -> {
            try {
                TcpConnection current = new TcpConnection(host, port);
                connection.set(current);
                if (closed.get()) { current.close(); return; }
                current.startReceiving(controller::handle, () -> {
                    controller.onClosed();
                    close();
                });
                current.send("JOIN|" + player.nickname() + "|" + player.character());
            } catch (Exception e) {
                if (!closed.get()) controller.onConnectFailed();
                close();
            }
        }, "lobby-connect");
        connector.setDaemon(true);
        connector.start();
    }

    /** Swing 이벤트 스레드를 막지 않는 비동기 송신 */
    public void send(String line) {
        LineMessages.validate(line);
        if (closed.get()) return;
        try {
            sender.execute(() -> {
                TcpConnection current = connection.get();
                if (closed.get()) return;
                if (current == null) { failSend(); return; }
                try { current.send(line); }
                catch (RuntimeException e) { failSend(); }
            });
        } catch (RejectedExecutionException e) {
            if (!closed.get()) failSend();
        }
    }

    private void failSend() {
        controller.onSendFailed();
        close();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        TcpConnection current = connection.get();
        if (current != null) current.close();
        sender.shutdownNow();
        controller.onClosed();
    }
}
