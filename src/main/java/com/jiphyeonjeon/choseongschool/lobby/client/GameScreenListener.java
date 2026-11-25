package com.jiphyeonjeon.choseongschool.lobby.client;

import com.jiphyeonjeon.choseongschool.lobby.model.GameSession;
import com.jiphyeonjeon.choseongschool.lobby.model.ParticipantInfo;

import java.util.List;

/** 기존 소켓을 유지하며 Swing 이벤트 스레드에서 실행하는 게임 화면 콜백 */
public interface GameScreenListener {
    void onStart(GameSession session);
    default void onMessage(String line) { }
    default void onPlayers(List<ParticipantInfo> players) { }
    default void onDisconnected(String message) { }
    default void onError(String message) { }
}
