package com.jiphyeonjeon.choseongschool.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class GameRoomViewTest {

    private final GameRoomView view = new GameRoomView();

    @Test
    void 입력창과_제출_버튼을_함께_잠그고_푼다() {
        view.setInputEnabled(false);
        assertEquals(List.of(false, false), List.of(view.getInputField().isEnabled(), view.getSendButton().isEnabled()));
        view.setInputEnabled(true);
        assertEquals(List.of(true, true), List.of(view.getInputField().isEnabled(), view.getSendButton().isEnabled()));
    }

    @Test
    void 정답_공개는_청록색이고_다음_문제에서_먹색으로_돌아온다() {
        view.showRoundEnd("사필귀정", "모든 일은 반드시 바른 길로 돌아감");
        assertEquals(Theme.GREEN, colorOf("사필귀정"));
        view.showQuestion("ㅅㅍㄱㅈ");
        assertEquals(Theme.TEXT, colorOf("ㅅㅍㄱㅈ"));
    }

    @Test
    void 점프_중에도_학동_자리_높이는_그대로다() throws Exception {
        view.setPlayers(List.of("유나"));
        Component seat = find(view, "Seat");
        int before = seat.getPreferredSize().height;
        SwingUtilities.invokeAndWait(() -> view.jump("유나"));

        AtomicInteger maxDiff = new AtomicInteger();
        for (int i = 0; i < 10; i++) { // 0.7초 점프 동안 여러 번 재 본다
            Thread.sleep(80);
            SwingUtilities.invokeAndWait(() ->
                    maxDiff.accumulateAndGet(Math.abs(seat.getPreferredSize().height - before), Math::max));
        }
        assertEquals(0, maxDiff.get());
    }

    private Color colorOf(String text) {
        return ((JLabel) findLabel(view, text)).getForeground();
    }

    private static Component findLabel(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (c instanceof Container child) {
                Component found = findLabel(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Component find(Container root, String simpleName) {
        for (Component c : root.getComponents()) {
            if (c.getClass().getSimpleName().equals(simpleName)) {
                return c;
            }
            if (c instanceof Container child) {
                Component found = find(child, simpleName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
