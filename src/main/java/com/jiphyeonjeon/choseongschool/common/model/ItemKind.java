package com.jiphyeonjeon.choseongschool.common.model;

// 아이템 종류를 enum으로 관리
public enum ItemKind {
    // 아이템을 획득한 사용자와 1등의 점수를 바꾼다. 등장 확률은 GameConstants.Item.GOTGAM_RATIO에서 조정 가능
    GOTGAM("곶감"),
    
    // 아이템을 획득한 사용자를 제외한 나머지 사용자들의 입력을 일정 시간 동안 막는다. 막는 시간은 GameConstants.Item.BLOCK_SECONDS에서 조정 가능
    YAKGWA("약과");

    private final String label;

    private ItemKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
