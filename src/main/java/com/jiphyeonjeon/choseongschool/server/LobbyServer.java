package com.jiphyeonjeon.choseongschool.server;

import com.jiphyeonjeon.choseongschool.model.Difficulty;
import com.jiphyeonjeon.choseongschool.network.LineMessages;
import com.jiphyeonjeon.choseongschool.common.LobbyConstants;
import com.jiphyeonjeon.choseongschool.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.model.RoomState;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** 입장·퇴장, 학칙, 시작 카운트다운을 공유하는 TCP 서버 */
public final class LobbyServer implements AutoCloseable {
    private final ServerSocket listener;
    // 동시 변경 충돌을 방지하기 위한 방 상태 변경의 순차 처리
    private final ScheduledExecutorService roomEvents = Executors.newSingleThreadScheduledExecutor();
    private final Map<Integer, Session> members = new LinkedHashMap<>();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final Thread acceptThread;
    private volatile boolean running = true;
    private int nextId = 1;
    private LobbySettings settings = LobbySettings.DEFAULT;
    private RoomState state = RoomState.WAITING;
    private long countdownId;
    private final GameEvents gameEvents;

    public LobbyServer(int port) throws IOException {
        this(InetAddress.getLoopbackAddress(), port, null);
    }

    public LobbyServer(InetAddress bindAddress, int port, GameEvents gameEvents) throws IOException {
        this.gameEvents = gameEvents;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(bindAddress, port));
        acceptThread = new Thread(this::acceptLoop, "lobby-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() { return listener.getLocalPort(); }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = listener.accept();
                sockets.add(socket);
                if (!running) { socket.close(); break; }
                Thread handler = new Thread(() -> handle(socket), "lobby-client");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                if (running) System.err.println(e.getMessage());
            }
        }
    }

    private void handle(Socket socket) {
        Session session = null;
        try {
            socket.setSoTimeout(LobbyConstants.JOIN_TIMEOUT_MS);
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            session = new Session(socket);
            String firstLine = LineMessages.read(input);
            Session current = session;
            boolean joined = roomEvents.submit(() -> join(current, firstLine)).get();
            if (!joined) return;
            socket.setSoTimeout(0);
            String line;
            while ((line = LineMessages.read(input)) != null) {
                if (line.equals("QUIT")) break;
                String command = line;
                // 방 이벤트 큐의 무제한 누적을 방지하기 위한 이전 명령 처리 대기
                roomEvents.submit(() -> processCommand(current, command)).get();
            }
        } catch (IOException | ExecutionException e) {
            // finally에서 정상 퇴장과 갑작스러운 연결 종료를 함께 처리
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RejectedExecutionException e) {
            // 서버 종료 중 새 작업이 들어온 경우
        } finally {
            Session departed = session;
            if (departed != null) {
                try { roomEvents.execute(() -> leave(departed)); }
                catch (RejectedExecutionException ignored) { }
                departed.finish();
            } else {
                try { socket.close(); } catch (IOException ignored) { }
                sockets.remove(socket);
            }
        }
    }

    // 방 상태 접근을 roomEvents 스레드로 제한
    private boolean join(Session session, String line) {
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

    private void processCommand(Session session, String line) {
        if (!members.containsKey(session.id)) return;
        if (line.equals("START")) {
            if (session.id != members.keySet().iterator().next()) commandError(session, "NOT_HOST");
            else if (state != RoomState.WAITING) commandError(session, "NOT_WAITING");
            else {
                state = RoomState.STARTING;
                tick(++countdownId, 3);
            }
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
        } else if (session.id != members.keySet().iterator().next()) {
            error = "NOT_HOST";
        } else if (state != RoomState.WAITING) {
            error = "NOT_WAITING";
        } else {
            try {
                if (fields.length != 4) throw new IllegalArgumentException();
                LobbySettings proposed = new LobbySettings(Difficulty.valueOf(fields[1]),
                        Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
                settings = proposed; // 세 값의 검증 완료 후 일괄 적용
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

    private void commandError(Session session, String error) {
        session.send("ERROR|" + error);
        session.send("SETTINGS|" + settings.fields());
    }

    private void broadcast(String line) {
        LineMessages.validate(line);
        for (Session member : members.values()) member.send(line);
    }

    private java.util.function.Consumer<String> gameBroadcast(long gameId) {
        return line -> {
            LineMessages.validate(line);
            if (!running) return;
            try {
                roomEvents.execute(() -> {
                    // 다른 스레드의 전송 요청 처리 및 이전 게임의 늦은 콜백 제외
                    if (state == RoomState.PLAYING && countdownId == gameId) broadcast(line);
                });
            } catch (RejectedExecutionException ignored) { }
        };
    }

    private void tick(long id, int number) {
        // 시작 번호를 비교해 취소된 예약 작업이 새 카운트다운에 섞이는 상황 방지
        if (!running || state != RoomState.STARTING || countdownId != id) return;
        if (number == 0) {
            state = RoomState.PLAYING;
            broadcast("GAME_START");
            if (gameEvents != null) gameEvents.onStart(members.values().stream().map(Session::info).toList(), settings, gameBroadcast(countdownId));
            return;
        }
        broadcast("COUNTDOWN|" + number);
        roomEvents.schedule(() -> tick(id, number - 1), 1, TimeUnit.SECONDS);
    }

    private boolean reject(Session session, String reason) {
        session.send("ERROR|" + reason);
        return false;
    }

    private void leave(Session session) {
        if (members.remove(session.id) != null) {
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

    private class Session {
        private final Socket socket;
        private final PrintWriter output;
        private int id;
        private String nickname;
        private String character;
        private final BlockingQueue<String> outgoing = new ArrayBlockingQueue<>(LobbyConstants.SEND_QUEUE_CAPACITY);
        private final String end = new String("END"); // 참조로 비교하는 내부 종료 표시

        Session(Socket socket) throws IOException {
            this.socket = socket;
            output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            Thread writer = new Thread(this::writeLoop, "lobby-writer");
            writer.setDaemon(true);
            writer.start();
        }

        ParticipantInfo info() { return new ParticipantInfo(Integer.toString(id), nickname, Integer.parseInt(character)); }

        void send(String line) {
            if (!outgoing.offer(line)) closeSocket();
        }

        void finish() {
            if (!outgoing.offer(end)) closeSocket();
        }

        private void writeLoop() {
            try {
                while (true) {
                    String line = outgoing.take();
                    if (line == end) break;
                    var deadline = roomEvents.schedule(this::closeSocket, LobbyConstants.WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    try {
                        output.println(line);
                        if (output.checkError()) break;
                    } finally { deadline.cancel(false); }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RejectedExecutionException ignored) {
                // 서버 종료 중 새 송신 중단
            } finally { closeSocket(); }
        }

        private void closeSocket() {
            try { socket.close(); } catch (IOException ignored) { }
            sockets.remove(socket);
            // 연결 종료나 송신 실패 시 대기 중인 writer 종료
            if (outgoing.isEmpty()) outgoing.offer(end);
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        listener.close();
        for (Socket socket : sockets) socket.close();
        roomEvents.shutdownNow();
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
