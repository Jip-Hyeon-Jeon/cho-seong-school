package com.jiphyeonjeon.choseongschool.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.jiphyeonjeon.choseongschool.common.GameConstants.Screen;
import com.jiphyeonjeon.choseongschool.controller.ResultController;
import com.jiphyeonjeon.choseongschool.model.Player;

/** 최종 결과 화면: 왼쪽 장원(우승자), 오른쪽 방(최종 순위) */
public class ResultView extends JPanel {

	private static final long serialVersionUID = 1L;

	//색 (피그마 6. 최종 결과)
	private static final Color BACKGROUND = new Color(0xF4EBD9);
	private static final Color PAPER = new Color(0xFBF6EA);
	private static final Color PAPER_LINE = new Color(0xC9B58E);
	private static final Color INK = new Color(0x2B2420);
	private static final Color SUB_INK = new Color(0x6B5B4B);
	private static final Color SCORE = new Color(0x8C5A12);
	private static final Color GOLD = new Color(0xD7A542);
	private static final Color CREAM = new Color(0xF6E7C1);
	private static final Color PLAQUE = new Color(0x4A2F1B);
	private static final Color WINNER_BACK = new Color(0x3B6690);
	private static final Color RED = new Color(0xB8402E);
	private static final Color RED_DARK = new Color(0x7E2A1E);
	private static final Color BLUE = new Color(0x2A4A6B);
	private static final Color BLUE_DARK = new Color(0x1B3149);
	private static final Color BROWN = new Color(0x7A5232);
	private static final Color GRAY = new Color(0x8A8178);

	//글꼴: 고운바탕
	private static final Font BATANG = Theme.batang(12f);
	private static final Font BATANG_BOLD = Theme.batangBold(12f);

	//한 번 읽은 이미지 보관 (그릴 때마다 파일을 다시 읽지 않도록)
	private final Map<String, Image> images = new HashMap<>();

	private JButton waitingRoomBtn;
	private JButton exitBtn;
	private List<Player> players = new ArrayList<>();	//점수 높은 순

	public ResultView() {
		setLayout(null);
		setPreferredSize(new Dimension(Screen.WIDTH, Screen.HEIGHT));
		setBackground(BACKGROUND);

		waitingRoomBtn = new ResultButton("서당 마루로", RED, RED_DARK, CREAM);
		waitingRoomBtn.setBounds(423, 656, 200, 50);
		add(waitingRoomBtn);

		exitBtn = new ResultButton("나가기", PAPER, BROWN, INK);
		exitBtn.setBounds(637, 656, 140, 50);
		add(exitBtn);
	}

	public void setWaitingRoomButtonListener(ActionListener listener) {
		waitingRoomBtn.addActionListener(listener);
	}

	public void setExitButtonListener(ActionListener listener) {
		exitBtn.addActionListener(listener);
	}

	/** 최종 결과 표시 */
	public void showResult(List<Player> result) {
		players = new ArrayList<>(result);
		players.sort(Comparator.comparingInt(Player::getScore).reversed());
		repaint();	//paintComponent가 다시 호출됨
	}

	//나보다 점수가 높은 사람 수 + 1 (동점이면 같은 등수: 1, 1, 3)
	private int getRank(Player player) {
		int rank = 1;
		for (Player other : players) {
			if (other.getScore() > player.getScore()) {
				rank++;
			}
		}
		return rank;
	}

	//우승자 목록 (공동 우승이면 여러 명, 전원 0점이면 없음)
	private List<Player> getWinners() {
		List<Player> winners = new ArrayList<>();
		for (Player player : players) {
			if (getRank(player) == 1 && player.getScore() > 0) {
				winners.add(player);
			}
		}
		return winners;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		setQuality(g2);
		paintFrame(g2);
		paintWinner(g2);
		paintRanking(g2);
		g2.dispose();
	}

