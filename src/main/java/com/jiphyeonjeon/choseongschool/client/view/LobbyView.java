package com.jiphyeonjeon.choseongschool.client.view;

import com.jiphyeonjeon.choseongschool.common.model.Difficulty;
import com.jiphyeonjeon.choseongschool.common.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.client.model.LobbyState;
import com.jiphyeonjeon.choseongschool.common.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.common.model.RoomState;

import java.awt.*;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;

/**
 * 대기실 화면 (Figma 76:779의 1200x740 배치).
 * 대기실 상태는 들고 있지 않고, 컨트롤러가 넘겨준 LobbyState를 render()로 그리기만 한다.
 * 버튼을 누르면 요청 콜백으로 알리고, 보낼 수 있는지는 컨트롤러가 판단한다.
 */
public class LobbyView extends JPanel {
    // 참가자 칸 안에서 배지·초상·이름 묶음의 세로 위치 기준
    private static final int PLAYER_GROUP_TOP = 393;
    private final JButton start = button("과거 시작");
    private final JButton exit = button("나가기");
    private final JLabel status = boldLabel("서버에 입장하는 중입니다.", 24);
    private final ButtonGroup levels = new ButtonGroup();
    private final ButtonGroup rounds = new ButtonGroup();
    private final ButtonGroup seconds = new ButtonGroup();
    private final BufferedImage[] portraits = new BufferedImage[5];
    /** 마지막으로 그린 상태 (그리기 전용, 이 화면이 바꾸지 않는다) */
    private LobbyState state = new LobbyState();
    /** 이름표를 다시 만들어야 하는지 비교하기 위한 마지막 참가자 목록 */
    private java.util.List<ParticipantInfo> renderedParticipants;
    private Runnable startRequest;
    private final CountdownOverlay networkCountdown = new CountdownOverlay();
    private java.util.function.Consumer<LobbySettings> settingsRequest;
    private final JLabel count = boldLabel("학동 0 / 5명", 20);
    private final java.util.List<JLabel> participantLabels = new java.util.ArrayList<>();
    private final java.util.List<JLabel> roleBadges = new java.util.ArrayList<>();
    private final JLabel guide = label("", 16);

    public LobbyView() {
        UIManager.put("ToggleButton.select", Theme.RED);
        setLayout(null);
        setPreferredSize(new Dimension(Theme.SCREEN_WIDTH, Theme.SCREEN_HEIGHT));
        setBackground(Theme.BACKGROUND);
        // 캐릭터 번호 = 파일 번호: 0 세종대왕, 1 퇴계이황, 2 율곡이이, 3 신사임당, 4 이순신
        for (int i = 0; i < portraits.length; i++) {
            try {
                var resource = LobbyView.class.getResource(Theme.portraitPath(i));
                if (resource == null) continue; // 파일이 없으면 "초상 준비 중"으로 표시
                portraits[i] = ImageIO.read(resource);
            } catch (IOException e) {
                throw new IllegalStateException("캐릭터 리소스를 읽지 못했습니다.", e);
            }
        }
        JLabel title = boldLabel("서당 마루", 34);
        title.setForeground(Theme.TEXT_LIGHT);
        place(title, 48, 102, 170, 56);
        place(count, 232, 112, 150, 36);
        place(exit, 1042, 108, 110, 44);
        JLabel rules = boldLabel("<html>학<br>칙</html>", 24);
        rules.setForeground(Theme.TEXT_LIGHT);
        place(rules, 50, 185, 42, 90);
        JLabel help = boldLabel("서당 학칙은 반장만 바꿀 수 있습니다", 18);
        help.setHorizontalAlignment(SwingConstants.LEFT);
        place(help, 110, 180, 600, 28);
        addOptions("난이도", levels, new String[]{"하", "중", "상"}, new String[]{"LOW", "MEDIUM", "HIGH"}, 0, 110);
        addOptions("총 문제 수", rounds, new String[]{"5", "10", "15", "20"}, new String[]{"5", "10", "15", "20"}, 1, 290);
        addOptions("풀이 시간", seconds, new String[]{"30초", "15초"}, new String[]{"30", "15"}, 0, 535);
        place(status, 100, 310, 1000, 40);
        start.setBackground(Theme.RED);
        start.setForeground(Theme.TEXT_LIGHT);
        start.setFont(Theme.batangBold(24));
        place(start, 485, 615, 230, 54);
        place(guide, 250, 677, 700, 25);
        {
            networkCountdown.setVisible(false);
            place(networkCountdown, 0, 0, Theme.SCREEN_WIDTH, Theme.SCREEN_HEIGHT);
            setComponentZOrder(networkCountdown, 0);
            start.addActionListener(e -> {
                if (startRequest != null) startRequest.run();
            });
            render(state);
        }
    }

