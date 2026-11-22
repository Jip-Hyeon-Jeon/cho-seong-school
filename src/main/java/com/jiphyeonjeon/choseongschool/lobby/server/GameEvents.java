package com.jiphyeonjeon.choseongschool.lobby.server;

import com.jiphyeonjeon.choseongschool.lobby.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.lobby.model.ParticipantInfo;

import java.util.List;
import java.util.function.Consumer;

/** 방 이벤트 스레드에서 호출하는 출제·정답 로직 연결점. 긴 대기 작업 금지 */
public interface GameEvents {
    void onStart(List<ParticipantInfo> players, LobbySettings settings, Consumer<String> broadcast);
    void onAnswer(ParticipantInfo player, String answer, Consumer<String> broadcast);
    default void onLeave(ParticipantInfo player) { }
    default void onRoomEmpty() { }
}
