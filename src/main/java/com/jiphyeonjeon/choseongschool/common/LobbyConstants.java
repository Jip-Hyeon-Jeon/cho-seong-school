package com.jiphyeonjeon.choseongschool.common;

/** 대기실 클라이언트·서버 공용 상수. 화면 크기는 GameConstants.Screen 참조 */
public final class LobbyConstants {
    public static final int MAX_PLAYERS = 5;
    public static final int MAX_NICKNAME_LENGTH = 8;
    public static final int CHARACTER_COUNT = 5;
    public static final int DEFAULT_PORT = 50024;
    public static final int MAX_LINE_LENGTH = 4096;
    public static final int CONNECT_TIMEOUT_MS = 3000;
    public static final int JOIN_TIMEOUT_MS = 5000;
    public static final int WRITE_TIMEOUT_MS = 3000;
    public static final int SEND_QUEUE_CAPACITY = 128;
    private LobbyConstants() { }
}
