package com.jiphyeonjeon.choseongschool.common.model;

// 게임 방식을 enum으로 관리
public enum GameMode {
    NORMAL("일반전"),
    ITEM("묘수전");	//아이템전

    private final String label;

    private GameMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