	//지붕, 기둥, 마루, 구름, 현판
	private void paintFrame(Graphics2D g2) {
		drawImage(g2, "/images/result/roof.png", 0, 0, 1200, 72);
		drawImage(g2, "/images/result/dancheong.png", 0, 72, 1200, 18);

		for (int x : new int[] { 0, 1176 }) {
			drawBox(g2, x, 90, 24, 626, 0, 2, new Color(0xA63A2B), RED_DARK);
			drawBox(g2, x - 4, 90, 32, 10, 0, 0, new Color(0x2F6B5E), null);
			drawBox(g2, x - 6, 706, 36, 12, 3, 0, new Color(0x9A958C), null);
		}

		drawBox(g2, 0, 716, 1200, 24, 0, 0, new Color(0x8A5E38), null);
		for (int x = 150; x <= 1050; x += 150) {
			drawBox(g2, x, 716, 2, 24, 0, 0, new Color(0x5A3B22), null);
		}

		drawImage(g2, "/images/result/cloud.png", 110, 108, 121, 58);
		drawImage(g2, "/images/result/cloud.png", 960, 108, 121, 58);

		drawBox(g2, 410, 102, 380, 80, 6, 4, PLAQUE, GOLD);
		drawCenter(g2, "오늘의 장원", BATANG_BOLD, 62, CREAM, 600, 142, 350);
	}

	//왼쪽: 장원 초상, 이름, 점수
	private void paintWinner(Graphics2D g2) {
		drawBox(g2, 48, 194, 420, 450, 8, 0, WINNER_BACK, null);
		Shape oldClip = g2.getClip();
		g2.clip(new RoundRectangle2D.Double(48, 194, 420, 450, 16, 16));
		drawImage(g2, "/images/result/landscape.png", 48, 194, 420, 280);
		g2.setClip(oldClip);
		drawBox(g2, 48, 194, 420, 450, 8, 4, null, GOLD);

		List<Player> winners = getWinners();
		if (winners.isEmpty()) {
			//결과가 오기 전에는 명패를 그리지 않음
			if (!players.isEmpty()) {
				drawBox(g2, 98, 486, 320, 75, 6, 3, PAPER, GOLD);
				drawCenter(g2, "오늘은 장원이 없소", BATANG_BOLD, 36, INK, 258, 523, 290);
			}
			return;
		}

		//초상: 한 명이면 크게, 공동 우승이면 작게 나란히
		int count = winners.size();
		int size = (count == 1) ? 150 : Math.min(120, 390 / count - 8);
		int step = size + 8;
		int startX = 258 - (step * count - 8) / 2;
		int y = 476 - size;
		int flowerWidth = size * 192 / 150;
		int flowerHeight = size * 110 / 150;
		for (int i = 0; i < count; i++) {
			int x = startX + step * i;
			drawImage(g2, "/images/result/eosahwa.png", x + size / 2 - flowerWidth / 2, y - size * 94 / 150,
					flowerWidth, flowerHeight);
			drawPortrait(g2, winners.get(i), x, y, size);
		}

		//명패: 글자 길이에 맞춰 너비가 늘어남
		List<String> names = new ArrayList<>();
		for (Player winner : winners) {
			names.add(winner.getNickname());
		}
		String name = String.join(", ", names);
		int nameWidth = textWidth(g2, name, BATANG_BOLD, 50, 265);
		int plateWidth = 25 + 55 + 12 + nameWidth + 25;
		int plateX = 258 - plateWidth / 2;
		drawBox(g2, plateX, 486, plateWidth, 75, 6, 3, PAPER, GOLD);
		drawBox(g2, plateX + 25, 505, 55, 36, 5, 2, RED, RED_DARK);
		drawCenter(g2, "장원", BATANG_BOLD, 24, CREAM, plateX + 52, 523, 45);
		drawLeft(g2, name, BATANG_BOLD, 50, INK, plateX + 92, 523, 265);

		//점수
		String score = winners.get(0).getScore() + "점";
		int scoreWidth = textWidth(g2, score, BATANG_BOLD, 22, 200);
		int boxWidth = 10 + 24 + 6 + scoreWidth + 14;
		int boxX = 258 - boxWidth / 2;
		drawBox(g2, boxX, 571, boxWidth, 39, 14, 0, PAPER, null);
		drawImage(g2, "/images/result/coin.png", boxX + 10, 578, 24, 24);
		drawLeft(g2, score, BATANG_BOLD, 22, SCORE, boxX + 40, 590, 200);
	}

