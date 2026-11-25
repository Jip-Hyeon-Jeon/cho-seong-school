package com.jiphyeonjeon.choseongschool.model;

/** 화면과 서버가 공유하는 변경 불가능한 학칙 값 */
public record LobbySettings(Difficulty difficulty, int rounds, int seconds) {
    public static final LobbySettings DEFAULT = new LobbySettings(Difficulty.LOW, 10, 30);

    public LobbySettings {
        if (difficulty == null || (rounds != 5 && rounds != 10 && rounds != 15 && rounds != 20)
                || (seconds != 15 && seconds != 30)) {
            throw new IllegalArgumentException("허용되지 않은 학칙입니다.");
        }
    }

    public String fields() { return difficulty.name() + "|" + rounds + "|" + seconds; }
}
