package com.jiphyeonjeon.choseongschool.client.controller;

import java.util.List;

import javax.swing.SwingUtilities;

import com.jiphyeonjeon.choseongschool.client.model.Player;
import com.jiphyeonjeon.choseongschool.client.view.ResultView;

public class ResultController {

	private ResultView view;
	private final Runnable waitingRoomAction;
	private final Runnable exitAction;

	/** 단독 화면 확인용: 버튼을 누르면 콘솔에 출력만 한다 */
	public ResultController(ResultView view) {
		this(view, () -> System.out.println("서당 마루로 이동"), () -> System.out.println("나가기"));
	}

	/**
	 * @param waitingRoomAction "서당 마루로" 버튼: 대기실 화면으로 전환 (ClientFlow에서 연결)
	 * @param exitAction        "나가기" 버튼: 서버 접속 종료 후 로그인 화면으로 (ClientFlow에서 연결)
	 */
	public ResultController(ResultView view, Runnable waitingRoomAction, Runnable exitAction) {
		this.view = view;
		this.waitingRoomAction = waitingRoomAction;
		this.exitAction = exitAction;
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
		waitingRoomAction.run();
	}

	private void onExit() {
		exitAction.run();
	}
}
