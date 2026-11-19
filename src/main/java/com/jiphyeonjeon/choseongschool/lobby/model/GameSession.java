package com.jiphyeonjeon.choseongschool.lobby.model;

import java.util.List;

/** 게임 시작 시점의 참가자 목록과 학칙을 보존하는 불변 데이터 */
public record GameSession(String localId, String hostId,
                          List<ParticipantInfo> players, LobbySettings settings) {
    public GameSession {
        players = List.copyOf(players);
        if (settings == null || players.stream().noneMatch(p -> p.id().equals(localId))
                || players.stream().noneMatch(p -> p.id().equals(hostId))) {
            throw new IllegalArgumentException("게임 시작 정보에 본인, 반장, 학칙이 필요합니다.");
        }
    }
}
