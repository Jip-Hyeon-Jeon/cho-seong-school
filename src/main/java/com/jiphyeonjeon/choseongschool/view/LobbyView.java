package com.jiphyeonjeon.choseongschool.view;

import com.jiphyeonjeon.choseongschool.model.Difficulty;
import com.jiphyeonjeon.choseongschool.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.model.RoomState;

import java.awt.*;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;

// Figma 76:779의 1200x740 대기실 배치
public class LobbyView extends JPanel {
    static final int WIDTH = 1200;
    static final int HEIGHT = 740;
    static final Color PAPER = new Color(0xF4EBD9);
    static final Color CARD = new Color(253, 250, 242);
    static final Color INK = new Color(78, 52, 46);
    static final Color RED = new Color(169, 50, 38);
    static final Color GOLD = new Color(185, 160, 120);
    // 참가자 칸 안에서 배지·초상·이름 묶음의 세로 위치 기준
    private static final int PLAYER_GROUP_TOP = 393;
    private final JButton start = button("과거 시작");
    private final JButton exit = button("나가기");
    private final JLabel status = label("서버에 입장하는 중입니다.", 21);
    private final ButtonGroup levels = new ButtonGroup();
    private final ButtonGroup rounds = new ButtonGroup();
    private final ButtonGroup seconds = new ButtonGroup();
    private final BufferedImage[] portraits = new BufferedImage[5];
    private java.util.List<ParticipantInfo> participants = java.util.List.of();
    private String localPlayerId;
    private String hostId;
    private boolean connected = true;
    private LobbySettings confirmedSettings = LobbySettings.DEFAULT;
    private boolean settingsReceived;
    private boolean settingsPending;
    private boolean startPending;
    private Runnable startRequest;
    private final CountdownOverlay networkCountdown = new CountdownOverlay();
    private int countdownNumber;
    private java.util.function.Consumer<LobbySettings> settingsRequest;
    private final JLabel count = label("학동 0 / 5명", 18);
    private final java.util.List<JLabel> participantLabels = new java.util.ArrayList<>();
    private RoomState viewState = RoomState.WAITING;
    private final java.util.List<JLabel> roleBadges = new java.util.ArrayList<>();
    private final JLabel guide = label("", 13);

    public LobbyView() {
        UIManager.put("ToggleButton.select", RED);
        setLayout(null);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(PAPER);
        // 캐릭터 번호 = 파일 번호: 0 세종대왕, 1 퇴계이황, 2 율곡이이, 3 신사임당, 4 이순신
        for (int i = 0; i < portraits.length; i++) {
            try {
                var resource = LobbyView.class.getResource("/lobby/portraits/ch" + i + ".png");
                if (resource == null) continue; // 파일이 없으면 "초상 준비 중"으로 표시
                portraits[i] = ImageIO.read(resource);
            } catch (IOException e) {
                throw new IllegalStateException("캐릭터 리소스를 읽지 못했습니다.", e);
            }
        }
        JLabel title = label("서당 마루", 28);
        title.setForeground(CARD);
        place(title, 50, 100, 165, 55);
        place(count, 230, 110, 140, 35);
        place(exit, 1040, 108, 108, 42);
        JLabel rules = label("<html>학<br>칙</html>", 23);
        rules.setForeground(CARD);
        place(rules, 50, 185, 42, 90);
        JLabel help = label("서당 학칙은 반장만 바꿀 수 있습니다", 17);
        help.setHorizontalAlignment(SwingConstants.LEFT);
        place(help, 110, 180, 600, 28);
        addOptions("난이도", levels, new String[]{"하", "중", "상"}, new String[]{"LOW", "MEDIUM", "HIGH"}, 0, 110);
        addOptions("총 문제 수", rounds, new String[]{"5", "10", "15", "20"}, new String[]{"5", "10", "15", "20"}, 1, 290);
        addOptions("풀이 시간", seconds, new String[]{"30초", "15초"}, new String[]{"30", "15"}, 0, 535);
        place(status, 100, 310, 1000, 40);
        start.setBackground(RED);
        start.setForeground(Color.WHITE);
        start.setFont(new Font("맑은 고딕", Font.BOLD, 23));
        place(start, 485, 615, 230, 54);
        place(guide, 250, 677, 700, 25);
        {
            networkCountdown.setVisible(false);
            place(networkCountdown, 0, 0, WIDTH, HEIGHT);
            setComponentZOrder(networkCountdown, 0);
            start.addActionListener(e -> {
                if (startRequest == null || startPending) return;
                startPending = true;
                refreshPermissions();
                startRequest.run();
            });
            refreshPermissions();
        }
    }

