package com.jiphyeonjeon.choseongschool.client.view;

import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.drawBox;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.drawCenter;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.drawImage;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.drawLeft;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.getImage;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.paintHanok;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.paintRod;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.setQuality;
import static com.jiphyeonjeon.choseongschool.client.view.HanokPainter.textWidth;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Shape;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.jiphyeonjeon.choseongschool.client.controller.ResultController;
import com.jiphyeonjeon.choseongschool.client.model.Player;

/** 최종 결과 화면: 왼쪽 장원(우승자), 오른쪽 방(최종 순위) */
public class ResultView extends JPanel {

	private static final long serialVersionUID = 1L;

	//글꼴: 고운바탕
	private static final Font BATANG = Theme.batang(12f);
	private static final Font BATANG_BOLD = Theme.batangBold(12f);

	private JButton waitingRoomBtn;
	private JButton exitBtn;
	private List<Player> players = new ArrayList<>();	//점수 높은 순

	public ResultView() {
		setLayout(null);
		setPreferredSize(new Dimension(Theme.SCREEN_WIDTH, Theme.SCREEN_HEIGHT));
		setBackground(Theme.BACKGROUND);

		waitingRoomBtn = new RoundButton("서당 마루로", BATANG_BOLD.deriveFont(19f), Theme.RED, Theme.RED_DARK, Theme.TEXT_LIGHT, 6);
		waitingRoomBtn.setBounds(423, 656, 200, 50);
		add(waitingRoomBtn);

		exitBtn = new RoundButton("나가기", BATANG_BOLD.deriveFont(19f), Theme.PAPER, Theme.BROWN, Theme.TEXT, 6);
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

	//한옥 틀, 구름, 현판
	private void paintFrame(Graphics2D g2) {
		paintHanok(g2);

		drawImage(g2, "/images/result/cloud.png", 110, 108, 121, 58);
		drawImage(g2, "/images/result/cloud.png", 960, 108, 121, 58);

		drawBox(g2, 410, 102, 380, 80, 6, 4, Theme.BROWN_DARK, Theme.GOLD);
		drawCenter(g2, "오늘의 장원", BATANG_BOLD, 62, Theme.TEXT_LIGHT, 600, 142, 350);
	}

	//왼쪽: 장원 초상, 이름, 점수
	private void paintWinner(Graphics2D g2) {
		drawBox(g2, 48, 194, 420, 450, 8, 0, Theme.WINNER_BACKGROUND, null);
		Shape oldClip = g2.getClip();
		g2.clip(new RoundRectangle2D.Double(48, 194, 420, 450, 16, 16));
		drawImage(g2, "/images/result/landscape.png", 48, 194, 420, 280);
		g2.setClip(oldClip);
		drawBox(g2, 48, 194, 420, 450, 8, 4, null, Theme.GOLD);

		List<Player> winners = getWinners();
		if (winners.isEmpty()) {
			//결과가 오기 전에는 명패를 그리지 않음
			if (!players.isEmpty()) {
				drawBox(g2, 98, 486, 320, 75, 6, 3, Theme.PAPER, Theme.GOLD);
				drawCenter(g2, "오늘은 장원이 없소", BATANG_BOLD, 36, Theme.TEXT, 258, 523, 290);
			}
			return;
		}

		//초상: 한 명이면 크게, 공동 우승이면 작게 나란히
		int count = winners.size();
		int size = (count == 1) ? Theme.PORTRAIT_SIZE_LARGE : Math.min(120, 390 / count - 8);
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
		drawBox(g2, plateX, 486, plateWidth, 75, 6, 3, Theme.PAPER, Theme.GOLD);
		drawBox(g2, plateX + 25, 505, 55, 36, 5, 2, Theme.RED, Theme.RED_DARK);
		drawCenter(g2, "장원", BATANG_BOLD, 24, Theme.TEXT_LIGHT, plateX + 52, 523, 45);
		drawLeft(g2, name, BATANG_BOLD, 50, Theme.TEXT, plateX + 92, 523, 265);

		//점수
		String score = winners.get(0).getScore() + "점";
		int scoreWidth = textWidth(g2, score, BATANG_BOLD, 22, 200);
		int boxWidth = 10 + 24 + 6 + scoreWidth + 14;
		int boxX = 258 - boxWidth / 2;
		drawBox(g2, boxX, 571, boxWidth, 39, 14, 0, Theme.PAPER, null);
		drawImage(g2, "/images/result/coin.png", boxX + 10, 578, 24, 24);
		drawLeft(g2, score, BATANG_BOLD, 22, Theme.TEXT_SCORE, boxX + 40, 590, 200);
	}

	//오른쪽: 방(순위표)
	private void paintRanking(Graphics2D g2) {
		g2.setColor(Theme.PAPER);
		g2.fillRect(504, 204, 632, 430);
		g2.setColor(Theme.PAPER_BORDER);
		g2.fillRect(504, 204, 632, 2);
		g2.fillRect(504, 632, 632, 2);

		for (int x : new int[] { 488, 1136 }) {
			paintRod(g2, x, 194, 450);
		}

		//고운바탕에 없는 한자라서 윈도우 바탕체 사용
		Font hanja = Theme.hanjaFont(22);
		int x = 530;
		drawLeft(g2, "방", BATANG_BOLD, 46, Theme.TEXT, x, 249, 60);
		x += textWidth(g2, "방", BATANG_BOLD, 46, 60) + 10;
		drawLeft(g2, "(榜)", hanja, 22, Theme.TEXT_SUB, x, 253, 60);
		x += textWidth(g2, "(榜)", hanja, 22, 60) + 10;
		drawLeft(g2, "최종 순위 · 동점은 같은 등수", BATANG, 14, Theme.TEXT_SUB, x, 258, 250);

		g2.setColor(Theme.RED);
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

		Color fill = Theme.GRAY;
		Color border = Theme.TEXT_SUB;
		String label = rank + "등";
		if (rank == 1) {
			fill = Theme.RED;
			border = Theme.RED_DARK;
			if (player.getScore() > 0) {
				label = "장원";
			}
		} else if (rank == 2) {
			fill = Theme.BLUE;
			border = Theme.BLUE_DARK;
		} else if (rank == 3) {
			fill = Theme.BROWN;
			border = Theme.BROWN_DARK;
		}
		drawBox(g2, 536, centerY - 19, 58, 38, 5, 2, fill, border);
		drawCenter(g2, label, BATANG_BOLD, 17, Theme.TEXT_LIGHT, 565, centerY, 50);

		drawPortrait(g2, player, 610, centerY - 23, 46);
		drawLeft(g2, player.getNickname(), BATANG_BOLD, 22, Theme.TEXT, 672, centerY, 300);

		String score = player.getScore() + "점";
		int scoreX = 1104 - textWidth(g2, score, BATANG_BOLD, 22, 200);
		drawImage(g2, "/images/result/coin.png", scoreX - 40, centerY - 12, 24, 24);
		drawLeft(g2, score, BATANG_BOLD, 22, Theme.TEXT_SCORE, scoreX, centerY, 200);

		g2.setColor(Theme.PAPER_BORDER);
		g2.setStroke(new BasicStroke(1.5f));
		g2.drawLine(530, y + height - 1, 1110, y + height - 1);
	}

	//둥근 초상화 (이미지가 없으면 빈 원)
	private void drawPortrait(Graphics2D g2, Player player, int x, int y, int size) {
		Image image = getImage(player.getCharacter());
		if (image == null) {
			g2.setColor(Theme.PAPER_BORDER);
			g2.fill(new Ellipse2D.Double(x, y, size, size));
			return;
		}
		g2.drawImage(image, x, y, size, size, null);
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
					new Player("가", Theme.portraitPath(3), 58),
					new Player("나", Theme.portraitPath(0), 44),
					new Player("다", Theme.portraitPath(2), 31)));
		});
	}
}
