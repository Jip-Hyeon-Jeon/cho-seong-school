package com.jiphyeonjeon.choseongschool.view;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JButton;

/** 모서리가 둥근 버튼 (radius를 크기의 절반으로 주면 원) */
class RoundButton extends JButton {

	private static final long serialVersionUID = 1L;
	private final Color fill;
	private final Color border;
	private final int radius;

	RoundButton(String text, Font font, Color fill, Color border, Color textColor, int radius) {
		super(text);
		this.fill = fill;
		this.border = border;
		this.radius = radius;
		setFont(font);
		setForeground(textColor);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		HanokPainter.setQuality(g2);
		Color color = getModel().isPressed() ? fill.darker() : fill;
		int borderWidth = isFocusOwner() ? 3 : 2;	//키보드로 선택된 버튼은 테두리를 굵게
		HanokPainter.drawBox(g2, 0, 0, getWidth(), getHeight(), radius, borderWidth, color, border);
		HanokPainter.drawCenter(g2, getText(), getFont(), getFont().getSize(), getForeground(), getWidth() / 2,
				getHeight() / 2, getWidth() - 8);
		g2.dispose();
	}
}
