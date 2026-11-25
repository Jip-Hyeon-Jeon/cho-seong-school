package com.jiphyeonjeon.choseongschool.controller;

import com.jiphyeonjeon.choseongschool.common.GameConstants.Protocol;
import com.jiphyeonjeon.choseongschool.view.GameRoomView;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

/**
 * 게임 화면 ↔ 서버 연결.
 * sender: 서버로 한 줄 보내는 함수 (소켓 연결 담당 쪽에서 out::println 같은 걸 넘겨준다)
 * handle(): 소켓 수신 스레드가 서버 메시지 한 줄을 받을 때마다 호출한다
 */
public class GameRoomController {

    private static final Pattern SPLIT = Pattern.compile(Pattern.quote(Protocol.SEPARATOR));

    private final GameRoomView view;
    private final Consumer<String> sender;

    public GameRoomController(GameRoomView view, Consumer<String> sender) {
        this.view = view;
        this.sender = sender;
        view.getInputField().addActionListener(e -> submit()); // Enter
        view.getSendButton().addActionListener(e -> submit());
    }

    private void submit() {
        String text = view.getInputField().getText().trim();
        // 구분자가 들어가면 메시지 칸이 밀리므로 보내지 않는다 (서버도 같은 검사를 해야 한다)
        if (text.isEmpty() || text.contains(Protocol.SEPARATOR)) {
            return;
        }
        sender.accept(Protocol.ANSWER + Protocol.SEPARATOR + text);
        view.getInputField().setText("");
    }

    public void handle(String line) {
        String[] parts = SPLIT.split(line, -1); // -1: "HINT|" 처럼 빈 마지막 칸도 유지
        if (parts.length < (Protocol.ANSWER.equals(parts[0]) ? 4 : 2)) {
            return; // 칸이 모자란 메시지는 버린다
        }
        // 수신 스레드에서 호출되므로 화면 변경은 Swing 스레드로 넘긴다
        SwingUtilities.invokeLater(() -> {
            switch (parts[0]) {
                case Protocol.QUESTION -> view.showQuestion(parts[1]);
                case Protocol.HINT -> view.showHint(parts[1]);
                case Protocol.ANSWER -> view.showBubble(parts[1], parts[2], Protocol.CORRECT.equals(parts[3]));
                default -> { } // 다른 담당자의 메시지는 무시
            }
        });
    }
}
