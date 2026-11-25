package com.jiphyeonjeon.choseongschool.client.view;

import java.util.List;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * 서버 없이 라운드 결과 연출만 확인하는 용도.
 * 1초 뒤 "나"가 정답을 맞혀 점프하고 입력창이 잠기며, 다시 1.5초 뒤 정답·뜻이 공개된다. 3초 뒤 다음 문제로 돌아간다.
 */
public class GameRoundEndPreview {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameRoomView view = new GameRoomView();
            view.setPlayers(List.of("가", "나", "다"));
            view.setAvatar("나", Avatar.SAIMDANG);
            view.setAvatar("다", Avatar.YULGOK);
            view.setHost("가");
            view.setRound(3, 10, "난이도 중");
            view.showQuestion("ㅅㅍㄱㅈ 이라더니 결국 진실이 밝혀졌다");
            view.setTimeLeft(12, 30);
            view.showBubble("가", "사필귀점", false);

            JFrame frame = new JFrame("초성서당 - 라운드 결과 연출 미리보기");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(view);
            frame.setResizable(false);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            later(1000, () -> {
                view.showBubble("나", "사필귀정", true);
                view.jump("나");
                view.setInputEnabled(false);
            });
            later(2500, () -> view.showRoundEnd("사필귀정", "모든 일은 반드시 바른 길로 돌아감"));
            later(5500, () -> {
                view.setRound(4, 10);
                view.showQuestion("ㄱㅅㅊㅎ로 날씨까지 맑았다");
                view.setInputEnabled(true);
            });
        });
    }

    private static void later(int millis, Runnable action) {
        Timer timer = new Timer(millis, e -> action.run());
        timer.setRepeats(false);
        timer.start();
    }
}
