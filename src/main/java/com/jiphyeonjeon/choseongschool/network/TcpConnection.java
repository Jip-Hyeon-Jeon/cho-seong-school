package com.jiphyeonjeon.choseongschool.network;

import com.jiphyeonjeon.choseongschool.common.LobbyConstants;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/** 화면 구현에 의존하지 않는 단일 TCP 연결의 송수신 */
public final class TcpConnection implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader input;
    private final BufferedWriter output;
    private volatile boolean closed;
    private boolean receiving;

    public TcpConnection(String host, int port) throws IOException {
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), LobbyConstants.CONNECT_TIMEOUT_MS);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    // Consumer<String>에 전달 가능한 connection::send 메서드 참조
    public synchronized void send(String line) {
        LineMessages.validate(line);
        if (closed) throw new IllegalStateException("연결이 닫혔습니다.");
        try {
            output.write(line);
            output.newLine();
            output.flush();
        } catch (IOException e) {
            close();
            throw new UncheckedIOException(e);
        }
    }

    /** 수신 스레드에서 콜백 실행. Swing 변경은 invokeLater로 전달 */
    public synchronized void startReceiving(Consumer<String> onLine, Runnable onClosed) {
        Objects.requireNonNull(onLine);
        Objects.requireNonNull(onClosed);
        if (receiving || closed) throw new IllegalStateException("수신은 연결당 한 번만 시작합니다.");
        receiving = true;
        Thread reader = new Thread(() -> {
            try {
                String line;
                while (!closed && (line = LineMessages.read(input)) != null) {
                    onLine.accept(line);
                }
            } catch (IOException e) {
                if (!closed) System.err.println("수신 종료: " + e.getMessage());
            } finally {
                close();
                onClosed.run();
            }
        }, "tcp-receiver");
        reader.setDaemon(true);
        reader.start();
    }

    @Override
    public void close() {
        closed = true;
        try {
            socket.close(); // 대기 중인 소켓 읽기도 종료
        } catch (IOException e) {
            System.err.println("연결 정리: " + e.getMessage());
        }
    }
}
