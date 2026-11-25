package com.jiphyeonjeon.choseongschool.server;

import com.jiphyeonjeon.choseongschool.common.GameConstants.Protocol;
import com.jiphyeonjeon.choseongschool.common.LobbyConstants;
import com.jiphyeonjeon.choseongschool.common.model.Difficulty;
import com.jiphyeonjeon.choseongschool.common.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.common.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.common.model.RoomState;
import com.jiphyeonjeon.choseongschool.common.network.LineMessages;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 입장·퇴장, 학칙, 시작 카운트다운을 공유하는 TCP 서버.
 * 10주차 채팅 서버처럼 접속마다 Thread 하나를 만들고,
 * 모두가 함께 쓰는 방 상태는 synchronized로 한 번에 한 Thread만 바꾼다.
 */
public final class LobbyServer implements AutoCloseable {
    private final ServerSocket listener;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final Map<Integer, Session> members = new LinkedHashMap<>(); // 입장 순서 유지: 첫 번째가 반장
    private volatile boolean running = true;
    private int nextId = 1;
    private LobbySettings settings = LobbySettings.DEFAULT;
    private RoomState state = RoomState.WAITING;
    private long countdownId; // 취소된 카운트다운과 이전 게임의 늦은 전송을 구분
    private final GameEvents gameEvents;

    public LobbyServer(int port) throws IOException {
        this(InetAddress.getLoopbackAddress(), port, null);
    }

