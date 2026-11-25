package com.jiphyeonjeon.choseongschool.view;

import java.util.List;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** 서버 없이 게임 화면만 띄워보는 용도 (Figma "4 게임 (한국풍)"과 같은 상태) */
public class GameRoomPreview {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameRoomView view = new GameRoomView();
            view.setPlayers(List.of("가", "나", "다"));
            view.setAvatar("나", Avatar.SAIMDANG);
            view.setAvatar("다", Avatar.YULGOK);
            view.setHost("가");
            view.setScore("가", 18);
            view.setScore("나", 24);
            view.setScore("다", 12);
            view.setRound(3, 10, "문장 · 난이도 중");
            view.showQuestion("ㅅㅅㅎ 사과를 드립니다");
            view.setTimeLeft(8, 30);
            view.showHint("뜻: 마음을 나타내는 정도가 매우 깊고 간절한");
            view.showBubble("가", "삼삼한", false);
            view.showBubble("나", "송구한?", false);
            view.getInputField().setText("심심");

            JFrame frame = new JFrame("초성서당 - 게임 화면 미리보기");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(view);
            frame.setResizable(false);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
