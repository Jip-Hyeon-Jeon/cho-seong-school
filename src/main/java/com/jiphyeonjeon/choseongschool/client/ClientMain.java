package com.jiphyeonjeon.choseongschool.client;

import com.jiphyeonjeon.choseongschool.common.LobbyConstants;
import javax.swing.SwingUtilities;

/** 클라이언트 실행 시작점: 창과 화면 흐름을 만들어 연결하고 로그인 화면을 띄운다 */
public final class ClientMain {

    private ClientMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            ClientFlow flow = new ClientFlow(frame, LobbyConstants.DEFAULT_PORT);
            frame.setOnClose(flow::close);
            flow.showLogin();
        });
    }
}
