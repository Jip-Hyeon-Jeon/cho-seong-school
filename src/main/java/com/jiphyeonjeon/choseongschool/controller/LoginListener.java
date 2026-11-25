package com.jiphyeonjeon.choseongschool.controller;

/** 로그인 입력 검증을 통과했을 때 다음 화면(대기실)으로 넘기는 콜백 */
@FunctionalInterface
public interface LoginListener {

    /**
     * @param serverIp       서버 주소
     * @param nickname       닉네임 (1~8자)
     * @param characterIndex 캐릭터 번호 0~4 (세종대왕, 신사임당, 율곡이이, 퇴계이황, 이순신 순서)
     */
    void onLogin(String serverIp, String nickname, int characterIndex);
}
