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
    }

    private final RecordingView view = new RecordingView();
    private final List<String> sent = new ArrayList<>();
    private final GameRoomController controller = new GameRoomController(view, sent::add);
    private final List<List<GameRoomController.FinalScore>> ended = new ArrayList<>();

    {
        controller.setGameEndListener(ended::add);
    }

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
    void 게임_진행_메시지를_화면에_그린다() throws Exception {
        handleAll("ROUND|3|10", "TIME|30|30", "TIME|7|30", "SCORE|유나|120", "ROUND_END|사필귀정|모든 일은 반드시 바른 길로 돌아감");
        assertEquals(List.of("R:3/10", "T:30/30", "T:7/30", "S:유나,120", "E:사필귀정,모든 일은 반드시 바른 길로 돌아감"),
                view.calls);
    }

    @Test
    void 숫자_칸이_잘못되거나_모자란_진행_메시지는_버린다() throws Exception {
        handleAll("TIME|칠|30", "TIME|7", "SCORE|유나", "SCORE|유나|많이", "ROUND|3", "ROUND_END|사필귀정",
                "GAME_END", "GAME_END|유나", "GAME_END|유나|10|지수", "GAME_END|유나|열");
        assertEquals(List.of(), view.calls);
        assertEquals(List.of(), ended);
    }

    @Test
    void 게임_종료면_최종_점수를_넘긴다() throws Exception {
        handleAll("GAME_END|유나|300|지수|300|서영|120");
        assertEquals(List.of(List.of(
                new GameRoomController.FinalScore("유나", 300),
                new GameRoomController.FinalScore("지수", 300),
                new GameRoomController.FinalScore("서영", 120))), ended);
    }

    @Test
    void 라운드_안내_문구는_처음_정한_것을_유지한다() {
        GameRoomView real = new GameRoomView();
        real.setRound(1, 10, "난이도 하");
        real.setRound(4, 10);
        assertEquals("10강 중 · 난이도 하", roundInfoText(real));
    }

    /** 팻말 둘째 줄 라벨 글자 (화면 구조를 직접 뒤지지 않도록 텍스트로만 찾는다) */
    private static String roundInfoText(java.awt.Container root) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JLabel label && label.getText() != null && label.getText().contains("강 중")) {
                return label.getText();
            }
            if (c instanceof java.awt.Container child) {
                String found = roundInfoText(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
