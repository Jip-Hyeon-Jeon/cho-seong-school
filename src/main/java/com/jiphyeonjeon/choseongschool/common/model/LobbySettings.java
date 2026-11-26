package com.jiphyeonjeon.choseongschool.common.model;

/** 화면과 서버가 공유하는 변경 불가능한 학칙 값 */
public record LobbySettings(Difficulty difficulty, int rounds, int seconds, GameMode mode) {
    public static final LobbySettings DEFAULT = new LobbySettings(Difficulty.LOW, 10, 30, GameMode.NORMAL);

    public LobbySettings {
        if (difficulty == null || mode == null || (rounds != 5 && rounds != 10 && rounds != 15 && rounds != 20)
                || (seconds != 15 && seconds != 30)) {
            throw new IllegalArgumentException("허용되지 않은 학칙입니다.");
        }
    }

    /** 방식을 정하지 않으면 일반전 */
    public LobbySettings(Difficulty difficulty, int rounds, int seconds) {
        this(difficulty, rounds, seconds, GameMode.NORMAL);
    }

    /** 메시지용 값: 난이도|문제수|초|방식 */
    public String fields() { return difficulty.name() + "|" + rounds + "|" + seconds + "|" + mode.name(); }
}