    private int playerCenterX(int index) {
        int size = state.participants().size();
        int spacing = Math.min(200, 600 / Math.max(1, size));
        return 600 - (size - 1) * spacing / 2 + index * spacing;
    }

    private void rebuildParticipants() {
        java.util.List<ParticipantInfo> participants = state.participants();
        for (JLabel label : participantLabels) remove(label);
        participantLabels.clear();
        roleBadges.clear();
        count.setText("학동 " + participants.size() + " / 5명");
        int nameWidth = participants.size() >= 4 ? 110 : 130;
        for (int i = 0; i < participants.size(); i++) {
            int centerX = playerCenterX(i);
            JLabel name = new CenteredLabel(Theme.batangBold(18), Theme.TEXT);
            name.setText(participants.get(i).nickname());
            name.putClientProperty("html.disable", Boolean.TRUE);
            name.setOpaque(true);
            name.setBackground(Theme.BROWN_LIGHT);
            name.setBorder(new LineBorder(Theme.BROWN, 2));
            place(name, centerX - nameWidth / 2, PLAYER_GROUP_TOP + 141, nameWidth, 38);
            participantLabels.add(name);
            // 반장 여부와 본인 여부를 독립적으로 구분
            JLabel roleBadge = new CenteredLabel(Theme.batangBold(15), Theme.TEXT_LIGHT);
            roleBadges.add(roleBadge);
            participantLabels.add(roleBadge);
            roleBadge.setOpaque(true);
            roleBadge.setBackground(i == 0 ? Theme.RED : Theme.BROWN_DARK);
            roleBadge.setBorder(new LineBorder(Theme.PAPER_BORDER));
            place(roleBadge, centerX - 34, PLAYER_GROUP_TOP, 68, 26);
        }
        revalidate();
        setComponentZOrder(networkCountdown, 0);
        repaint();
    }

    private void addOptions(String title, ButtonGroup group, String[] labels, String[] values, int selected, int x) {
        JLabel heading = label(title, 16);
        heading.setHorizontalAlignment(SwingConstants.LEFT);
        place(heading, x, 213, 150, 26);
        for (int i = 0; i < labels.length; i++) {
            JToggleButton option = new JToggleButton(labels[i]);
            option.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
            option.setFont(Theme.batangBold(17));
            option.setActionCommand(values[i]);
            option.setFocusPainted(false);
            option.setMargin(new Insets(0, 0, 0, 0));
            option.setBorder(new LineBorder(Theme.BROWN));
            option.addItemListener(e -> styleOption(option));
            option.addActionListener(e -> requestSettings());
            group.add(option);
            option.setSelected(i == selected);
            styleOption(option);
            place(option, x + i * 54, 243, 48, 34);
        }
    }

    private void styleOption(JToggleButton option) {
        option.setBackground(option.isSelected() ? Theme.RED : Theme.PAPER);
        option.setForeground(option.isSelected() ? Theme.TEXT_LIGHT : Theme.TEXT);
    }

    private void place(JComponent component, int x, int y, int w, int h) {
        component.setBounds(x, y, w, h);
        add(component);
    }

