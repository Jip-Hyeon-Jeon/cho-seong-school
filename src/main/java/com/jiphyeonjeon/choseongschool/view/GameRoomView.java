package com.jiphyeonjeon.choseongschool.view;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/** 게임 화면: 위 초성 · 가운데 뜻(힌트) · 아래 입력창, 오른쪽 플레이어 + 말풍선 */
public class GameRoomView extends JPanel {

    private final JLabel chosungLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel meaningLabel = new JLabel("", SwingConstants.CENTER);
    /** 타이머 표시 자리 (타이머 로직은 송서영 담당) */
    private final JLabel timerLabel = new JLabel("", SwingConstants.CENTER);
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("제출");
    private final JPanel playerPanel = new JPanel();
    /** 닉네임 → 그 플레이어 위 말풍선 */
    private final Map<String, JLabel> bubbles = new LinkedHashMap<>();

    public GameRoomView() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        chosungLabel.setFont(chosungLabel.getFont().deriveFont(Font.BOLD, 48f));
        meaningLabel.setFont(meaningLabel.getFont().deriveFont(20f));

        JPanel center = new JPanel(new GridLayout(3, 1));
        center.add(timerLabel);
        center.add(chosungLabel);
        center.add(meaningLabel);

        JPanel input = new JPanel(new BorderLayout(5, 0));
        input.add(inputField, BorderLayout.CENTER);
        input.add(sendButton, BorderLayout.EAST);

        playerPanel.setLayout(new BoxLayout(playerPanel, BoxLayout.Y_AXIS));

        add(center, BorderLayout.CENTER);
        add(input, BorderLayout.SOUTH);
        add(playerPanel, BorderLayout.EAST);
    }

    public void setPlayers(List<String> nicknames) {
        playerPanel.removeAll();
        bubbles.clear();
        for (String nickname : nicknames) {
            JLabel bubble = new JLabel(" ");
            bubble.setBorder(BorderFactory.createLineBorder(getForeground()));
            bubbles.put(nickname, bubble);
            playerPanel.add(bubble);
            playerPanel.add(new JLabel(nickname));
        }
        playerPanel.revalidate();
        playerPanel.repaint();
    }

    public void showQuestion(String chosung) {
        chosungLabel.setText(chosung);
        meaningLabel.setText("");
        for (JLabel bubble : bubbles.values()) {
            bubble.setText(" ");
        }
    }

    public void showHint(String meaning) {
        meaningLabel.setText(meaning);
    }

    /** 해당 플레이어 위 말풍선에 입력 단어 + V(정답)/X(오답) */
    public void showBubble(String nickname, String word, boolean correct) {
        JLabel bubble = bubbles.get(nickname);
        if (bubble != null) {
            bubble.setText(word + (correct ? " V" : " X"));
        }
    }

    public void setTimerText(String text) {
        timerLabel.setText(text);
    }

    public JTextField getInputField() {
        return inputField;
    }

    public JButton getSendButton() {
        return sendButton;
    }
}
