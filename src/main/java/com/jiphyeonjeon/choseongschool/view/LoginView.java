package com.jiphyeonjeon.choseongschool.view;

import com.jiphyeonjeon.choseongschool.controller.LoginController;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class LoginView extends JPanel {

    // 색상 팔레트 (한지 & 전통 한국풍 테마)
    private static final Color BG_HANJI = new Color(247, 241, 227);      // 전체 한지 배경색
    private static final Color CARD_BG = new Color(253, 250, 242);       // 카드 내부 밝은 한지색
    private static final Color SCROLL_BG = new Color(238, 226, 200);     // 족자/상소문 배경색
    private static final Color BROWN_DARK = new Color(78, 52, 46);       // 타이틀 배너 짙은 갈색
    private static final Color RED_STAMP = new Color(169, 50, 38);       // 입학 버튼/낙관 붉은색
    private static final Color BORDER_GOLD = new Color(185, 160, 120);   // 테두리 선 색

    // Controller에서 접근할 UI 컴포넌트들
    private JTextField ipField;
    private JTextField nicknameField;
    private JButton enterButton;

    private JButton prevCharButton;
    private JButton nextCharButton;
    private JLabel charImageLabel;
    private JLabel charNameLabel;
    private JLabel charIndexLabel;

    public LoginView() {
        setLayout(new BorderLayout(20, 20));
        setBackground(BG_HANJI);
        setBorder(new EmptyBorder(30, 40, 40, 40));

        // 1. 상단 타이틀 영역
        add(createHeaderPanel(), BorderLayout.NORTH);

        // 2. 중앙 3단 메인 영역 (입학 원서 | 초상 고르기 | 상소문)
        JPanel centerPanel = new JPanel(new GridLayout(1, 3, 25, 0));
        centerPanel.setOpaque(false);

        centerPanel.add(createAdmissionPanel());
        centerPanel.add(createCharacterPanel());
        centerPanel.add(createRuleScrollPanel());

        add(centerPanel, BorderLayout.CENTER);
    }

    // 상단 타이틀 패널
    private JPanel createHeaderPanel() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel titleLabel = new JLabel("초성서당", SwingConstants.CENTER);
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 36));
        titleLabel.setForeground(new Color(255, 248, 231));
        titleLabel.setOpaque(true);
        titleLabel.setBackground(BROWN_DARK);
        titleLabel.setBorder(new CompoundBorder(
                new LineBorder(BORDER_GOLD, 3),
                new EmptyBorder(10, 50, 10, 50)
        ));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subTitleLabel = new JLabel("초성을 보고 가장 먼저 정답을 맞히는 문해력 겨루기");
        subTitleLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        subTitleLabel.setForeground(BROWN_DARK);
        subTitleLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
        subTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(titleLabel);
        header.add(subTitleLabel);
        return header;
    }

    // 왼쪽: 입학 원서 패널
    private JPanel createAdmissionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 15));
        panel.setBackground(CARD_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(BORDER_GOLD, 2),
                new EmptyBorder(25, 25, 25, 25)
        ));

        JLabel sectionTitle = new JLabel("입학 원서");
        sectionTitle.setFont(new Font("맑은 고딕", Font.BOLD, 24));
        sectionTitle.setForeground(BROWN_DARK);
        panel.add(sectionTitle, BorderLayout.NORTH);

        // 입력 폼
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setOpaque(false);

        JLabel ipLabel = new JLabel("서버 주소 (IP)");
        ipLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        ipField = new JTextField("127.0.0.1");
        ipField.setFont(new Font("맑은 고딕", Font.PLAIN, 16));
        ipField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel nameLabel = new JLabel("이름 (8자까지)");
        nameLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        nicknameField = new JTextField();
        nicknameField.setFont(new Font("맑은 고딕", Font.PLAIN, 16));
        nicknameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        formPanel.add(Box.createVerticalStrut(15));
        formPanel.add(ipLabel);
        formPanel.add(Box.createVerticalStrut(5));
        formPanel.add(ipField);
        formPanel.add(Box.createVerticalStrut(20));
        formPanel.add(nameLabel);
        formPanel.add(Box.createVerticalStrut(5));
        formPanel.add(nicknameField);

        panel.add(formPanel, BorderLayout.CENTER);

        // 하단 입장 버튼
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.setOpaque(false);

        enterButton = new JButton("서당 입학");
        enterButton.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        enterButton.setBackground(RED_STAMP);
        enterButton.setForeground(Color.WHITE);
        enterButton.setFocusPainted(false);
        enterButton.setPreferredSize(new Dimension(0, 48));

        JLabel guideLabel = new JLabel("같은 Wi-Fi에서 서버를 연 사람의 주소를 적습니다", SwingConstants.CENTER);
        guideLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        guideLabel.setForeground(Color.GRAY);

        bottomPanel.add(enterButton, BorderLayout.CENTER);
        bottomPanel.add(guideLabel, BorderLayout.SOUTH);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    // 가운데: 초상 고르기(캐릭터 선택) 패널
    private JPanel createCharacterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 15));
        panel.setBackground(SCROLL_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(BROWN_DARK, 3),
                new EmptyBorder(25, 20, 25, 20)
        ));

        JLabel sectionTitle = new JLabel("초상 고르기", SwingConstants.CENTER);
        sectionTitle.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        sectionTitle.setForeground(BROWN_DARK);
        panel.add(sectionTitle, BorderLayout.NORTH);

        // 캐릭터 이미지 자리 (이미지 파일 넣기 전 임시 플레이스홀더)
        charImageLabel = new JLabel("캐릭터 초상화", SwingConstants.CENTER);
        charImageLabel.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        charImageLabel.setOpaque(true);
        charImageLabel.setBackground(CARD_BG);
        charImageLabel.setBorder(new LineBorder(BORDER_GOLD, 2));
        panel.add(charImageLabel, BorderLayout.CENTER);

        // 하단 이름 및 화살표 컨트롤
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setOpaque(false);

        charNameLabel = new JLabel("세종대왕", SwingConstants.CENTER);
        charNameLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        charNameLabel.setForeground(BROWN_DARK);
        charNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        navPanel.setOpaque(false);
        prevCharButton = new JButton("◀");
        charIndexLabel = new JLabel("1 / 5");
        charIndexLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        nextCharButton = new JButton("▶");

        navPanel.add(prevCharButton);
        navPanel.add(charIndexLabel);
        navPanel.add(nextCharButton);

        controlPanel.add(charNameLabel);
        controlPanel.add(navPanel);
        panel.add(controlPanel, BorderLayout.SOUTH);

        return panel;
    }

    // 오른쪽: 상소문(규칙 안내) 패널
    private JPanel createRuleScrollPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 15));
        panel.setBackground(CARD_BG);
        panel.setBorder(new CompoundBorder(
                new LineBorder(BORDER_GOLD, 2),
                new EmptyBorder(25, 25, 25, 25)
        ));

        JLabel sectionTitle = new JLabel("상소문", SwingConstants.CENTER);
        sectionTitle.setFont(new Font("맑은 고딕", Font.BOLD, 24));
        sectionTitle.setForeground(BROWN_DARK);
        panel.add(sectionTitle, BorderLayout.NORTH);

        JTextArea ruleArea = new JTextArea(
                "전하, 초성서당의 법도를 아뢰옵니다.\n\n" +
                        "하나, 처음 들어온 이가 반장이 되어 난이도, 문제 수, 풀이 시간을 정하옵니다.\n\n" +
                        "둘, 반장이 과거를 시작하옵니다. 혼자서도 할 수 있사옵니다.\n\n" +
                        "셋, 초성을 보고 정답을 가장 먼저 올린 한 명만 엽전을 얻사옵니다.\n\n" +
                        "넷, 풀이 시간의 절반이 지나면 훈장님의 귀띔(힌트)이 열리옵니다.\n\n" +
                        "이상 네 가지를 삼가 아뢰옵니다."
        );
        ruleArea.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        ruleArea.setForeground(BROWN_DARK);
        ruleArea.setBackground(CARD_BG);
        ruleArea.setLineWrap(true);
        ruleArea.setWrapStyleWord(true);
        ruleArea.setEditable(false);

        panel.add(ruleArea, BorderLayout.CENTER);
        return panel;
    }

    // --- Controller에서 사용할 Getter 메서드들 ---
    public String getServerIp() {
        return ipField.getText().trim();
    }

    public String getNickname() {
        return nicknameField.getText().trim();
    }

    public JButton getEnterButton() {
        return enterButton;
    }

    public JButton getPrevCharButton() {
        return prevCharButton;
    }

    public JButton getNextCharButton() {
        return nextCharButton;
    }

    public void updateCharacterDisplay(String name, int currentIndex, int totalCount) {
        charNameLabel.setText(name);
        charIndexLabel.setText((currentIndex + 1) + " / " + totalCount);
    }

    // 단독 화면 테스트용 main (나중에 합칠 때는 MainFrame에서 LoginView만 가져다 씀)
    public static void main(String[] args) {


        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("초성서당 - 로그인 테스트");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1000, 620);
            frame.setLocationRelativeTo(null);

            LoginView loginView = new LoginView();
            new LoginController(loginView);
            frame.add(loginView);
            frame.setVisible(true);
        });
    }
}