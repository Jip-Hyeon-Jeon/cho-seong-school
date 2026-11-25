package com.jiphyeonjeon.choseongschool.lobby.network;

import com.jiphyeonjeon.choseongschool.lobby.model.LobbyConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Objects;

/** 메시지 한 줄의 길이 제한 및 줄바꿈 검증 */
public final class LineMessages {
    private LineMessages() { }

    public static void validate(String line) {
        Objects.requireNonNull(line);
        if (line.length() > LobbyConstants.MAX_LINE_LENGTH || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("메시지 길이나 줄바꿈을 확인해 주세요.");
        }
    }

    public static String read(BufferedReader input) throws IOException {
        StringBuilder line = new StringBuilder();
        int ch;
        while ((ch = input.read()) != -1) {
            if (ch == '\n') {
                if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
                return line.toString();
            }
            if (line.length() >= LobbyConstants.MAX_LINE_LENGTH) throw new IOException("메시지가 너무 깁니다.");
            line.append((char) ch);
        }
        return line.isEmpty() ? null : line.toString();
    }
}
