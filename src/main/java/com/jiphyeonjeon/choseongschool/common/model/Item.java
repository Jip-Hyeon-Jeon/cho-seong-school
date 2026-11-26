package com.jiphyeonjeon.choseongschool.common.model;

import java.text.Normalizer;

/** 화면에 떠 있는 아이템 하나 (곶감 또는 약과) */
public class Item {

    private final ItemKind kind;	//아이템 종류: 약과, 곶감
    private final String word;		//아이템에 적힌 단어: 사자성어, 4글자 단어
    private final long endTime; 	//이 시각(ms)이 지나면 아이템은 화면 밖으로 사라짐

    public Item(ItemKind kind, String word, long endTime) {
        this.kind = kind;
        this.word = word;
        this.endTime = endTime;
    }

    // 이미 아이템이 화면 밖으로 나갔는지 확인하는 함수
    public boolean isGone() {
        return System.currentTimeMillis() > endTime;
    }

    // 사용자의 입력과 아이템의 단어가 동일한지 체크하는 함수 (맥은 분리되어 입력하기 normlize를 해줘야함)
    public boolean matches(String input) {
        return normalize(input).equals(normalize(word));
    }

    public ItemKind getKind() {
        return kind;
    }

    public String getWord() {
        return word;
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFC);
    }
}
