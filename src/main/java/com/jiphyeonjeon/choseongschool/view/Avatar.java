package com.jiphyeonjeon.choseongschool.view;

/** 학동 캐릭터 5종 (돈에 그려진 인물). 그림은 resources/images/game/char_이름.png */
public enum Avatar {
    SEJONG("char_sejong"),
    TOEGYE("char_toegye"),
    YULGOK("char_yulgok"),
    SAIMDANG("char_saimdang"),
    CHUNGMUGONG("char_chungmugong");

    private final String imageName;

    Avatar(String imageName) {
        this.imageName = imageName;
    }

    public String imageName() {
        return imageName;
    }
}