	//오른쪽: 방(순위표)
	private void paintRanking(Graphics2D g2) {
		g2.setColor(PAPER);
		g2.fillRect(504, 204, 632, 430);
		g2.setColor(PAPER_LINE);
		g2.fillRect(504, 204, 632, 2);
		g2.fillRect(504, 632, 632, 2);

		for (int x : new int[] { 488, 1136 }) {
			drawBox(g2, x, 194, 16, 450, 8, 2, BROWN, INK);
			g2.setColor(GOLD);
			g2.fillOval(x + 4, 201, 8, 8);
			g2.fillOval(x + 4, 629, 8, 8);
		}

		//고운바탕에 없는 한자라서 윈도우 바탕체 사용
		Font hanja = new Font("바탕", Font.BOLD, 22);
		int x = 530;
		drawLeft(g2, "방", BATANG_BOLD, 46, INK, x, 249, 60);
		x += textWidth(g2, "방", BATANG_BOLD, 46, 60) + 10;
		drawLeft(g2, "(榜)", hanja, 22, SUB_INK, x, 253, 60);
		x += textWidth(g2, "(榜)", hanja, 22, 60) + 10;
		drawLeft(g2, "최종 순위 · 동점은 같은 등수", BATANG, 14, SUB_INK, x, 258, 250);

		g2.setColor(RED);
		g2.fillRect(530, 286, 580, 2);

		//5명이면 종이를 넘치므로 줄 높이를 줄임
		int rowHeight = (players.size() >= 5) ? 56 : 62;
		for (int i = 0; i < players.size(); i++) {
			paintRow(g2, players.get(i), 296 + i * (rowHeight + 8), rowHeight);
		}
	}

	//순위 한 줄
	private void paintRow(Graphics2D g2, Player player, int y, int height) {
		int centerY = y + height / 2 - 1;
		int rank = getRank(player);

		Color fill = GRAY;
		Color border = SUB_INK;
		String label = rank + "등";
		if (rank == 1) {
			fill = RED;
			border = RED_DARK;
			if (player.getScore() > 0) {
				label = "장원";
			}
		} else if (rank == 2) {
			fill = BLUE;
			border = BLUE_DARK;
		} else if (rank == 3) {
			fill = BROWN;
			border = PLAQUE;
		}
		drawBox(g2, 536, centerY - 19, 58, 38, 5, 2, fill, border);
		drawCenter(g2, label, BATANG_BOLD, 17, CREAM, 565, centerY, 50);

		drawPortrait(g2, player, 610, centerY - 23, 46);
		drawLeft(g2, player.getNickname(), BATANG_BOLD, 22, INK, 672, centerY, 300);

		String score = player.getScore() + "점";
		int scoreX = 1104 - textWidth(g2, score, BATANG_BOLD, 22, 200);
		drawImage(g2, "/images/result/coin.png", scoreX - 40, centerY - 12, 24, 24);
		drawLeft(g2, score, BATANG_BOLD, 22, SCORE, scoreX, centerY, 200);

		g2.setColor(PAPER_LINE);
		g2.setStroke(new BasicStroke(1.5f));
		g2.drawLine(530, y + height - 1, 1110, y + height - 1);
	}

