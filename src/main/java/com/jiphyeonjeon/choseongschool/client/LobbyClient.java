package com.jiphyeonjeon.choseongschool.client;

import com.jiphyeonjeon.choseongschool.client.controller.NetworkLobbyController;
import com.jiphyeonjeon.choseongschool.client.model.LobbyState;
import com.jiphyeonjeon.choseongschool.client.network.TcpConnection;
import com.jiphyeonjeon.choseongschool.client.view.LobbyView;
import com.jiphyeonjeon.choseongschool.common.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.common.network.LineMessages;

import javax.swing.SwingUtilities;

/**
 * 로그인 이후 연결 수명 관리 및 게임 화면과 공유하는 송신 기능.
 * 10주차 채팅 클라이언트처럼 연결은 별도 Thread에서 만들고, 보내기는 TcpConnection.send(synchronized)로 직접 한다.
 */
public final class LobbyClient implements AutoCloseable {
    private final NetworkLobbyController controller;
    private TcpConnection connection;   // synchronized 메서드로만 읽고 쓴다
    private boolean closed;
    private boolean started;

    public LobbyClient(LobbyView view, GameScreenListener gameScreen) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Swing 스레드에서 생성하세요.");
        // 학칙 변경·시작 버튼 요청은 컨트롤러가 권한을 확인한 뒤 이 연결로 보낸다
        controller = new NetworkLobbyController(view, gameScreen, this::send);
    }

    /** 대기실 상태 (참가자·반장·학칙 등 읽기용). Swing 스레드에서만 읽는다 */
    public LobbyState lobbyState() {
        return controller.state();
    }

    /** 로그인에서 선택한 캐릭터 번호(0~4) 수신 및 별도 스레드의 서버 연결 */
    public void connect(String host, int port, String nickname, int character) {
        if (host == null || host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("접속 주소를 확인하세요.");
        ParticipantInfo player = new ParticipantInfo("local", nickname == null ? null : nickname.trim(), character);
        markStarted();
        Thread connector = new Thread(() -> {
            try {
                TcpConnection current = new TcpConnection(host, port);
                if (!setConnection(current)) return; // 연결 중에 이미 닫혔으면 정리하고 끝
                current.startReceiving(controller::handle, () -> {
                    controller.onClosed();
                    close();
                });
                current.send("JOIN|" + player.nickname() + "|" + player.character());
            } catch (Exception e) {
                if (!isClosed()) controller.onConnectFailed();
                close();
            }
        }, "lobby-connect");
        connector.setDaemon(true);
        connector.start();
    }

    /** 한 줄 전송. 실패하면 컨트롤러에 알리고 연결을 닫는다 */
    public void send(String line) {
        LineMessages.validate(line);
        TcpConnection current = currentConnection();
        if (current == null) {
            if (!isClosed()) failSend();
            return;
        }
        try {
            current.send(line);
        } catch (RuntimeException e) {
            failSend();
        }
    }

    private void failSend() {
        controller.onSendFailed();
        close();
    }

    @Override
    public void close() {
        TcpConnection current;
        synchronized (this) {
            if (closed) return;
            closed = true;
            current = connection;
        }
        if (current != null) current.close();
        controller.onClosed();
    }

    private synchronized void markStarted() {
        if (closed || started) throw new IllegalStateException("연결은 한 번만 시작합니다.");
        started = true;
    }

    /** 닫힌 뒤에 연결이 만들어졌다면 바로 닫고 false */
    private synchronized boolean setConnection(TcpConnection current) {
        if (closed) {
            current.close();
            return false;
        }
        connection = current;
        return true;
    }

    private synchronized TcpConnection currentConnection() {
        return closed ? null : connection;
    }

    private synchronized boolean isClosed() {
        return closed;
    }
}
