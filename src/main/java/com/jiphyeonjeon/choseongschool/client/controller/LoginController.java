package com.jiphyeonjeon.choseongschool.client.controller;

import com.jiphyeonjeon.choseongschool.client.view.LoginView;

import javax.swing.*;

public class LoginController {

    private final LoginView view;
    private final LoginListener loginListener;

    // 캐릭터 목록 (번호 = 대기실 초상 파일 ch번호 = 게임 화면 Avatar 순서)
    private final String[] characters = {"세종대왕", "퇴계이황", "율곡이이", "신사임당", "이순신"};
    private int currentIndex = 0;

    /** 단독 화면 테스트용: 검증 통과 시 콘솔에 출력만 한다 */
    public LoginController(LoginView view) {
        this(view, (ip, nickname, characterIndex) -> System.out.println(
                "입장 시도 -> IP: " + ip + ", 닉네임: " + nickname + ", 캐릭터 번호: " + characterIndex));
    }

    /** 검증 통과 시 loginListener로 접속 정보를 넘긴다 (클라이언트 메인에서 대기실 연결) */
    public LoginController(LoginView view, LoginListener loginListener) {
        this.view = view;
        this.loginListener = loginListener;
        initListeners();
    }

    private void initListeners() {
        // 1. 이전 캐릭터 버튼 (◀) 이벤트 연결
        view.getPrevCharButton().addActionListener(e -> handlePrevCharacter());

        // 2. 다음 캐릭터 버튼 (▶) 이벤트 연결
        view.getNextCharButton().addActionListener(e -> handleNextCharacter());

        // 3. 서당 입학 버튼 이벤트 연결
        view.getEnterButton().addActionListener(e -> handleLogin());
    }

    private void handlePrevCharacter() {

        currentIndex = (currentIndex - 1 + characters.length) % characters.length;
        view.updateCharacterDisplay(characters[currentIndex], currentIndex, characters.length);
    }

    private void handleNextCharacter() {

        currentIndex = (currentIndex + 1) % characters.length;
        view.updateCharacterDisplay(characters[currentIndex], currentIndex, characters.length);
    }

    private void handleLogin() {
        String ip = view.getServerIp();
        String nickname = view.getNickname();
        if (ip.isEmpty()) {
            JOptionPane.showMessageDialog(view, "서버 IP를 입력해 주세요.");
            return;
        }
        if (nickname.isEmpty()) {
            JOptionPane.showMessageDialog(view, "닉네임을 입력해 주세요.");
            return;
        } else if (nickname.length()>8) {
            JOptionPane.showMessageDialog(view, "닉네임은 8자 이하로 입력해 주세요.");
            return;
        }
        // - ip가 비어있으면 JOptionPane.showMessageDialog로 알림 후 return
        // - nickname이 비어있으면 알림 후 return
        // - nickname이 8자를 초과하면 알림 후 return

        // 검증 통과 시 대기실 연결 담당에게 접속 정보 전달
        loginListener.onLogin(ip, nickname, currentIndex);
    }
}