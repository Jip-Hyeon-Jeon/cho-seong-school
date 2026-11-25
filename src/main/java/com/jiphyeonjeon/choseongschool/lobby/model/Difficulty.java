package com.jiphyeonjeon.choseongschool.lobby.model;

/** 하·중·상으로 제한한 난이도 선택지 */
public enum Difficulty {
    LOW("하"),
    MEDIUM("중"),
    HIGH("상");

    private final String label;

    private Difficulty(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
