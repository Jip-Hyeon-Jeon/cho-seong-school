package com.jiphyeonjeon.choseongschool.client.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BaseMultiResolutionImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import javax.swing.Icon;

/**
 * [클라이언트] 모든 화면이 함께 쓰는 화면 크기, 색, 폰트, 이미지.
 * Figma "초성서당 화면 설계 (한국풍 고급 시안)"을 기준으로 한다.
 *
 * [상수 추가 규칙]
 * - 화면 코드에 색, 폰트, 화면 크기를 직접 쓰지 말고 여기에 추가한 뒤 사용한다.
 * - 여러 곳에 쓰는 색은 색 이름(RED, BROWN), 글자색은 TEXT_, 한 곳에만 쓰는 색은 사물 이름(PILLAR)으로 짓는다.
 */
public final class Theme {

    private Theme() {
    }

    // ---- 크기 (px) ----
    /** 화면 가로 크기 */
    public static final int SCREEN_WIDTH = 1200;
    /** 화면 세로 크기 */
    public static final int SCREEN_HEIGHT = 740;
    /** 초상 크기 (게임) */
    public static final int PORTRAIT_SIZE = 92;
    /** 큰 초상 크기 (로그인, 결과의 장원) */
    public static final int PORTRAIT_SIZE_LARGE = 150;

    // ---- 색: 글자 ----
    /** 기본 글자, 진한 테두리 */
    public static final Color TEXT = new Color(0x2B2420);
    /** 보조 글자 (안내, 설명) */
    public static final Color TEXT_SUB = new Color(0x6B5B4B);
    /** 진한 배경 위의 글자 */
    public static final Color TEXT_LIGHT = new Color(0xF6E7C1);
    /** 점수 글자 */
    public static final Color TEXT_SCORE = new Color(0x8C5A12);

    // ---- 색: 바탕 ----
    /** 화면 배경 (한지) */
    public static final Color BACKGROUND = new Color(0xF4EBD9);
    /** 종이 (카드, 답안지, 말풍선) */
    public static final Color PAPER = new Color(0xFBF6EA);
    /** 종이 테두리, 구분선 */
    public static final Color PAPER_BORDER = new Color(0xC9B58E);
    /** 로비 돗자리 */
    public static final Color MAT = new Color(0xE7D4A2);
    /** 결과 화면 장원 배경 */
    public static final Color WINNER_BACKGROUND = new Color(0x3B6690);

    // ---- 색: 공통 ----
    /** 빨강 (낙관, 강조 버튼, 오답, 1등 배지) */
    public static final Color RED = new Color(0xB8402E);
    /** 진한 빨강 (빨강 테두리, 기둥 테두리) */
    public static final Color RED_DARK = new Color(0x7E2A1E);
    /** 밝은 갈색 (팻말, 명패) */
    public static final Color BROWN_LIGHT = new Color(0xC99A5B);
    /** 갈색 (명패와 버튼 테두리, 두루마리 축, 3등 배지) */
    public static final Color BROWN = new Color(0x7A5232);
    /** 진한 갈색 (현판 배경, 학동 배지, 3등 배지 테두리) */
    public static final Color BROWN_DARK = new Color(0x4A2F1B);
    /** 금색 (현판과 강조 테두리, 장식) */
    public static final Color GOLD = new Color(0xD7A542);
    /** 초록 (단청, 기둥 머리, 정답 글자) */
    public static final Color GREEN = new Color(0x2F6B5E);
    /** 파랑 (2등 배지) */
    public static final Color BLUE = new Color(0x2A4A6B);
    /** 진한 파랑 (2등 배지 테두리) */
    public static final Color BLUE_DARK = new Color(0x1B3149);
    /** 회색 (4, 5등 배지) */
    public static final Color GRAY = new Color(0x8A8178);

