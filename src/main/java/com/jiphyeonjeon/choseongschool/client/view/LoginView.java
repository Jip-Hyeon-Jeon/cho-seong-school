package com.jiphyeonjeon.choseongschool.client.view;

import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.drawBox;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.drawCenter;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.drawImage;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.drawLeft;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.paintHanok;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.paintRod;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.setQuality;

import com.jiphyeonjeon.choseongschool.client.controller.LoginController;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/** 로그인 화면 (Figma "1 로그인 (한국풍)"): 입학 원서 | 초상 고르기 | 상소문 */
public class LoginView extends JPanel {

    private static final Font BATANG = Theme.batang(12);
    private static final Font BATANG_BOLD = Theme.batangBold(12);

    private static final String[] RULES = {
            "하나. 처음 들어온 이가 반장이 되어 난이도,총 문제 수, 풀이 시간을 정하옵니다.",
            "둘. 반장이 과거를 시작하옵니다. 혼자서도 할 수 있사옵니다.",
            "셋. 초성을 보고 정답을 가장 먼저 맞힌 한 명만 엽전을 얻사옵니다.",
            "넷. 빨리 맞힐수록 엽전이 많사옵니다.",
            "다섯. 향이 10초만큼 남으면 훈장님의 귀띔이 열리옵니다."
    };

    // Controller에서 접근할 UI 컴포넌트들
    private JTextField ipField;
    private JTextField nicknameField;
    private JButton enterButton;

    private JButton prevCharButton;
    private JButton nextCharButton;
    private JLabel charImageLabel;
    private JLabel charNameLabel;
    private JLabel charIndexLabel;
    private int characterIndex = 0; // 족자 비단 색을 정하는 캐릭터 번호

    public LoginView() {
        setLayout(null);
        setPreferredSize(new Dimension(Theme.SCREEN_WIDTH, Theme.SCREEN_HEIGHT));
        setBackground(Theme.BACKGROUND);

        createAdmissionForm();
        createCharacterSelector();
    }

    // 왼쪽: 입학 원서의 입력칸과 버튼
    private void createAdmissionForm() {
        place(label("서버 주소 (IP)", BATANG_BOLD, 14, Theme.TEXT_SUB, SwingConstants.LEFT), 74, 323, 268, 20);
        ipField = inputField("127.0.0.1");
        place(ipField, 74, 351, 268, 40);

        place(label("이름 (8자까지)", BATANG_BOLD, 14, Theme.TEXT_SUB, SwingConstants.LEFT), 74, 399, 268, 20);
        nicknameField = inputField("");
        place(nicknameField, 74, 427, 268, 40);

        enterButton = new RoundButton("서당 입학", Theme.batangBold(21), Theme.RED, Theme.RED_DARK,
                Theme.TEXT_LIGHT, 6);
        place(enterButton, 74, 605, 268, 52);
    }

    // 가운데: 초상과 화살표
    private void createCharacterSelector() {
        charImageLabel = new JLabel(Theme.portraitIcon(0, Theme.PORTRAIT_SIZE_LARGE));
        place(charImageLabel, 448, 368, Theme.PORTRAIT_SIZE_LARGE, Theme.PORTRAIT_SIZE_LARGE);

        charNameLabel = label("세종대왕", BATANG_BOLD, 34, Theme.TEXT, SwingConstants.CENTER);
        place(charNameLabel, 412, 526, 222, 46);

        prevCharButton = arrowButton("◀", "이전 초상");
        place(prevCharButton, 450, 581, 40, 40);
        charIndexLabel = label("1 / 5", BATANG_BOLD, 17, Theme.TEXT, SwingConstants.CENTER);
        place(charIndexLabel, 490, 581, 66, 40);
        nextCharButton = arrowButton("▶", "다음 초상");
        place(nextCharButton, 556, 581, 40, 40);
    }

    private void place(JComponent component, int x, int y, int width, int height) {
        component.setBounds(x, y, width, height);
        add(component);
    }

    private static JLabel label(String text, Font font, float size, Color color, int alignment) {
        JLabel label = new JLabel(text, alignment);
        label.setFont(font.deriveFont(size));
        label.setForeground(color);
        return label;
    }

