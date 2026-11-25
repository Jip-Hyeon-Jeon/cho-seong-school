package com.jiphyeonjeon.choseongschool.controller;

import java.util.List;

import javax.swing.SwingUtilities;

import com.jiphyeonjeon.choseongschool.model.Player;
import com.jiphyeonjeon.choseongschool.view.ResultView;

public class ResultController {

	private ResultView view;

	public ResultController(ResultView view) {
		this.view = view;
		initEventBindings();
	}

	private void initEventBindings() {
		view.setWaitingRoomButtonListener(e -> onWaitingRoom());
		view.setExitButtonListener(e -> onExit());
	}

	//서버에서 최종 결과를 받았을 때 호출
	//통신 스레드에서 불러도 되도록 화면 변경은 Swing 스레드로 넘김
	public void showFinalResult(List<Player> players) {
		SwingUtilities.invokeLater(() -> view.showResult(players));
	}

	private void onWaitingRoom() {
		//TODO: MainFrame이 생기면 대기실 화면으로 전환
		System.out.println("서당 마루로 이동");
	}

	private void onExit() {
		//TODO: 통신이 연결되면 서버 접속 종료 처리
		System.out.println("나가기");
	}
}