    public LobbyServer(InetAddress bindAddress, int port, GameEvents gameEvents) throws IOException {
        this.gameEvents = gameEvents;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(bindAddress, port));
        Thread accept = new Thread(this::acceptLoop, "lobby-accept");
        accept.setDaemon(true);
        accept.start();
    }

    public int getPort() { return listener.getLocalPort(); }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = listener.accept();
                sockets.add(socket);
                Thread client = new Thread(() -> handle(socket), "lobby-client");
                client.setDaemon(true);
                client.start();
            } catch (IOException e) {
                if (running) System.err.println(e.getMessage());
            }
        }
    }

    /** 클라이언트 한 명을 맡는 Thread. 연결이 끊기면 정상 퇴장과 같게 처리한다. */
    private void handle(Socket socket) {
        Session session = null;
        boolean joined = false;
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            session = new Session(socket);
            joined = join(session, LineMessages.read(input));
            String line;
            while (joined && (line = LineMessages.read(input)) != null) {
                if (line.equals("QUIT")) break;
                processCommand(session, line);
            }
        } catch (IOException e) {
            // 연결 끊김과 너무 긴 줄은 아래 finally에서 퇴장으로 처리
        } finally {
            if (joined) leave(session);
            try { socket.close(); } catch (IOException ignored) { }
            sockets.remove(socket);
        }
    }

    private synchronized boolean join(Session session, String line) {
        String[] parts = line == null ? new String[0] : line.split("\\|", -1);
        if (parts.length != 3 || !parts[0].equals("JOIN")) return reject(session, "BAD_JOIN");
        String nickname = parts[1].trim();
        if (nickname.isBlank() || nickname.length() > LobbyConstants.MAX_NICKNAME_LENGTH || nickname.chars().anyMatch(Character::isISOControl)) {
            return reject(session, "BAD_NICKNAME");
        }
        if (!parts[2].matches("[0-4]")) return reject(session, "BAD_CHARACTER");
        if (state != RoomState.WAITING) return reject(session, "ROOM_BUSY");
        if (members.size() >= LobbyConstants.MAX_PLAYERS) return reject(session, "ROOM_FULL");
        for (Session member : members.values()) {
            if (member.nickname.equals(nickname)) return reject(session, "DUPLICATE_NICKNAME");
        }
        session.id = nextId++;
        session.nickname = nickname;
        session.character = parts[2];
        members.put(session.id, session);
        session.send("WELCOME|" + session.id);
        broadcastPlayers();
        session.send("SETTINGS|" + settings.fields());
        return true;
    }

    private synchronized void processCommand(Session session, String line) {
        if (!members.containsKey(session.id)) return;
        if (line.equals("START")) {
            if (!isHost(session)) commandError(session, "NOT_HOST");
            else if (state != RoomState.WAITING) commandError(session, "NOT_WAITING");
            else startCountdown();
            return;
        }
        String[] fields = line.split("\\|", -1);
        if (fields[0].equals("ANSWER")) {
            if (state != RoomState.PLAYING) commandError(session, "NOT_PLAYING");
            else if (fields.length != 2 || fields[1].isBlank()
                    || fields[1].chars().anyMatch(Character::isISOControl)) commandError(session, "BAD_ANSWER");
            else if (gameEvents == null) commandError(session, "GAME_NOT_CONNECTED");
            else gameEvents.onAnswer(session.info(), fields[1].trim(), gameBroadcast(countdownId));
            return;
        }
        String error = null;
        if (!fields[0].equals("SET_SETTINGS")) {
            error = "UNSUPPORTED_COMMAND";
        } else if (!isHost(session)) {
            error = "NOT_HOST";
        } else if (state != RoomState.WAITING) {
            error = "NOT_WAITING";
        } else {
            try {
                if (fields.length != 4) throw new IllegalArgumentException();
                settings = new LobbySettings(Difficulty.valueOf(fields[1]),
                        Integer.parseInt(fields[2]), Integer.parseInt(fields[3])); // 세 값 검증 후 일괄 적용
            } catch (IllegalArgumentException e) {
                error = "BAD_SETTINGS";
            }
        }
        if (error != null) {
            commandError(session, error);
            return;
        }
        for (Session member : members.values()) member.send("SETTINGS|" + settings.fields());
    }

    private boolean isHost(Session session) {
        return session.id == members.keySet().iterator().next();
    }

    private void commandError(Session session, String error) {
        session.send("ERROR|" + error);
        session.send("SETTINGS|" + settings.fields());
    }

    private boolean reject(Session session, String reason) {
        session.send("ERROR|" + reason);
        return false;
    }

    /** 3 → 2 → 1을 1초 간격으로 알리고 게임을 시작한다. 중간에 취소되면 번호가 달라 멈춘다. */
    private void startCountdown() {
        state = RoomState.STARTING;
        long id = ++countdownId;
        Thread countdown = new Thread(() -> runCountdown(id), "lobby-countdown");
        countdown.setDaemon(true);
        countdown.start();
    }

    private void runCountdown(long id) {
        for (int number = 3; number >= 1; number--) {
            synchronized (this) {
                if (!isCountingDown(id)) return;
                broadcast("COUNTDOWN|" + number);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
        synchronized (this) {
            if (!isCountingDown(id)) return;
            state = RoomState.PLAYING;
            broadcast("GAME_START");
            if (gameEvents != null) {
                gameEvents.onStart(members.values().stream().map(Session::info).toList(), settings, gameBroadcast(id));
            }
        }
    }

    private boolean isCountingDown(long id) {
        return running && state == RoomState.STARTING && countdownId == id;
    }

    /** 게임 로직이 방 전체에 보내는 통로. 이전 게임의 늦은 전송은 버린다. */
    private Consumer<String> gameBroadcast(long gameId) {
        return line -> {
            LineMessages.validate(line);
            synchronized (this) {
                if (!running || state != RoomState.PLAYING || countdownId != gameId) return;
                broadcast(line);
                if (line.equals(Protocol.GAME_END) || line.startsWith(Protocol.GAME_END + Protocol.SEPARATOR)) {
                    endGame();
                }
            }
        };
    }

    /** 게임 로직이 GAME_END를 보내면 방을 대기 상태로 되돌려 다시 시작·입장할 수 있게 한다 */
    private void endGame() {
        state = RoomState.WAITING;
        countdownId++; // 끝난 게임이 늦게 보내는 메시지는 무시
        for (Session member : members.values()) member.send("SETTINGS|" + settings.fields());
    }

    private void broadcast(String line) {
        LineMessages.validate(line);
        for (Session member : members.values()) member.send(line);
    }

    private synchronized void leave(Session session) {
        if (members.remove(session.id) == null) return;
        if (state == RoomState.PLAYING && gameEvents != null) gameEvents.onLeave(session.info());
        boolean cancel = state == RoomState.STARTING;
        if (cancel || members.isEmpty()) {
            countdownId++;
            state = RoomState.WAITING;
        }
        if (members.isEmpty()) {
            settings = LobbySettings.DEFAULT;
            if (gameEvents != null) gameEvents.onRoomEmpty();
        }
        broadcastPlayers();
        if (cancel) broadcast("START_CANCELLED|PLAYER_LEFT");
    }

    private void broadcastPlayers() {
        if (members.isEmpty()) return;
        int hostId = members.keySet().iterator().next();
        StringBuilder line = new StringBuilder("PLAYERS|" + hostId + "|" + members.size());
        for (Session member : members.values()) {
            line.append('|').append(member.id).append('|').append(member.nickname).append('|').append(member.character);
        }
        for (Session member : members.values()) member.send(line.toString());
    }

    /** 접속자 한 명의 정보와 보내기용 PrintWriter */
    private static class Session {
        private final PrintWriter output;
        private int id;
        private String nickname;
        private String character;

        Session(Socket socket) throws IOException {
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        ParticipantInfo info() { return new ParticipantInfo(Integer.toString(id), nickname, Integer.parseInt(character)); }

        void send(String line) { output.println(line); }
    }

    @Override
    public void close() throws IOException {
        running = false;
        listener.close();
        for (Socket socket : sockets) socket.close();
    }

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? LobbyConstants.DEFAULT_PORT : Integer.parseInt(args[0]);
        InetAddress bindAddress = InetAddress.getByName(args.length > 1 ? args[1] : "127.0.0.1");
        try (LobbyServer server = new LobbyServer(bindAddress, port, null)) {
            System.out.println("대기실 서버: " + bindAddress.getHostAddress() + ":" + server.getPort() + " (Enter로 종료)");
            new BufferedReader(new InputStreamReader(System.in)).readLine();
        }
    }
}
