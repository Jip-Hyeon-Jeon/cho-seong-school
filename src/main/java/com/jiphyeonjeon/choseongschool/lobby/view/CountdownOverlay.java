package com.jiphyeonjeon.choseongschool.lobby.view;

import java.awt.*;
import java.awt.event.MouseAdapter;
import javax.swing.*;
import javax.swing.border.*;

// 전달받은 카운트다운 숫자를 표시하는 덮개 화면
public class CountdownOverlay extends JPanel {
    private final JLabel number = new JLabel("3", SwingConstants.CENTER);

    public CountdownOverlay() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        // 아래 대기실로 전달되는 마우스 클릭 차단
        addMouseListener(new MouseAdapter() { });

        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(LobbyView.CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(LobbyView.GOLD, 3),
                new EmptyBorder(30, 64, 30, 64)));
        JLabel title = new JLabel("잠시 후 과거를 시작합니다", SwingConstants.CENTER);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 25));
        title.setForeground(LobbyView.INK);
        number.setFont(new Font("Serif", Font.BOLD, 150));
        number.setForeground(LobbyView.RED);
        JLabel subtitle = new JLabel("마음을 가다듬고 준비해 주세요", SwingConstants.CENTER);
        subtitle.setFont(new Font("맑은 고딕", Font.PLAIN, 18));
        subtitle.setForeground(new Color(103, 86, 62));
        card.add(title, BorderLayout.NORTH);
        card.add(number, BorderLayout.CENTER);
        card.add(subtitle, BorderLayout.SOUTH);
        add(card);
    }

    public void showNumber(int value) {
        number.setText(Integer.toString(value));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(new Color(28, 24, 20, 120));
        g.fillRect(0, 0, getWidth(), getHeight());
    }
}
