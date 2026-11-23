package com.jiphyeonjeon.choseongschool.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jiphyeonjeon.choseongschool.view.GameRoomView;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class GameRoomControllerTest {

    /** 화면에 그려진 내용 대신 호출 기록만 남기는 가짜 화면 */
    static class RecordingView extends GameRoomView {
        final List<String> calls = new ArrayList<>();

        @Override
        public void showQuestion(String chosung) {
            calls.add("Q:" + chosung);
        }

        @Override
        public void showHint(String meaning) {
            calls.add("H:" + meaning);
        }

        @Override
        public void showBubble(String nickname, String word, boolean correct) {
            calls.add("A:" + nickname + "," + word + "," + correct);
        }
    }

    private final RecordingView view = new RecordingView();
    private final List<String> sent = new ArrayList<>();
    private final GameRoomController controller = new GameRoomController(view, sent::add);

    /** handle()은 invokeLater로 넘기므로 Swing 스레드가 다 처리할 때까지 기다린다 */
    private void handleAll(String... lines) throws Exception {
        for (String line : lines) {
            controller.handle(line);
        }
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void 서버_메시지를_화면에_그린다() throws Exception {
        handleAll("QUESTION|ㅅㅍㄱㅈ", "HINT|", "ANSWER|유나|사필귀정|O", "ANSWER|지수|사필귀점|X");
        assertEquals(List.of("Q:ㅅㅍㄱㅈ", "H:", "A:유나,사필귀정,true", "A:지수,사필귀점,false"), view.calls);
    }

    @Test
    void 칸이_모자란_메시지는_버린다() throws Exception {
        handleAll("QUESTION", "HINT", "ANSWER|유나", "ANSWER|유나|사필귀정", "");
        assertEquals(List.of(), view.calls);
    }

    @Test
    void 구분자가_들어간_답은_보내지_않는다() {
        view.getInputField().setText("사필|귀정");
        view.getSendButton().doClick();
        view.getInputField().setText(" 사필귀정 ");
        view.getSendButton().doClick();
        assertEquals(List.of("ANSWER|사필귀정"), sent);
    }
}