    private static JLabel label(String text, int size) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(Theme.batang(size));
        label.setForeground(Theme.TEXT);
        return label;
    }

    private static JLabel boldLabel(String text, int size) {
        JLabel label = label(text, size);
        label.setFont(Theme.batangBold(size));
        return label;
    }

    /** 글자의 실제 모양을 기준으로 가운데에 그리는 이름표, 배지 (글꼴 위쪽 여백 때문에 글자가 아래로 처지지 않게) */
    private static class CenteredLabel extends JLabel {
        CenteredLabel(Font font, Color color) {
            setFont(font);
            setForeground(color);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            HanokPainter.setQuality(g);
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (!getText().isEmpty()) {
                Font font = getFont();
                var bounds = font.createGlyphVector(g.getFontRenderContext(), getText()).getVisualBounds();
                if (bounds.getWidth() > getWidth() - 12) { // 긴 닉네임은 칸에 맞게 줄인다
                    font = font.deriveFont((float) (font.getSize2D() * (getWidth() - 12) / bounds.getWidth()));
                    bounds = font.createGlyphVector(g.getFontRenderContext(), getText()).getVisualBounds();
                }
                g.setFont(font);
                g.setColor(getForeground());
                g.drawString(getText(), (float) ((getWidth() - bounds.getWidth()) / 2 - bounds.getX()),
                        (float) ((getHeight() - bounds.getHeight()) / 2 - bounds.getY()));
            }
            g.dispose();
        }
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.batangBold(18));
        button.setBackground(Theme.PAPER);
        button.setForeground(Theme.TEXT);
        button.setFocusPainted(false);
        button.setBorder(new LineBorder(Theme.BROWN, 1));
        return button;
    }

    public JButton getStartButton() { return start; }
    public JButton getExitButton() { return exit; }
    public int getSelectedRounds() { return Integer.parseInt(rounds.getSelection().getActionCommand()); }
    public int getSelectedSeconds() { return Integer.parseInt(seconds.getSelection().getActionCommand()); }
    public Difficulty getSelectedDifficulty() { return Difficulty.valueOf(levels.getSelection().getActionCommand()); }

    /** 학칙 버튼을 누르면 고른 학칙을 넘길 곳 (컨트롤러가 권한 확인 후 서버로 전송) */
    public void setSettingsRequest(java.util.function.Consumer<LobbySettings> request) {
        settingsRequest = request;
    }

    private void requestSettings() {
        LobbySettings proposed = new LobbySettings(getSelectedDifficulty(), getSelectedRounds(), getSelectedSeconds());
        // 서버가 확정하기 전까지는 기존 학칙을 표시
        selectSettings(state.settings());
        if (settingsRequest != null) settingsRequest.accept(proposed);
    }

    private void selectSettings(LobbySettings settings) {
        select(levels, settings.difficulty().name());
        select(rounds, Integer.toString(settings.rounds()));
        select(seconds, Integer.toString(settings.seconds()));
    }

    private static void select(ButtonGroup group, String command) {
        var buttons = group.getElements();
        while (buttons.hasMoreElements()) {
            AbstractButton button = buttons.nextElement();
            if (command.equals(button.getActionCommand())) button.setSelected(true);
        }
    }

    /** 시작 버튼을 누르면 알릴 곳 (컨트롤러가 권한 확인 후 서버로 전송) */
    public void setStartRequest(Runnable request) {
        startRequest = request;
    }

    public boolean isCountdownVisible() { return networkCountdown.isVisible(); }

    /** 대기실 상태를 화면에 그린다. 컨트롤러가 상태를 바꾼 뒤 Swing 스레드에서 호출한다 */
    public void render(LobbyState current) {
        state = current;
        if (current.participants() != renderedParticipants) {
            renderedParticipants = current.participants();
            rebuildParticipants();
        }
        selectSettings(current.settings());
        boolean host = current.isHost();
        RoomState phase = current.phase();
        boolean editable = current.canEditSettings() && settingsRequest != null;
        for (Component component : getComponents()) {
            if (component instanceof JToggleButton) component.setEnabled(editable);
        }
        start.setEnabled(current.canStart() && startRequest != null);
        start.setVisible(host && phase == RoomState.WAITING);
        exit.setEnabled(phase != RoomState.STARTING);
        boolean counting = phase == RoomState.STARTING && current.countdownNumber() > 0;
        if (counting) networkCountdown.showNumber(current.countdownNumber());
        networkCountdown.setVisible(counting);
        for (int i = 0; i < roleBadges.size(); i++) {
            boolean memberIsHost = current.participants().get(i).id().equals(current.hostId());
            roleBadges.get(i).setText(memberIsHost ? "반장" : "학동");
            roleBadges.get(i).setBackground(memberIsHost ? Theme.RED : Theme.BROWN_DARK);
        }
        status.setText(statusText(current));
        guide.setText(guideText(current));
        setComponentZOrder(networkCountdown, 0);
        revalidate();
        repaint();
    }

    private static String statusText(LobbyState s) {
        if (s.notice() != null) return s.notice();
        return switch (s.phase()) {
            case STARTING -> "잠시 후 과거를 시작합니다.";
            case PLAYING -> "과거가 시작되었습니다.";
            case WAITING -> s.participants().isEmpty() ? "서버에 입장하는 중입니다."
                    : s.isHost() ? "학동 " + s.participants().size() + "명이 모였습니다. 반장이 과거를 시작합니다."
                    : "반장이 과거를 시작하기를 기다리는 중입니다.";
        };
    }

    private static String guideText(LobbyState s) {
        if (!s.isConnected()) return "창을 닫고 접속 정보를 확인한 뒤 다시 실행해 주세요.";
        if (s.phase() != RoomState.WAITING) return "";
        return s.isSettingsPending() ? "학칙 변경을 확인하고 있습니다." : "반장이 시작하면 셋을 센 뒤 과거가 시작됩니다.";
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        HanokPainter.paintHanok(g); // 기와, 단청, 기둥, 마루 (로그인, 결과와 같은 그림)
        HanokPainter.drawBox(g, 48, 102, 170, 56, 6, 4, Theme.BROWN_DARK, Theme.GOLD);
        g.setColor(Theme.PAPER); g.fillRoundRect(50,170,1100,118,4,4);
        g.setColor(Theme.PAPER_BORDER); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(50,170,1100,118,4,4);
        g.setColor(Theme.RED); g.fillRect(50,185,42,90);
        g.setColor(Theme.MAT); g.fillRoundRect(275,365,650,235,5,5);
        g.setColor(Theme.BROWN_LIGHT); g.drawRoundRect(275,365,650,235,5,5);
        java.util.List<ParticipantInfo> participants = state.participants();
        for (int i=0;i<participants.size();i++) {
            int centerX = playerCenterX(i);
            BufferedImage portrait = portraits[participants.get(i).character()];
            if (portrait != null) {
                g.drawImage(portrait,centerX-50,PLAYER_GROUP_TOP+28,100,100,null);
            } else {
                g.setColor(Theme.PAPER); g.fillOval(centerX-50,PLAYER_GROUP_TOP+28,100,100);
                g.setColor(Theme.TEXT); g.setFont(Theme.batang(14));
                g.drawString("초상 준비 중", centerX-38, PLAYER_GROUP_TOP+83);
            }
            if (participants.get(i).id().equals(state.localId())) {
                g.setColor(Theme.SHADOW);
                g.fillOval(centerX-65,PLAYER_GROUP_TOP+125,130,14);
                g.setColor(Theme.SHADOW_DARK);
                g.fillOval(centerX-53,PLAYER_GROUP_TOP+128,106,8);
            }
        }
        g.dispose();
    }
}
