package com.jiphyeonjeon.choseongschool.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jiphyeonjeon.choseongschool.client.view.GameRoomView;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class GameRoomControllerTest {

    /** 화면에 그리는 대신 어떤 메서드가 불렸는지만 기록하는 가짜 화면 */
    static class RecordingView extends GameRoomView {
        final List<String> calls = new ArrayList<>();

        @Override
        public void showQuestion(String question) {
            calls.add("Q:" + question);
        }

        @Override
        public void showHint(String meaning) {
            calls.add("H:" + meaning);
        }

        @Override
        public void showBubble(String nickname, String word, boolean correct) {
            calls.add("A:" + nickname + "," + word + "," + correct);
        }

        @Override
        public void setTimeLeft(int seconds, int totalSeconds) {
            calls.add("T:" + seconds + "/" + totalSeconds);
        }

        @Override
        public void setScore(String nickname, int score) {
            calls.add("S:" + nickname + "," + score);
        }

        @Override
        public void setRound(int round, int totalRounds) {
            calls.add("R:" + round + "/" + totalRounds);
        }

        @Override
        public void showRoundEnd(String answer, String meaning) {
            calls.add("E:" + answer + "," + meaning);
        }

        @Override
        public void jump(String nickname) {
            calls.add("J:" + nickname);
        }

        @Override
        public void setInputEnabled(boolean enabled) {
            calls.add("I:" + enabled);
        }
    }

    private final RecordingView view = new RecordingView();
    private final List<String> sent = new ArrayList<>();
    private final List<String> ended = new ArrayList<>();
    private final GameRoomController controller = new GameRoomController(view, sent::add);

    /** handle()은 invokeLater로 넘기므로, Swing 스레드가 다 처리할 때까지 기다린다 */
    private void handleAll(String... lines) throws Exception {
        controller.setGameEndListener(scores -> {
            for (GameRoomController.FinalScore s : scores) {
                ended.add(s.nickname() + ":" + s.score());
            }
        });
        for (String line : lines) {
            controller.handle(line);
        }
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void 문제_힌트_답을_화면에_그린다() throws Exception {
        handleAll("QUESTION|ㅅㅍㄱㅈ", "HINT|", "ANSWER|지수|사필귀점|X", "ANSWER|유나|사필귀정|O");
        // 오답은 말풍선만, 정답은 말풍선 + 점프 + 입력 잠금
        assertEquals(List.of("Q:ㅅㅍㄱㅈ", "I:true", "H:", "A:지수,사필귀점,false",
                "A:유나,사필귀정,true", "J:유나", "I:false"), view.calls);
    }

    @Test
    void 게임_진행_메시지를_화면에_그린다() throws Exception {
        handleAll("ROUND|3|10", "TIME|7|30", "SCORE|유나|120", "ROUND_END|사필귀정|모든 일은 반드시 바른 길로 돌아감");
        assertEquals(List.of("R:3/10", "T:7/30", "S:유나,120", "E:사필귀정,모든 일은 반드시 바른 길로 돌아감", "I:false"),
                view.calls);
    }

    @Test
    void 게임이_끝나면_입력을_잠그고_최종_점수를_넘긴다() throws Exception {
        handleAll("GAME_END|유나|300|지수|300|서영|120");
        assertEquals(List.of("I:false"), view.calls);
        assertEquals(List.of("유나:300", "지수:300", "서영:120"), ended);
    }

    @Test
    void 칸이_모자라거나_숫자가_잘못된_메시지는_버린다() throws Exception {
        handleAll("", "QUESTION", "ANSWER|유나|사필귀정", "TIME|칠|30", "SCORE|유나", "ROUND|3",
                "ROUND_END|사필귀정", "GAME_END|유나", "GAME_END|유나|10|지수", "GAME_END|유나|열");
        assertEquals(List.of(), view.calls);
        assertEquals(List.of(), ended);
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