    // 아래 줄만 있는 입력칸
    private static JTextField inputField(String text) {
        JTextField field = new JTextField(text);
        field.setOpaque(false);
        field.setFont(Theme.batang(18));
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.TEXT);
        field.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 2, 0, Theme.TEXT),
                new EmptyBorder(0, 4, 0, 4)
        ));
        return field;
    }

    private static JButton arrowButton(String text, String description) {
        JButton button = new RoundButton(text, Theme.symbolFont(14), Theme.PAPER, Theme.BROWN, Theme.BROWN, 20);
        button.setToolTipText(description);
        return button;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        setQuality(g2);
        paintHanok(g2);
        paintHeader(g2);
        paintAdmission(g2);
        paintPortraitScroll(g2);
        paintRules(g2);
        g2.dispose();
    }

    // 구름, 현판, 부제
    private void paintHeader(Graphics2D g2) {
        drawImage(g2, "/images/result/cloud.png", 150, 112, 134, 64);
        drawImage(g2, "/images/result/cloud.png", 920, 112, 134, 64);
        Composite original = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f)); // 작은 구름은 더 흐리게
        drawImage(g2, "/images/result/cloud.png", 250, 150, 94, 45);
        drawImage(g2, "/images/result/cloud.png", 850, 152, 94, 45);
        g2.setComposite(original);

        drawBox(g2, 400, 102, 400, 96, 6, 4, Theme.BROWN_DARK, Theme.GOLD);
        drawCenter(g2, "초성서당", BATANG_BOLD, 76, Theme.TEXT_LIGHT, 600, 150, 370);
        drawCenter(g2, "초성을 보고 가장 먼저 정답을 맞히는 문해력 겨루기", BATANG, 17, Theme.TEXT_SUB, 600, 220, 500);
    }

    // 왼쪽: 입학 원서 종이
    private void paintAdmission(Graphics2D g2) {
        drawBox(g2, 48, 243, 320, 463, 4, 2, Theme.PAPER, Theme.PAPER_BORDER);
        drawLeft(g2, "입학 원서", BATANG_BOLD, 40, Theme.TEXT, 74, 290, 215);
        paintSeal(g2, 298, 268, 44, 20, 18, "입학");
        drawLeft(g2, "같은 Wi-Fi에서 서버를 연 사람의 주소를 적습니다", BATANG, 13, Theme.TEXT_SUB, 74, 674, 272);
    }

    // 가운데: 초상 족자
    private void paintPortraitScroll(Graphics2D g2) {
        g2.setColor(Theme.moneyColor(characterIndex)); // 비단: 초상 인물의 지폐 색
        g2.fillRect(400, 257, 246, 435);
        g2.setColor(Theme.PAPER);
        g2.fillRect(412, 257, 222, 435);
        drawBox(g2, 388, 243, 270, 14, 7, 2, Theme.BROWN, Theme.TEXT);
        drawBox(g2, 388, 692, 270, 14, 7, 2, Theme.BROWN, Theme.TEXT);
        drawCenter(g2, "초상 고르기", BATANG_BOLD, 19, Theme.TEXT, 523, 344, 200);
    }

    // 오른쪽: 상소문 두루마리
    private void paintRules(Graphics2D g2) {
        g2.setColor(Theme.PAPER);
        g2.fillRect(694, 253, 442, 443);
        g2.setColor(Theme.PAPER_BORDER);
        g2.fillRect(694, 253, 442, 2);
        g2.fillRect(694, 694, 442, 2);
        paintRod(g2, 678, 243, 463);
        paintRod(g2, 1136, 243, 463);

        drawCenter(g2, "상소문", BATANG_BOLD, 46, Theme.TEXT, 915, 300, 394);
        g2.setColor(Theme.RED);
        g2.fillRect(718, 339, 394, 2);

        drawLeft(g2, "학동이여, 초성서당의 법도를 알려드리오.", BATANG_BOLD, 16, Theme.TEXT, 718, 362, 394);
        int y = 397;
        for (String rule : RULES) {
            y = drawWrapped(g2, rule, 718, y, 394);
        }

        drawLeft(g2, "이상 다섯 가지를 삼가 아뢰옵니다.", BATANG_BOLD, 15, Theme.TEXT, 718, 667, 320);
        paintSeal(g2, 1060, 626, 52, 19, 17, "초성", "서당");
    }

    // 낙관: 붉은 도장에 글자를 한 줄 또는 두 줄로
    private void paintSeal(Graphics2D g2, int x, int y, int size, int lineHeight, int fontSize, String... lines) {
        drawBox(g2, x, y, size, size, 5, 2, Theme.RED, Theme.RED_DARK);
        int centerY = y + size / 2 - lineHeight * (lines.length - 1) / 2;
        for (String line : lines) {
            drawCenter(g2, line, BATANG_BOLD, fontSize, Theme.TEXT_LIGHT, x + size / 2, centerY, size - 8);
            centerY += lineHeight;
        }
    }

    // 폭을 넘으면 띄어쓰기에서 줄을 바꿔 쓰고, 다음 줄의 y를 돌려준다
    private int drawWrapped(Graphics2D g2, String text, int x, int centerY, int width) {
        FontMetrics fm = g2.getFontMetrics(BATANG.deriveFont(15f));
        String line = "";
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(next) > width) {
                drawLeft(g2, line, BATANG, 15, Theme.TEXT, x, centerY, width);
                centerY += 26;
                next = word;
            }
            line = next;
        }
        drawLeft(g2, line, BATANG, 15, Theme.TEXT, x, centerY, width);
        return centerY + 26;
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
        charImageLabel.setIcon(Theme.portraitIcon(currentIndex, Theme.PORTRAIT_SIZE_LARGE));
        characterIndex = currentIndex;
        repaint();
        charIndexLabel.setText((currentIndex + 1) + " / " + totalCount);
    }

    // 단독 화면 테스트용 main (나중에 합칠 때는 MainFrame에서 LoginView만 가져다 씀)
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("초성서당 - 로그인 테스트");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            LoginView loginView = new LoginView();
            new LoginController(loginView);
            frame.add(loginView);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
