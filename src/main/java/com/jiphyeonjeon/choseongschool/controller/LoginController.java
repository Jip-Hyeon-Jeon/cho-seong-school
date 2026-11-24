package com.jiphyeonjeon.choseongschool.controller;

import com.jiphyeonjeon.choseongschool.view.LoginView;

import javax.swing.*;

public class LoginController {

    private final LoginView view;

    // 캐릭터 목록
    private final String[] characters = {"세종대왕", "신사임당", "율곡이이", "퇴계이황", "정약용"};
    private int currentIndex = 0;

    public LoginController(LoginView view) {
        this.view = view;
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
        String selectedCharacter = characters[currentIndex];

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

        // 검증 통과 시 테스트 출력 메시지
        System.out.println("입장 시도 -> IP: " + ip + ", 닉네임: " + nickname + ", 캐릭터: " + selectedCharacter);
    }
}