package com.jiphyeonjeon.choseongschool.client;

import com.jiphyeonjeon.choseongschool.client.view.Theme;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;

/** 팀 공통 크기(Theme.SCREEN_WIDTH, Theme.SCREEN_HEIGHT) 창 하나. 로그인 → 대기실 → 게임 화면을 이 창의 내용만 바꿔 보여 준다 */
public final class MainFrame implements ScreenHost {

    private final JFrame frame = new JFrame("초성서당");
    private Runnable onClose = () -> { };

    public MainFrame() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose.run();
            }
        });
    }

    /** 창을 닫을 때 실행할 정리 작업 (소켓 연결 종료 등) */
    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @Override
    public void show(JComponent screen) {
        screen.setPreferredSize(new Dimension(Theme.SCREEN_WIDTH, Theme.SCREEN_HEIGHT));
        frame.setContentPane(screen);
        frame.revalidate();
        frame.repaint();
        if (!frame.isVisible()) {
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }
    }
}
