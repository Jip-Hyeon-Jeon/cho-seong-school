package com.jiphyeonjeon.choseongschool.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import javax.swing.Icon;

/**
 * [클라이언트] 한국풍 시안(Figma "초성서당 화면 설계 (한국풍 고급 시안)")의 색 · 폰트 · 이미지.
 * 이미지는 resources/images/game/ 에 1배(이름.png)와 2배(이름@2x.png)를 두고, 화면 배율에 맞는 쪽이 그려진다.
 */
public final class Theme {

    private Theme() {
    }

    // ---- 색 (시안 그대로) ----
    /** 한지 바탕 */
    public static final Color PAPER_BG = new Color(0xF4EBD9);
    /** 종이 · 답안지 · 말풍선 */
    public static final Color PAPER = new Color(0xFBF6EA);
    /** 종이 테두리, 재 */
    public static final Color PAPER_LINE = new Color(0xC9B58E);
    /** 먹색 (본문, 진한 테두리) */
    public static final Color INK = new Color(0x2B2420);
    /** 흐린 먹색 (보조 글자) */
    public static final Color INK_SOFT = new Color(0x6B5B4B);
    /** 낙관 · 제출 버튼 · 남은 초 */
    public static final Color SEAL = new Color(0xB8402E);
    /** 낙관 테두리 */
    public static final Color SEAL_LINE = new Color(0x7E2A1E);
    /** 낙관 글자 */
    public static final Color SEAL_TEXT = new Color(0xF6E7C1);
    /** 팻말 · 명패 */
    public static final Color WOOD = new Color(0xC99A5B);
    /** 팻말 · 명패 테두리, 두루마리 축 */
    public static final Color WOOD_DARK = new Color(0x7A5232);
    /** 팻말 보조 글자 */
    public static final Color WOOD_TEXT = new Color(0x4A2F1B);
    /** 점수 글자 */
    public static final Color SCORE = new Color(0x8C5A12);
    /** 향 */
    public static final Color INCENSE = new Color(0xA8743F);
    /** 기둥 */
    public static final Color PILLAR = new Color(0xA63A2B);
    /** 주두 (기둥 머리) · 정답 말풍선 글자 */
    public static final Color JADE = new Color(0x2F6B5E);
    /** 주춧돌 */
    public static final Color STONE = new Color(0x9A958C);
    /** 마루 */
    public static final Color FLOOR = new Color(0x8A5E38);
    /** 마루 널 */
    public static final Color FLOOR_LINE = new Color(0x5A3B22);

    // ---- 폰트 (resources/fonts, SIL OFL) ----
    private static final Font BRUSH = load("NanumBrushScript-Regular.ttf");
    private static final Font BATANG = load("GowunBatang-Regular.ttf");
    private static final Font BATANG_BOLD = load("GowunBatang-Bold.ttf");
    private static final Font HAHMLET_BLACK = load("Hahmlet-Black.ttf");

    /** 나눔손글씨 붓 — "제 N 강" 전용 (숫자 · 제 · 강만 들어 있음) */
    public static Font brush(float size) {
        return BRUSH.deriveFont(size);
    }

    /** 고운바탕 */
    public static Font batang(float size) {
        return BATANG.deriveFont(size);
    }

    /** 고운바탕 굵게 */
    public static Font batangBold(float size) {
        return BATANG_BOLD.deriveFont(size);
    }

    /** 함렛 Black — 문제 문장 · 남은 초 */
    public static Font hahmlet(float size) {
        return HAHMLET_BLACK.deriveFont(size);
    }

    /** 답안 입력 글자 (시안은 Noto Sans KR, 여기서는 시스템 고딕) */
    public static Font input(float size) {
        return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size));
    }

    /** images/game/이름.png + 이름@2x.png 를 하나로 묶은 이미지 */
    public static Image image(String name) {
        return new BaseMultiResolutionImage(read(name + ".png"), read(name + "@2x.png"));
    }

    /** 1배 PNG 크기 그대로 그리는 아이콘 (화면 배율이 크면 @2x가 쓰인다) */
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
        try (InputStream in = resource("/images/game/" + file)) {
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
