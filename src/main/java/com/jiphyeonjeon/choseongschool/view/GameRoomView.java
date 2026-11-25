package com.jiphyeonjeon.choseongschool.view;

import com.jiphyeonjeon.choseongschool.common.GameConstants.Screen;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * 게임 화면 (Figma "4 게임 (한국풍)", 1200×740).
 * 위: 강 팻말 + 문제 두루마리 / 향 타이머 / 훈장님 귀띔(힌트) / 답안 입력 / 아래: 학동 줄(말풍선 · 캐릭터 · 명패 · 점수)
 */
public class GameRoomView extends JPanel {

    private static final float QUESTION_FONT_SIZE = 40f;
    /** 두루마리 종이(약 900px) 안에서 문제 글자가 쓸 수 있는 폭 */
    private static final int QUESTION_MAX_WIDTH = 860;
    /** 귀띔 상자(약 1100px) 안에서 뜻이 한 줄에 쓸 수 있는 폭 */
    private static final int HINT_MAX_WIDTH = 1000;

    private final JLabel roundLabel = label("제 1 강", Theme.brush(42), Theme.INK);
    private final JLabel roundInfoLabel = label(" ", Theme.batang(12), Theme.WOOD_TEXT);
    private final JLabel questionLabel = label(" ", Theme.hahmlet(QUESTION_FONT_SIZE), Theme.INK);
    private final IncenseTimer incense = new IncenseTimer();
    private final JLabel secondsLabel = label("", Theme.hahmlet(40), Theme.SEAL);
    private final RoundBox hintSeal = seal("훈장님 귀띔", 14, 4, 12, 5);
    private final JLabel meaningLabel = label(" ", Theme.batangBold(24), Theme.INK);
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new SealButton("제출");
    private final JPanel seatRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 34, 0));
    /** 닉네임 → 학동 자리 */
    private final Map<String, Seat> seats = new LinkedHashMap<>();
    /** 팻말 둘째 줄의 안내 문구 (예: "난이도 하") */
    private String roundInfo = "";

    public GameRoomView() {
        setLayout(new BorderLayout());
        setBackground(Theme.PAPER_BG);
        setPreferredSize(new Dimension(Screen.WIDTH, Screen.HEIGHT));
        // 기둥(24) · 단청(18) · 마루(24) 안쪽에 내용
        setBorder(BorderFactory.createEmptyBorder(30, 48, 34, 48));

        JPanel content = transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 10, 0);
        content.add(top(), c);
        content.add(timer(), c);
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        content.add(hint(), c);
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        content.add(inputRow(), c);
        c.insets = new Insets(0, 0, 0, 0);
        seatRow.setOpaque(false);
        content.add(seatRow, c);
        add(content, BorderLayout.CENTER);

        hintSeal.setVisible(false); // showHint("")는 하위 클래스가 덮어쓸 수 있어 생성자에서 부르지 않는다
    }

    // ===================== 컨트롤러 · 다른 담당자가 쓰는 메서드 =====================

    /** 입장 순서대로 캐릭터를 배정해 학동 줄을 만든다 (최대 5명) */
    public void setPlayers(List<String> nicknames) {
        seatRow.removeAll();
        seats.clear();
        Avatar[] avatars = Avatar.values();
        for (int i = 0; i < nicknames.size() && i < avatars.length; i++) {
            Seat seat = new Seat(nicknames.get(i), avatars[i]);
            seats.put(nicknames.get(i), seat);
            seatRow.add(seat);
        }
        seatRow.revalidate();
        seatRow.repaint();
    }

    /** 로그인에서 고른 캐릭터로 바꾼다 */
    public void setAvatar(String nickname, Avatar avatar) {
        Seat seat = seats.get(nickname);
        if (seat != null) {
            seat.avatarLabel.setIcon(Theme.icon(avatar.imageName()));
        }
    }

    /** 명패에 "방장" 낙관 */
    public void setHost(String nickname) {
        seats.forEach((name, seat) -> seat.hostSeal.setVisible(name.equals(nickname)));
    }

    public void setScore(String nickname, int score) {
        Seat seat = seats.get(nickname);
        if (seat != null) {
            seat.scoreLabel.setText(score + "점");
        }
    }

    /** 팻말: "제 3 강" / "10강 중 · 문장 · 난이도 중" */
    public void setRound(int round, int totalRounds, String info) {
        roundInfo = info;
        roundLabel.setText("제 " + round + " 강");
        roundInfoLabel.setText(totalRounds + "강 중 · " + info);
    }

    /** 라운드만 바꾸고, 난이도 같은 안내 문구는 처음 setRound로 정한 것을 그대로 쓴다 */
    public void setRound(int round, int totalRounds) {
        setRound(round, totalRounds, roundInfo);
    }

    /** 라운드 종료: 두루마리에 정답을 청록색으로 띄우고 귀띔 자리에 뜻풀이를 보여준다. 다음 showQuestion에서 원래대로 돌아간다 */
    public void showRoundEnd(String answer, String meaning) {
        setScrollText(answer, Theme.JADE);
        showHint(meaning);
    }

    /** 새 문제 (초성이 섞인 문장). 힌트와 말풍선은 비운다 */
    public void showQuestion(String question) {
        setScrollText(question, Theme.INK);
        showHint("");
        for (Seat seat : seats.values()) {
            seat.setBubble("", null);
        }
    }

    /** 두루마리 글자. 높이가 고정이라 줄을 바꾸지 않고, 길면 한 줄에 들어가게 글씨를 줄인다 */
    private void setScrollText(String text, Color color) {
        Font font = Theme.hahmlet(QUESTION_FONT_SIZE);
        int width = questionLabel.getFontMetrics(font).stringWidth(text);
        if (width > QUESTION_MAX_WIDTH) {
            font = font.deriveFont(QUESTION_FONT_SIZE * QUESTION_MAX_WIDTH / width);
        }
        questionLabel.setFont(font);
        questionLabel.setForeground(color);
        questionLabel.setText(text);
    }

    /** 정답을 맞힌 학동이 깡총 뛴다 */
    public void jump(String nickname) {
        Seat seat = seats.get(nickname);
        if (seat != null) {
            seat.jump();
        }
    }

    /** 답안 입력창·제출 버튼 잠금/해제 (정답이 나온 뒤 ~ 다음 문제 전, 게임 종료 후에는 잠근다) */
    public void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        if (enabled) {
            inputField.requestFocusInWindow();
        }
    }

    /** 훈장님 귀띔. 빈 문자열이면 낙관과 뜻을 숨긴다. 길면 여러 줄로 나눈다 */
    public void showHint(String meaning) {
        boolean open = !meaning.isEmpty();
        hintSeal.setVisible(open);
        String safe = meaning.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        meaningLabel.setText(open
                ? "<html><div align='center' width='" + HINT_MAX_WIDTH + "'><b>" + safe + "</b></div></html>"
                : " ");
    }

    /** 해당 학동 위 말풍선에 입력 단어 + V(정답, 청록) / X(오답, 주홍) */
    public void showBubble(String nickname, String word, boolean correct) {
        Seat seat = seats.get(nickname);
        if (seat != null) {
            seat.setBubble(word + (correct ? " V" : " X"), correct ? Theme.JADE : Theme.SEAL);
        }
    }

    /** 향 타이머: 남은 시간만큼 향이 남고 "8초"로 표시 (타이머 로직은 송서영 담당) */
    public void setTimeLeft(int seconds, int totalSeconds) {
        incense.setRatio(totalSeconds <= 0 ? 0 : (double) seconds / totalSeconds);
        secondsLabel.setText(seconds + "초");
    }

    public JTextField getInputField() {
        return inputField;
    }

    public JButton getSendButton() {
        return sendButton;
    }

    // ===================== 화면 구성 =====================

    /** 강 팻말 + 문제 두루마리 */
    private JComponent top() {
        RoundBox plaque = new RoundBox(Theme.WOOD, Theme.WOOD_DARK, 2, 8);
        plaque.setLayout(new BoxLayout(plaque, BoxLayout.Y_AXIS));
        plaque.setPreferredSize(new Dimension(156, 92));
        plaque.add(Box.createVerticalGlue());
        plaque.add(centered(roundLabel));
        plaque.add(centered(roundInfoLabel));
        plaque.add(Box.createVerticalGlue());

        JPanel paper = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.PAPER);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.PAPER_LINE);
                g.fillRect(0, 0, getWidth(), 2);
                g.fillRect(0, getHeight() - 2, getWidth(), 2);
            }
        };
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(0, 0, 2, 0);
        paper.add(seal("문제", 12, 2, 8, 3), c);
        c.insets = new Insets(0, 0, 0, 0);
        paper.add(questionLabel, c);

        JPanel paperSlot = transparent(new BorderLayout());
        paperSlot.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        paperSlot.add(paper);

        JPanel scroll = transparent(new BorderLayout());
        scroll.setPreferredSize(new Dimension(0, 104));
        scroll.add(new ScrollAxis(), BorderLayout.WEST);
        scroll.add(paperSlot, BorderLayout.CENTER);
        scroll.add(new ScrollAxis(), BorderLayout.EAST);

        JPanel row = transparent(new BorderLayout(16, 0));
        JPanel plaqueSlot = transparent(new GridBagLayout()); // 팻말(92)을 두루마리(104) 높이 가운데에
        plaqueSlot.add(plaque);
        row.add(plaqueSlot, BorderLayout.WEST);
        row.add(scroll, BorderLayout.CENTER);
        return row;
    }

    /** 향로 + 타는 향 + 남은 시간 */
    private JComponent timer() {
        JLabel caption = label("남은 시간", Theme.batang(14), Theme.INK_SOFT);
        caption.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        caption.setAlignmentY(1f);
        secondsLabel.setAlignmentY(1f);

        JPanel seconds = transparent(null);
        seconds.setLayout(new BoxLayout(seconds, BoxLayout.X_AXIS));
        seconds.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        seconds.add(caption);
        seconds.add(Box.createHorizontalStrut(8));
        seconds.add(secondsLabel);

        JPanel row = transparent(new BorderLayout());
        row.setPreferredSize(new Dimension(0, 50));
        row.add(incense, BorderLayout.CENTER);
        row.add(seconds, BorderLayout.EAST);
        return row;
    }

    /** 훈장님 귀띔 (힌트) 상자 */
    private JComponent hint() {
        RoundBox box = new RoundBox(Theme.PAPER, Theme.PAPER_LINE, 2, 8);
        box.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(0, 0, 6, 0);
        box.add(hintSeal, c);
        box.add(meaningLabel, c);
        c.insets = new Insets(0, 0, 0, 0);
        box.add(label("가장 먼저 올린 한 명만 엽전을 얻습니다", Theme.batang(14), Theme.INK_SOFT), c);
        return box;
    }

    /** 붓 + 답안지 + 제출 */
    private JComponent inputRow() {
        inputField.setFont(Theme.input(19));
        inputField.setForeground(Theme.INK);
        inputField.setCaretColor(Theme.INK);
        inputField.setOpaque(false);
        inputField.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));

        RoundBox answerSheet = new RoundBox(Theme.PAPER, Theme.INK, 2, 8);
        answerSheet.setLayout(new BorderLayout());
        answerSheet.add(inputField);

        sendButton.setPreferredSize(new Dimension(110, 50));

        JPanel row = transparent(new BorderLayout(10, 0));
        row.setPreferredSize(new Dimension(0, 50));
        row.add(new JLabel(Theme.icon("brush")), BorderLayout.WEST);
        row.add(answerSheet, BorderLayout.CENTER);
        row.add(sendButton, BorderLayout.EAST);
        return row;
    }

    /** 기와 아래 단청 띠 · 양옆 기둥 · 바닥 마루 */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth();
        int h = getHeight();
        int floorY = h - 24;

        g2.setColor(Theme.FLOOR);
        g2.fillRect(0, floorY, w, 24);
        g2.setColor(Theme.FLOOR_LINE);
        for (int x = 150; x < w; x += 150) {
            g2.fillRect(x, floorY, 2, 24);
        }
        for (int x : new int[] {0, w - 24}) {
            g2.setColor(Theme.PILLAR);
            g2.fillRect(x, 18, 24, floorY - 18);
            g2.setColor(Theme.SEAL_LINE);
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(x + 1, 19, 22, floorY - 20);
            g2.setColor(Theme.JADE); // 주두
            g2.fillRect(x - 4, 18, 32, 10);
            g2.setColor(Theme.STONE); // 주춧돌
            g2.fillRoundRect(x - 6, floorY - 10, 36, 12, 6, 6);
        }
        g2.drawImage(DANCHEONG, 0, 0, w, 18, null);
        g2.dispose();
    }

    private static final Image DANCHEONG = Theme.image("dancheong");

    // ===================== 작은 부품 =====================

    /** 학동 한 명: 말풍선 · 캐릭터 · 방석 · 명패 · 점수 */
    private static final class Seat extends JPanel {

        /** 점프 높이(px). 캐릭터 위에 이만큼 자리를 비워 두고, 뛸 때 위·아래 여백을 바꿔 높이가 변하지 않게 한다 */
        private static final int JUMP_HEIGHT = 14;
        /** 점프 전체 시간(ms)과 횟수: 0.7초 동안 두 번 깡총 */
        private static final int JUMP_MILLIS = 700;
        private static final int JUMP_HOPS = 2;
        /** 애니메이션 한 프레임 간격(ms, 약 60fps) */
        private static final int FRAME_MILLIS = 16;

        private final RoundBox bubble = new RoundBox(Theme.PAPER, Theme.PAPER_LINE, 1.5f, 20);
        private final JLabel bubbleLabel = label("", Theme.batangBold(15), Theme.INK);
        private final JLabel avatarLabel;
        private final RoundBox hostSeal = seal("방장", 12, 2, 8, 3);
        private final JLabel scoreLabel = label("0점", Theme.batangBold(16), Theme.SCORE);

        Seat(String nickname, Avatar avatar) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            bubble.setLayout(new GridBagLayout());
            bubble.add(bubbleLabel);
            bubble.setPreferredSize(new Dimension(176, 34));
            bubble.setMaximumSize(new Dimension(176, 34));
            setBubble("", null);

            avatarLabel = new JLabel(Theme.icon(avatar.imageName()));
            setLift(0);

            RoundBox nameplate = new RoundBox(Theme.WOOD, Theme.WOOD_DARK, 2, 8);
            nameplate.setLayout(new FlowLayout(FlowLayout.CENTER, 6, 0));
            nameplate.setBorder(BorderFactory.createEmptyBorder(4, 6, 5, 6));
            hostSeal.setVisible(false);
            nameplate.add(hostSeal);
            nameplate.add(label(nickname, Theme.batangBold(17), Theme.INK));

            JPanel score = transparent(new FlowLayout(FlowLayout.CENTER, 5, 0));
            score.add(new JLabel(Theme.icon("coin")));
            score.add(scoreLabel);

            add(bubble);
            add(centered(avatarLabel)); // 점프 여백(JUMP_HEIGHT)이 말풍선과의 간격을 겸한다
            add(Box.createVerticalStrut(4));
            add(centered(new JLabel(Theme.icon("cushion"))));
            add(Box.createVerticalStrut(4));
            add(hug(nameplate));
            add(Box.createVerticalStrut(4));
            add(hug(score));
        }

        private Timer jumpTimer;

        /** |sin| 곡선으로 JUMP_HOPS번 뛰었다가 제자리로 돌아온다. 뛰는 중에 다시 부르면 처음부터 */
        void jump() {
            if (jumpTimer != null) {
                jumpTimer.stop();
            }
            long start = System.currentTimeMillis();
            Timer timer = new Timer(FRAME_MILLIS, null);
            timer.addActionListener(e -> {
                double t = Math.min(1.0, (System.currentTimeMillis() - start) / (double) JUMP_MILLIS);
                setLift((int) Math.round(JUMP_HEIGHT * Math.abs(Math.sin(Math.PI * JUMP_HOPS * t))));
                if (t >= 1.0) {
                    timer.stop();
                }
            });
            jumpTimer = timer;
            timer.start();
        }

        /** 캐릭터를 lift만큼 올린다. 위·아래 여백 합이 항상 JUMP_HEIGHT라 자리 크기는 그대로다 */
        private void setLift(int lift) {
            avatarLabel.setBorder(BorderFactory.createEmptyBorder(JUMP_HEIGHT - lift, 0, lift, 0));
        }

        /** 빈 말풍선은 자리만 차지하고 보이지 않는다 */
        void setBubble(String text, Color color) {
            bubbleLabel.setText(text);
            bubbleLabel.setForeground(color == null ? Theme.INK : color);
            bubble.setPainted(!text.isEmpty());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(176, super.getPreferredSize().height);
        }
    }

    /** 둥근 모서리 + 테두리 상자 (팻말 · 종이 · 낙관 · 말풍선) */
    private static class RoundBox extends JPanel {

        private final Color fill;
        private final Color line;
        private final float lineWidth;
        private final int arc;
        private boolean painted = true;

        RoundBox(Color fill, Color line, float lineWidth, int arc) {
            this.fill = fill;
            this.line = line;
            this.lineWidth = lineWidth;
            this.arc = arc;
            setOpaque(false);
        }

        void setPainted(boolean painted) {
            this.painted = painted;
            for (Component child : getComponents()) {
                child.setVisible(painted);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!painted) {
                return;
            }
            Graphics2D g2 = smooth(g);
            float half = lineWidth / 2;
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g2.setColor(line);
            g2.setStroke(new BasicStroke(lineWidth));
            g2.draw(new RoundRectangle2D.Float(half, half, getWidth() - lineWidth, getHeight() - lineWidth, arc, arc));
            g2.dispose();
        }
    }

    /** 붉은 낙관 (문제 · 훈장님 귀띔 · 방장) */
    private static RoundBox seal(String text, float size, int top, int side, int bottom) {
        RoundBox box = new RoundBox(Theme.SEAL, Theme.SEAL_LINE, 2, 10);
        box.setLayout(new BorderLayout());
        box.setBorder(BorderFactory.createEmptyBorder(top + 2, side + 2, bottom + 2, side + 2));
        box.add(label(text, Theme.batangBold(size), Theme.SEAL_TEXT));
        return box;
    }

    /** 붉은 제출 버튼 */
    private static final class SealButton extends JButton {

        SealButton(String text) {
            super(text);
            setFont(Theme.batangBold(19));
            setForeground(Theme.SEAL_TEXT);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(!isEnabled() ? Theme.PAPER_LINE : getModel().isPressed() ? Theme.SEAL_LINE : Theme.SEAL);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(Theme.SEAL_LINE);
            g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 두루마리 축 (위아래 금색 마구리) */
    private static final class ScrollAxis extends JComponent {

        private static final Icon CAP = Theme.icon("scroll_cap");

        ScrollAxis() {
            setPreferredSize(new Dimension(16, 104));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(Theme.WOOD_DARK);
            g2.fillRoundRect(0, 0, 16, getHeight(), 16, 16);
            g2.setColor(Theme.INK);
            g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect(1, 1, 14, getHeight() - 2, 14, 14);
            CAP.paintIcon(this, g2, 4, 7);
            CAP.paintIcon(this, g2, 4, getHeight() - 15);
            g2.dispose();
        }
    }

    /** 향로 + 향(남은 시간만큼) + 불씨 + 재 */
    private static final class IncenseTimer extends JComponent {

        private static final Icon BURNER = Theme.icon("incense_burner");
        private static final Icon EMBER = Theme.icon("ember");
        private double ratio = 1;

        void setRatio(double ratio) {
            this.ratio = Math.max(0, Math.min(1, ratio));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int h = getHeight();
            BURNER.paintIcon(this, g2, 0, h - BURNER.getIconHeight());

            int left = BURNER.getIconWidth();
            int barY = h - 22 - 9;
            int track = getWidth() - left - EMBER.getIconWidth();
            int stick = (int) Math.round(track * ratio);

            if (stick > 0) {
                g2.setColor(Theme.INCENSE);
                g2.fillRoundRect(left, barY, stick, 9, 8, 8);
                g2.setColor(Theme.INK);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(left, barY, stick - 1, 8, 8, 8);
            }
            int emberX = left + stick;
            g2.setColor(Theme.PAPER_LINE); // 재
            g2.fillRect(emberX + EMBER.getIconWidth(), barY + 3, getWidth() - emberX - EMBER.getIconWidth(), 2);
            EMBER.paintIcon(this, g2, emberX, h - 22 - EMBER.getIconHeight());
            g2.dispose();
        }
    }

    // ===================== 공통 =====================

    private static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    /** BoxLayout(Y) 안에서 가운데 정렬 (라벨용) */
    private static JComponent centered(JComponent c) {
        c.setAlignmentX(Component.CENTER_ALIGNMENT);
        return c;
    }

    /** BoxLayout(Y) 안에서 늘어나지 않게 감싸서 가운데 정렬 (명패 · 점수줄) */
    private static JComponent hug(JComponent c) {
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        wrap.setOpaque(false);
        wrap.add(c);
        return centered(wrap);
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }
}
