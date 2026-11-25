package com.jiphyeonjeon.choseongschool.client;

import com.jiphyeonjeon.choseongschool.controller.GameRoomController;
import com.jiphyeonjeon.choseongschool.controller.LoginController;
import com.jiphyeonjeon.choseongschool.model.GameSession;
import com.jiphyeonjeon.choseongschool.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.view.LobbyContainer;
import com.jiphyeonjeon.choseongschool.view.LobbyView;
import com.jiphyeonjeon.choseongschool.view.Avatar;
import com.jiphyeonjeon.choseongschool.view.GameRoomView;
import com.jiphyeonjeon.choseongschool.view.LoginView;
import java.util.List;
import javax.swing.JOptionPane;

/**
 * 클라이언트 화면 흐름: 로그인 → 대기실 → 게임.
 * 화면과 컨트롤러를 만들어 서로 연결하고, 소켓 연결(LobbyClient)은 대기실과 게임 화면이 함께 쓴다.
 * 모든 메서드는 Swing 이벤트 스레드에서 실행된다.
 */
public final class ClientFlow {

    private final ScreenHost host;
    private final int port;
    private final LoginView loginView = new LoginView();

    /** 지금 쓰는 연결. 로그인 화면에서는 null */
    private Connection connection;

    public ClientFlow(ScreenHost host, int port) {
        this.host = host;
        this.port = port;
        new LoginController(loginView, this::enterLobby);
    }

    /** 로그인 화면. 이전에 입력한 주소와 이름은 그대로 남는다 */
    public void showLogin() {
        host.show(loginView);
    }

    /** 로그인 검증 통과 → 대기실 화면으로 바꾸고 서버에 접속 */
    void enterLobby(String serverIp, String nickname, int characterIndex) {
        closeConnection();
        Connection next = new Connection();
        try {
            next.client.connect(serverIp, port, nickname, characterIndex);
        } catch (IllegalArgumentException e) {
            next.client.close();
            JOptionPane.showMessageDialog(loginView,
                    "접속 정보를 확인해 주세요. 이름에는 | 기호나 줄바꿈 같은 특수 문자를 쓸 수 없습니다.");
            return;
        }
        connection = next;
        // 접속 실패·입장 거절 시 대기실에 안내가 뜨고, 나가기를 누르면 로그인으로 돌아간다
        next.lobbyView.getExitButton().addActionListener(e -> backToLogin());
        host.show(new LobbyContainer(next.lobbyView));
    }

    /** 대기실 나가기, 게임 중 연결 끊김 → 연결을 정리하고 로그인 화면으로 */
    void backToLogin() {
        closeConnection();
        showLogin();
    }

    /** 창을 닫을 때 소켓 정리 */
    public void close() {
        closeConnection();
    }

    private void closeConnection() {
        Connection old = connection;
        connection = null; // 먼저 비워서, 닫히며 들어오는 이전 연결의 콜백은 무시된다
        if (old != null) {
            old.client.close();
        }
    }

    /** 로그인 한 번에 대응하는 연결 하나와 그 연결이 쓰는 화면들 */
    private final class Connection implements GameScreenListener {

        private final LobbyView lobbyView = new LobbyView();
        private final LobbyClient client = new LobbyClient(lobbyView, this);
        private GameRoomView gameView;
        private GameRoomController gameController;

        /** 이미 닫았거나 새 로그인으로 바뀐 연결이면 false */
        private boolean active() {
            return connection == this;
        }

        @Override
        public void onStart(GameSession session) {
            if (!active()) {
                return;
            }
            gameView = new GameRoomView();
            gameController = new GameRoomController(gameView, client::send); // 같은 소켓으로 답 제출
            LobbySettings settings = session.settings();
            gameView.setRound(1, settings.rounds(), "난이도 " + settings.difficulty().getLabel());
            gameView.setTimeLeft(settings.seconds(), settings.seconds());
            showPlayers(session.players(), session.hostId());
            host.show(gameView);
        }

        @Override
        public void onMessage(String line) {
            if (active() && gameController != null) {
                gameController.handle(line);
            }
        }

        /** 게임 중 누가 나가면 학동 줄을 다시 그린다 (반장이 나가면 새 반장 표시) */
        @Override
        public void onPlayers(List<ParticipantInfo> players) {
            if (active() && gameView != null) {
                showPlayers(players, lobbyView.getHostId());
            }
        }

        @Override
        public void onDisconnected(String message) {
            if (!active() || gameView == null) {
                return; // 대기실에서는 LobbyView가 직접 안내한다
            }
            JOptionPane.showMessageDialog(gameView, message);
            backToLogin();
        }

        private void showPlayers(List<ParticipantInfo> players, String hostId) {
            gameView.setPlayers(players.stream().map(ParticipantInfo::nickname).toList());
            for (ParticipantInfo player : players) {
                // 로그인 캐릭터 번호와 Avatar 순서가 같다
                gameView.setAvatar(player.nickname(), Avatar.values()[player.character()]);
                if (player.id().equals(hostId)) {
                    gameView.setHost(player.nickname());
                }
            }
        }
    }
}