    // ---- 색: 건물, 소품 ----
    /** 기둥 */
    public static final Color PILLAR = new Color(0xA63A2B);
    /** 주춧돌 */
    public static final Color STONE = new Color(0x9A958C);
    /** 마루 */
    public static final Color FLOOR = new Color(0x8A5E38);
    /** 마루 널 경계선 */
    public static final Color FLOOR_LINE = new Color(0x5A3B22);
    /** 향 */
    public static final Color INCENSE = new Color(0xA8743F);

    // ---- 색: 지폐, 동전 (캐릭터 번호 순) ----
    private static final Color[] MONEY_COLORS = {
            new Color(0x4F8A55), // 0 세종: 만 원 녹색
            new Color(0x4F78B0), // 1 퇴계: 천 원 파랑
            new Color(0xBE6A4A), // 2 율곡: 오천 원 주황
            new Color(0xE0A92E), // 3 사임당: 오만 원 노랑
            new Color(0x9A9C98)  // 4 이순신: 백 원 은색
    };

    /** 캐릭터 번호(0~4)의 인물이 그려진 지폐, 동전의 색 */
    public static Color moneyColor(int character) {
        return MONEY_COLORS[character];
    }

    // ---- 색: 반투명 ----
    /** 카운트다운 때 화면을 덮는 어두운 막 */
    public static final Color OVERLAY = new Color(28, 24, 20, 120);
    /** 로비에서 나를 표시하는 그림자 */
    public static final Color SHADOW = new Color(78, 52, 46, 45);
    /** 로비에서 나를 표시하는 그림자의 안쪽 */
    public static final Color SHADOW_DARK = new Color(78, 52, 46, 75);

    // ---- 폰트 (resources/fonts, SIL OFL) ----
    private static final Font BATANG = load("GowunBatang-Regular.ttf");
    private static final Font BATANG_BOLD = load("GowunBatang-Bold.ttf");

    /** 고운바탕: 기본 글꼴 */
    public static Font batang(float size) {
        return BATANG.deriveFont(size);
    }

    /** 고운바탕 굵게: 제목, 버튼 */
    public static Font batangBold(float size) {
        return BATANG_BOLD.deriveFont(size);
    }

    /** 화살표 기호: 시스템 글꼴 (고운바탕에 ◀, ▶가 없음) */
    public static Font symbolFont(float size) {
        return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size));
    }

    /** 캐릭터 번호(0 세종, 1 퇴계, 2 율곡, 3 사임당, 4 이순신)의 초상 경로 */
    public static String portraitPath(int character) {
        return "/lobby/portraits/ch" + character + ".png";
    }

    /** 캐릭터 번호(0~4)의 초상을 size 크기로 그리는 아이콘 */
    public static Icon portraitIcon(int character, int size) {
        Image img = readImage(portraitPath(character));
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(img, x, y, size, size, null);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /** images/game/의 이름.png와 이름@2x.png를 묶은 이미지 (화면 배율에 맞는 쪽이 그려진다) */
    public static Image image(String name) {
        return new BaseMultiResolutionImage(read(name + ".png"), read(name + "@2x.png"));
    }

    /** image(이름)를 1배 PNG 크기로 그리는 아이콘 */
    public static Icon icon(String name) {
        Image img = image(name);
        int w = img.getWidth(null);
        int h = img.getHeight(null);
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.drawImage(img, x, y, w, h, null);
            }

            @Override
            public int getIconWidth() {
                return w;
            }

            @Override
            public int getIconHeight() {
                return h;
            }
        };
    }

    private static Font load(String file) {
        try (InputStream in = resource("/fonts/" + file)) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            // 등록해야 HTML 라벨(귀띔)이 이름으로 이 폰트를 찾는다
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("폰트를 읽을 수 없음: " + file, e);
        }
    }

    private static Image read(String file) {
        return readImage("/images/game/" + file);
    }

    private static Image readImage(String path) {
        try (InputStream in = resource(path)) {
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream resource(String path) {
        InputStream in = Theme.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("리소스 없음: " + path);
        }
        return in;
    }
}