    public void showRoster(String localId, String newHostId, java.util.List<ParticipantInfo> members) {
        if (members.isEmpty() || members.size() > 5
                || members.stream().noneMatch(p -> p.id().equals(localId))
                || members.stream().noneMatch(p -> p.id().equals(newHostId))
                || members.stream().map(ParticipantInfo::id).distinct().count() != members.size()) {
            throw new IllegalArgumentException("참가자 목록에 본인 또는 반장이 없거나 ID가 중복됩니다.");
        }
        participants = java.util.List.copyOf(members);
        localPlayerId = localId;
        hostId = newHostId;
        connected = true;
        rebuildParticipants();
        refreshPermissions();
    }

    private int playerCenterX(int index) {
        int spacing = Math.min(200, 600 / Math.max(1, participants.size()));
        return 600 - (participants.size() - 1) * spacing / 2 + index * spacing;
    }

    private void rebuildParticipants() {
        for (JLabel label : participantLabels) remove(label);
        participantLabels.clear();
        roleBadges.clear();
        count.setText("학동 " + participants.size() + " / 5명");
        int nameWidth = participants.size() >= 4 ? 110 : 130;
        for (int i = 0; i < participants.size(); i++) {
            int centerX = playerCenterX(i);
            JLabel name = label(participants.get(i).nickname(), 18);
            name.putClientProperty("html.disable", Boolean.TRUE);
            name.setOpaque(true);
            name.setBackground(new Color(214, 174, 111));
            name.setBorder(new LineBorder(new Color(137, 91, 53), 2));
            place(name, centerX - nameWidth / 2, PLAYER_GROUP_TOP + 141, nameWidth, 38);
            participantLabels.add(name);
            // 반장 여부와 본인 여부를 독립적으로 구분
            JLabel roleBadge = label("", 14);
            roleBadges.add(roleBadge);
            participantLabels.add(roleBadge);
            roleBadge.setOpaque(true);
            roleBadge.setBackground(i == 0 ? RED : INK);
            roleBadge.setForeground(CARD);
            roleBadge.setBorder(new LineBorder(GOLD));
            place(roleBadge, centerX - 34, PLAYER_GROUP_TOP, 68, 26);
        }
        revalidate();
        setComponentZOrder(networkCountdown, 0);
        repaint();
    }

    private void addOptions(String title, ButtonGroup group, String[] labels, String[] values, int selected, int x) {
        JLabel heading = label(title, 14);
        heading.setHorizontalAlignment(SwingConstants.LEFT);
        place(heading, x, 215, 150, 25);
        for (int i = 0; i < labels.length; i++) {
            JToggleButton option = new JToggleButton(labels[i]);
            option.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
            option.setFont(new Font("맑은 고딕", Font.PLAIN, 16));
            option.setActionCommand(values[i]);
            option.setFocusPainted(false);
            option.setMargin(new Insets(0, 0, 0, 0));
            option.setBorder(new LineBorder(INK));
            option.addItemListener(e -> styleOption(option));
            option.addActionListener(e -> requestSettings());
            group.add(option);
            option.setSelected(i == selected);
            styleOption(option);
            place(option, x + i * 54, 243, 48, 34);
        }
    }

    private void styleOption(JToggleButton option) {
        option.setBackground(option.isSelected() ? RED : CARD);
        option.setForeground(option.isSelected() ? Color.WHITE : INK);
    }

    private void place(JComponent component, int x, int y, int w, int h) {
        component.setBounds(x, y, w, h);
        add(component);
    }

