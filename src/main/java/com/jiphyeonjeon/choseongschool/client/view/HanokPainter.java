package com.jiphyeonjeon.choseongschool.client.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;


/** 한국풍 화면(로그인, 대기실, 결과)이 함께 쓰는 그리기 도구 */
final class HanokPainter {

	//한 번 읽은 이미지 보관 (그릴 때마다 파일을 다시 읽지 않도록)
	private static final Map<String, Image> IMAGES = new HashMap<>();

	private HanokPainter() {
	}

	//기와지붕, 단청, 기둥, 마루
	static void paintHanok(Graphics2D g2) {
		drawImage(g2, "/images/result/roof.png", 0, 0, Theme.SCREEN_WIDTH, 72);
		drawImage(g2, "/images/result/dancheong.png", 0, 72, Theme.SCREEN_WIDTH, 18);

		for (int x : new int[] { 0, 1176 }) {
			drawBox(g2, x, 90, 24, 626, 0, 2, Theme.PILLAR, Theme.RED_DARK);
			drawBox(g2, x - 4, 90, 32, 10, 0, 0, Theme.GREEN, null);
			drawBox(g2, x - 6, 706, 36, 12, 3, 0, Theme.STONE, null);
		}

		drawBox(g2, 0, 716, Theme.SCREEN_WIDTH, 24, 0, 0, Theme.FLOOR, null);
		for (int x = 150; x <= 1050; x += 150) {
			drawBox(g2, x, 716, 2, 24, 0, 0, Theme.FLOOR_LINE, null);
		}
	}

	//두루마리의 세로 축
	static void paintRod(Graphics2D g2, int x, int y, int height) {
		drawBox(g2, x, y, 16, height, 8, 2, Theme.BROWN, Theme.TEXT);
		g2.setColor(Theme.GOLD);
		g2.fillOval(x + 4, y + 7, 8, 8);
		g2.fillOval(x + 4, y + height - 15, 8, 8);
	}

	static void drawImage(Graphics2D g2, String path, int x, int y, int width, int height) {
		Image image = getImage(path);
		if (image != null) {
			g2.drawImage(image, x, y, width, height, null);
		}
	}

	static Image getImage(String path) {
		if (path == null) {
			return null;
		}
		if (!IMAGES.containsKey(path)) {
			Image image = null;
			URL url = HanokPainter.class.getResource(path);
			if (url != null) {
				try {
					image = ImageIO.read(url);
				} catch (IOException e) {
					System.out.println("이미지를 읽을 수 없음: " + path);
				}
			}
			IMAGES.put(path, image);
		}
		return IMAGES.get(path);
	}

	//x에서 시작하는 글자, 너비가 넘치면 글자 크기를 줄임
	static void drawLeft(Graphics2D g2, String text, Font font, int size, Color color, int x, int centerY,
			int maxWidth) {
		g2.setFont(fitFont(g2, text, font, size, maxWidth));
		g2.setColor(color);
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(text, x, centerY + (fm.getAscent() - fm.getDescent()) / 2);
	}

	//centerX가 가운데인 글자
	static void drawCenter(Graphics2D g2, String text, Font font, int size, Color color, int centerX,
			int centerY, int maxWidth) {
		int width = textWidth(g2, text, font, size, maxWidth);
		drawLeft(g2, text, font, size, color, centerX - width / 2, centerY, maxWidth);
	}

	static int textWidth(Graphics2D g2, String text, Font font, int size, int maxWidth) {
		return g2.getFontMetrics(fitFont(g2, text, font, size, maxWidth)).stringWidth(text);
	}

	static Font fitFont(Graphics2D g2, String text, Font font, int size, int maxWidth) {
		Font result = font.deriveFont((float) size);
		while (size > 10 && g2.getFontMetrics(result).stringWidth(text) > maxWidth) {
			size--;
			result = font.deriveFont((float) size);
		}
		return result;
	}

	//둥근 상자, 테두리는 상자 안쪽에 그림 (fill, border는 null이면 생략)
	static void drawBox(Graphics2D g2, int x, int y, int width, int height, int radius, int borderWidth,
			Color fill, Color border) {
		if (fill != null) {
			g2.setColor(fill);
			g2.fillRoundRect(x, y, width, height, radius * 2, radius * 2);
		}
		if (border != null && borderWidth > 0) {
			double half = borderWidth / 2.0;
			double arc = Math.max(0, radius * 2 - borderWidth);
			g2.setColor(border);
			g2.setStroke(new BasicStroke(borderWidth));
			g2.draw(new RoundRectangle2D.Double(x + half, y + half, width - borderWidth, height - borderWidth, arc,
					arc));
		}
	}

	static void setQuality(Graphics2D g2) {
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}
}
