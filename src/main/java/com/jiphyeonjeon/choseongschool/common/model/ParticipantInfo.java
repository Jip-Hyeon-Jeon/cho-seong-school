package com.jiphyeonjeon.choseongschool.common.model;

import com.jiphyeonjeon.choseongschool.common.LobbyConstants;

/** 소켓을 포함하지 않는 참가자 표시 정보 */
public record ParticipantInfo(String id, String nickname, int character) {
    public ParticipantInfo {
        if (id == null || id.isBlank() || nickname == null || nickname.isBlank()
                || nickname.length() > LobbyConstants.MAX_NICKNAME_LENGTH || nickname.contains("|")
                || nickname.chars().anyMatch(Character::isISOControl)
                || character < 0 || character >= LobbyConstants.CHARACTER_COUNT) {
            throw new IllegalArgumentException("올바르지 않은 참가자 정보입니다.");
        }
    }
}