    private static JLabel label(String text, int size) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("맑은 고딕", Font.PLAIN, size));
        label.setForeground(INK);
        return label;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("맑은 고딕", Font.PLAIN, 17));
        button.setBackground(CARD);
        button.setForeground(INK);
        button.setFocusPainted(false);
        button.setBorder(new LineBorder(INK, 1));
        return button;
    }

    public JButton getStartButton() { return start; }
    public JButton getExitButton() { return exit; }
    public int getSelectedRounds() { return Integer.parseInt(rounds.getSelection().getActionCommand()); }
    public int getSelectedSeconds() { return Integer.parseInt(seconds.getSelection().getActionCommand()); }
    public Difficulty getSelectedDifficulty() { return Difficulty.valueOf(levels.getSelection().getActionCommand()); }

    public void setSettingsRequest(java.util.function.Consumer<LobbySettings> request) {
        settingsRequest = request;
        refreshPermissions();
    }

    private void requestSettings() {
        if (settingsRequest == null || settingsPending) return;
        LobbySettings proposed = new LobbySettings(getSelectedDifficulty(), getSelectedRounds(), getSelectedSeconds());
        // 서버 승인 전까지 기존 선택 유지
        selectSettings(confirmedSettings);
        settingsPending = true;
        refreshPermissions();
        settingsRequest.accept(proposed);
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

    public void showSettings(LobbySettings settings) {
        confirmedSettings = settings;
        settingsReceived = true;
        settingsPending = false;
        startPending = false;
        selectSettings(settings);
        refreshPermissions();
    }

    public void showCommandError(String message) { status.setText(message); }

    public void setStartRequest(Runnable request) {
        startRequest = request;
        refreshPermissions();
    }

    public RoomState getViewState() { return viewState; }
    public int getCountdownNumber() { return countdownNumber; }
    public boolean isCountdownVisible() { return networkCountdown.isVisible(); }

    public void showCountdown(int number) {
        if (number < 1 || number > 3) throw new IllegalArgumentException("카운트다운은 1~3입니다.");
        showStarting();
        countdownNumber = number;
        networkCountdown.showNumber(number);
        networkCountdown.setVisible(true);
        networkCountdown.revalidate();
        repaint();
    }

    public void showStartCancelled() {
        networkCountdown.setVisible(false);
        countdownNumber = 0;
        startPending = false;
        viewState = RoomState.WAITING;
        exit.setEnabled(true);
        refreshPermissions();
        status.setText("학동이 나가 시작을 취소했습니다. 다시 시작할 수 있습니다.");
    }

    private void refreshPermissions() {
        boolean isHost = connected && localPlayerId != null && localPlayerId.equals(hostId);
        boolean canEdit = isHost && viewState == RoomState.WAITING
                && settingsReceived && settingsRequest != null && !settingsPending && !startPending;
        for (Component component : getComponents()) {
            if (component instanceof JToggleButton) component.setEnabled(canEdit);
        }
        start.setEnabled(canEdit && startRequest != null);
        start.setVisible(isHost && viewState == RoomState.WAITING);
        if (viewState == RoomState.WAITING) {
            status.setText(isHost ? "학동 " + participants.size() + "명이 모였습니다. 반장이 과거를 시작합니다."
                    : "반장이 과거를 시작하기를 기다리는 중입니다.");
            guide.setText(isHost ? "혼자서도 시작할 수 있습니다. 시작하면 버튼이 사라집니다."
                    : "학칙은 보기만 가능합니다. 캐릭터 아래 그림자가 있는 학동이 나입니다.");
        }
        if (viewState == RoomState.WAITING) {
            guide.setText(settingsPending ? "학칙 변경을 확인하고 있습니다."
                    : "반장이 시작하면 셋을 센 뒤 과거가 시작됩니다.");
            if (participants.isEmpty()) status.setText("서버에 입장하는 중입니다.");
        }
        for (int i = 0; i < roleBadges.size(); i++) {
            boolean memberIsHost = participants.get(i).id().equals(hostId);
            roleBadges.get(i).setText(memberIsHost ? "반장" : "학동");
            roleBadges.get(i).setBackground(memberIsHost ? RED : INK);
        }
        repaint();
    }

    public java.util.List<ParticipantInfo> getParticipants() { return participants; }
    public String getLocalPlayerId() { return localPlayerId; }
    public String getHostId() { return hostId; }

    public void showConnectionClosed(String message) {
        connected = false;
        networkCountdown.setVisible(false);
        countdownNumber = 0;
        viewState = RoomState.WAITING;
        startPending = false;
        exit.setEnabled(true);
        settingsReceived = false;
        settingsPending = false;
        participants = java.util.List.of();
        localPlayerId = null;
        hostId = null;
        rebuildParticipants();
        refreshPermissions();
        status.setText(message);
        guide.setText("창을 닫고 접속 정보를 확인한 뒤 다시 실행해 주세요.");
    }

    public void showStarting() {
        startPending = false;
        viewState = RoomState.STARTING;
        refreshPermissions();
        status.setText("잠시 후 과거를 시작합니다.");
        guide.setText("");
        for (Component component : getComponents()) {
            if (component instanceof AbstractButton) component.setEnabled(false);
        }
        start.setVisible(false);
    }

    public void showPlaying() {
        networkCountdown.setVisible(false);
        countdownNumber = 0;
        viewState = RoomState.PLAYING;
        refreshPermissions();
        status.setText("과거가 시작되었습니다.");
        exit.setEnabled(true);
    }

    /** 게임이 끝나 대기 상태로 복귀: 반장은 학칙을 바꾸고 다시 시작할 수 있다 */
    public void showGameEnded() {
        networkCountdown.setVisible(false);
        countdownNumber = 0;
        startPending = false;
        viewState = RoomState.WAITING;
        exit.setEnabled(true);
        refreshPermissions();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 시안을 참고해 Java2D로 재현한 기와와 단청
        g.setColor(new Color(49, 51, 60)); g.fillRect(0, 0, WIDTH, 65);
        for (int x = 0; x < WIDTH; x += 28) {
            g.setColor(new Color(78, 79, 87)); g.fillRect(x + 4, 12, 13, 44);
            g.setColor(new Color(142, 142, 146)); g.fillOval(x + 2, 48, 18, 16);
            g.setColor(new Color(47, 48, 53)); g.fillOval(x + 7, 53, 8, 7);
        }
        g.setColor(new Color(46, 104, 86)); g.fillRect(0, 65, WIDTH, 29);
        g.setColor(GOLD); g.fillRect(0, 65, WIDTH, 3); g.fillRect(0, 89, WIDTH, 3);
        for (int x = 15; x < WIDTH; x += 60) {
            g.setColor(RED); g.fillOval(x, 73, 10, 10);
            g.setColor(CARD); g.drawOval(x, 73, 10, 10);
            g.setColor(new Color(214, 172, 65));
            g.fillPolygon(new int[]{x+28,x+34,x+28,x+22}, new int[]{72,78,84,78},4);
        }
        g.setColor(RED); g.fillRect(0, 94, 19, 626); g.fillRect(1181, 94, 19, 626);
        g.setColor(new Color(137, 93, 54)); g.fillRect(0, 719, WIDTH, 21);
        g.setColor(INK); for(int x=100;x<WIDTH;x+=150) g.drawLine(x,720,x,740);
        g.setColor(INK); g.fillRoundRect(50,100,165,55,4,4);
        g.setColor(GOLD); g.setStroke(new BasicStroke(3)); g.drawRoundRect(50,100,165,55,4,4);
        g.setColor(CARD); g.fillRoundRect(50,170,1100,118,4,4);
        g.setColor(GOLD); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(50,170,1100,118,4,4);
        g.setColor(RED); g.fillRect(50,185,42,90);
        g.setColor(new Color(231, 212, 162)); g.fillRoundRect(275,365,650,235,5,5);
        g.setColor(GOLD); g.drawRoundRect(275,365,650,235,5,5);
        for (int i=0;i<participants.size();i++) {
            int centerX = playerCenterX(i);
            BufferedImage portrait = portraits[participants.get(i).character()];
            if (portrait != null) {
                g.drawImage(portrait,centerX-50,PLAYER_GROUP_TOP+28,100,100,null);
            } else {
                g.setColor(CARD); g.fillOval(centerX-50,PLAYER_GROUP_TOP+28,100,100);
                g.setColor(INK); g.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
                g.drawString("초상 준비 중", centerX-38, PLAYER_GROUP_TOP+83);
            }
            if (participants.get(i).id().equals(localPlayerId)) {
                g.setColor(new Color(78,52,46,45));
                g.fillOval(centerX-65,PLAYER_GROUP_TOP+125,130,14);
                g.setColor(new Color(78,52,46,75));
                g.fillOval(centerX-53,PLAYER_GROUP_TOP+128,106,8);
            }
        }
        g.dispose();
    }
}