	//둥근 초상화 (이미지가 없으면 빈 원)
	private void drawPortrait(Graphics2D g2, Player player, int x, int y, int size) {
		Image image = getImage(player.getCharacter());
		if (image == null) {
			g2.setColor(PAPER_LINE);
			g2.fill(new Ellipse2D.Double(x, y, size, size));
			return;
		}
		g2.drawImage(image, x, y, size, size, null);
	}

	private void drawImage(Graphics2D g2, String path, int x, int y, int width, int height) {
		Image image = getImage(path);
		if (image != null) {
			g2.drawImage(image, x, y, width, height, null);
		}
	}

	private Image getImage(String path) {
		if (path == null) {
			return null;
		}
		if (!images.containsKey(path)) {
			Image image = null;
			URL url = ResultView.class.getResource(path);
			if (url != null) {
				try {
					image = ImageIO.read(url);
				} catch (IOException e) {
					System.out.println("이미지를 읽을 수 없음: " + path);
				}
			}
			images.put(path, image);
		}
		return images.get(path);
	}

	//x에서 시작하는 글자, 너비가 넘치면 글자 크기를 줄임
	private static void drawLeft(Graphics2D g2, String text, Font font, int size, Color color, int x, int centerY,
			int maxWidth) {
		g2.setFont(fitFont(g2, text, font, size, maxWidth));
		g2.setColor(color);
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(text, x, centerY + (fm.getAscent() - fm.getDescent()) / 2);
	}

	//centerX가 가운데인 글자
	private static void drawCenter(Graphics2D g2, String text, Font font, int size, Color color, int centerX,
			int centerY, int maxWidth) {
		int width = textWidth(g2, text, font, size, maxWidth);
		drawLeft(g2, text, font, size, color, centerX - width / 2, centerY, maxWidth);
	}

	private static int textWidth(Graphics2D g2, String text, Font font, int size, int maxWidth) {
		return g2.getFontMetrics(fitFont(g2, text, font, size, maxWidth)).stringWidth(text);
	}

	private static Font fitFont(Graphics2D g2, String text, Font font, int size, int maxWidth) {
		Font result = font.deriveFont((float) size);
		while (size > 10 && g2.getFontMetrics(result).stringWidth(text) > maxWidth) {
			size--;
			result = font.deriveFont((float) size);
		}
		return result;
	}

	//둥근 상자, 테두리는 상자 안쪽에 그림 (fill, border는 null이면 생략)
	private static void drawBox(Graphics2D g2, int x, int y, int width, int height, int radius, int borderWidth,
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

	private static void setQuality(Graphics2D g2) {
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}

	/** 결과 화면 버튼 */
	private static class ResultButton extends JButton {

		private static final long serialVersionUID = 1L;
		private final Color fill;
		private final Color border;

		ResultButton(String text, Color fill, Color border, Color textColor) {
			super(text);
			this.fill = fill;
			this.border = border;
			setForeground(textColor);
			setFont(BATANG_BOLD.deriveFont(19f));
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			setQuality(g2);
			Color color = getModel().isPressed() ? fill.darker() : fill;
			int borderWidth = isFocusOwner() ? 3 : 2;	//키보드로 선택된 버튼은 테두리를 굵게
			drawBox(g2, 0, 0, getWidth(), getHeight(), 6, borderWidth, color, border);
			drawCenter(g2, getText(), getFont(), 19, getForeground(), getWidth() / 2, getHeight() / 2,
					getWidth() - 36);
			g2.dispose();
		}
	}

	/** 화면 확인용 실행 (통신 연결 후 삭제) */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			ResultView view = new ResultView();
			ResultController controller = new ResultController(view);

			JFrame frame = new JFrame("최종 결과");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.add(view);
			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);

			controller.showFinalResult(List.of(
					new Player("가", "/lobby/portraits/ch3.png", 58),
					new Player("나", "/lobby/portraits/ch0.png", 44),
					new Player("다", "/lobby/portraits/ch2.png", 31)));
		});
	}
}
