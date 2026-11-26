package com.jiphyeonjeon.choseongschool.client.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import com.jiphyeonjeon.choseongschool.common.model.ItemKind;

/**
 * 묘수전 아이템(곶감, 약과) 그리기. 이미지 파일 없이 도형으로 그린다.
 * 모든 좌표는 120x120 칸 기준이고, 그릴 때 원하는 크기로 늘리거나 줄인다.
 */
final class ItemPainter {

	private static final double BASE = 120;
	private static final float LINE = 1.4f;

	//곶감 잎: {끝이 향하는 각도(도, 0은 오른쪽, 90은 아래), 길이, 폭}
	private static final double[][] LEAVES = { { 190, 32, 15 }, { 350, 32, 15 }, { 235, 22, 13 },
			{ 305, 22, 13 }, { 145, 22, 12 }, { 35, 22, 12 } };

	private ItemPainter() {
	}

	/** (x, y)를 왼쪽 위로 하는 size 크기 칸에 아이템과 단어를 그린다 */
	static void paint(Graphics2D g2, ItemKind kind, String word, double x, double y, double size) {
		Graphics2D g = (Graphics2D) g2.create();
		HanokPainter.setQuality(g);
		g.translate(x, y);
		g.scale(size / BASE, size / BASE);
		if (kind == ItemKind.GOTGAM) {
			paintGotgam(g);
			paintWord(g, word, 60, 68);
		} else {
			paintYakgwa(g);
			paintWord(g, word, 60, 60);
		}
		g.dispose();
	}

	//곶감: 살짝 옆으로 퍼진 원형 몸통, 아래쪽 그늘, 사방으로 퍼진 잎
	private static void paintGotgam(Graphics2D g) {
		Shape body = new Ellipse2D.Double(9, 22, 102, 88);
		g.setColor(Theme.GOTGAM);
		g.fill(body);
		Shape clip = g.getClip();
		g.clip(body);
		g.setColor(Theme.GOTGAM_SHADE);
		g.fill(new Ellipse2D.Double(0, 74, 120, 70));
		g.setClip(clip);
		g.setColor(Theme.GOTGAM_LINE);
		g.setStroke(new BasicStroke(LINE));
		g.draw(body);

		double cx = 60;
		double cy = 28;
		for (double[] leaf : LEAVES) {
			Graphics2D c = (Graphics2D) g.create();
			c.rotate(Math.toRadians(leaf[0] + 90), cx, cy);
			double length = leaf[1];
			double half = leaf[2] / 2;
			Path2D shape = new Path2D.Double();
			shape.moveTo(cx, cy);
			shape.curveTo(cx - half * 1.3, cy - length * 0.35, cx - half * 0.8, cy - length * 0.8, cx, cy - length);
			shape.curveTo(cx + half * 0.8, cy - length * 0.8, cx + half * 1.3, cy - length * 0.35, cx, cy);
			fillAndDraw(c, shape, Theme.LEAF, Theme.LEAF_LINE, 1.1f);
			c.setStroke(new BasicStroke(1f));
			c.draw(new Line2D.Double(cx, cy - 4, cx, cy - length * 0.75));
			c.dispose();
		}
		fillAndDraw(g, new Ellipse2D.Double(cx - 8, cy - 6, 16, 12), Theme.LEAF_DARK, Theme.LEAF_LINE, 1.1f);
		g.setColor(Theme.STEM);
		g.fill(new Ellipse2D.Double(cx - 3, cy - 2.5, 6, 5));
	}

	//약과: 둥근 꽃잎 14장, 가운데 판, 오목 무늬
	private static void paintYakgwa(Graphics2D g) {
		double cx = 60;
		double cy = 60;
		fillAndDraw(g, new Ellipse2D.Double(cx - 46, cy - 46, 92, 92), Theme.YAKGWA_BASE, Theme.YAKGWA_LINE, LINE);
		int petals = 14;
		for (int i = 0; i < petals; i++) {
			double angle = Math.PI * 2 * i / petals;
			double px = cx + Math.cos(angle) * 44;
			double py = cy + Math.sin(angle) * 44;
			Graphics2D c = (Graphics2D) g.create();
			c.rotate(angle, px, py);
			Color fill = (i % 2 == 0) ? Theme.YAKGWA_PETAL : Theme.YAKGWA_PETAL_DARK;
			fillAndDraw(c, new Ellipse2D.Double(px - 11, py - 10, 22, 20), fill, Theme.YAKGWA_LINE, LINE);
			c.dispose();
		}
		fillAndDraw(g, new Ellipse2D.Double(cx - 34, cy - 34, 68, 68), Theme.YAKGWA_CENTER, Theme.YAKGWA_LINE, LINE);
		fillAndDraw(g, new Ellipse2D.Double(cx - 27, cy - 27, 54, 54), Theme.YAKGWA_INNER, Theme.YAKGWA_INNER_LINE,
				1.1f);
		g.setColor(Theme.YAKGWA_DOT);
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 4; col++) {
				double dx = cx - 16.5 + col * 11;
				double dy = cy - 19 + row * 12.7;
				g.fill(new RoundRectangle2D.Double(dx - 4, dy - 3.2, 8, 6.4, 5, 5));
			}
		}
	}

	//(cx, cy)가 가운데인 한지 띠와 단어
	private static void paintWord(Graphics2D g, String word, double cx, double cy) {
		Font font = Theme.batangBold(20);
		int width = Math.max(g.getFontMetrics(font).stringWidth(word) + 16, 76);
		fillAndDraw(g, new RoundRectangle2D.Double(cx - width / 2.0, cy - 14, width, 28, 8, 8), Theme.PAPER, Theme.TEXT,
				LINE);
		g.setFont(font);
		g.setColor(Theme.TEXT);
		Rectangle2D bounds = font.createGlyphVector(g.getFontRenderContext(), word).getVisualBounds();
		g.drawString(word, (float) (cx - bounds.getWidth() / 2 - bounds.getX()),
				(float) (cy - bounds.getHeight() / 2 - bounds.getY()));
	}

	private static void fillAndDraw(Graphics2D g, Shape shape, Color fill, Color line, float lineWidth) {
		g.setColor(fill);
		g.fill(shape);
		g.setColor(line);
		g.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(shape);
	}
}
