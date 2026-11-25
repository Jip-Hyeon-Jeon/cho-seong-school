package com.jiphyeonjeon.choseongschool.controller;

import com.jiphyeonjeon.choseongschool.client.GameScreenListener;
import com.jiphyeonjeon.choseongschool.common.LobbyConstants;
import com.jiphyeonjeon.choseongschool.model.Difficulty;
import com.jiphyeonjeon.choseongschool.model.GameSession;
import com.jiphyeonjeon.choseongschool.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.model.RoomState;
import com.jiphyeonjeon.choseongschool.view.LobbyView;

import java.util.*;
import javax.swing.SwingUtilities;

/** 서버 메시지 검증과 화면 갱신을 담당하는 컨트롤러 */
public final class NetworkLobbyController {
    private final LobbyView view;
    private String localId;
    private boolean closed;
    private String endMessage = "서버 연결이 종료되었습니다.";
    private String commandError;
    private LobbySettings confirmedSettings;
    private final GameScreenListener gameScreen;
    private boolean gameStarted;

    public NetworkLobbyController(LobbyView view) {
        this(view, session -> { });
    }

    public NetworkLobbyController(LobbyView view, GameScreenListener gameScreen) {
        this.view = view;
        this.gameScreen = Objects.requireNonNull(gameScreen);
    }

    // TcpConnection에 전달하는 수신 콜백: controller::handle
    public void handle(String line) {
        SwingUtilities.invokeLater(() -> apply(line));
    }

    private void apply(String line) {
        if (closed || line == null) return;
        String[] fields = line.split("\\|", -1);
        try {
            switch (fields[0]) {
                case "WELCOME" -> {
                    if (fields.length != 2 || localId != null) return;
                    requireId(fields[1]);
                    localId = fields[1];
                }
                case "PLAYERS" -> {
                    if (localId == null || fields.length < 3) return;
                    requireId(fields[1]);
                    int count = Integer.parseInt(fields[2]);
                    if (count < 1 || count > LobbyConstants.MAX_PLAYERS || fields.length != 3 + count * 3) return;
                    List<ParticipantInfo> members = new ArrayList<>();
                    Set<String> ids = new HashSet<>();
                    Set<String> names = new HashSet<>();
                    for (int i = 0; i < count; i++) {
                        int offset = 3 + i * 3;
                        String id = fields[offset];
                        requireId(id);
                        String name = fields[offset + 1];
                        if (!ids.add(id) || !names.add(name)
                                || name.chars().anyMatch(Character::isISOControl)) return;
                        members.add(new ParticipantInfo(id, name, Integer.parseInt(fields[offset + 2])));
                    }
                    // 전체 참가자 정보의 검증 완료 후 화면 갱신
                    if (!ids.contains(localId) || !ids.contains(fields[1])) return;
                    view.showRoster(localId, fields[1], members);
                    if (gameStarted) gameScreen.onPlayers(List.copyOf(members));
                }
                case "SETTINGS" -> {
                    if (localId == null || fields.length != 4) return;
                    LobbySettings settings = new LobbySettings(Difficulty.valueOf(fields[1]),
                            Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
                    confirmedSettings = settings;
                    view.showSettings(settings);
                    if (commandError != null) {
                        view.showCommandError(commandError);
                        commandError = null;
                    }
                }
                case "COUNTDOWN" -> {
                    if (localId == null || fields.length != 2 || view.getParticipants().isEmpty()
                            || view.getViewState() == RoomState.PLAYING) return;
                    int number = Integer.parseInt(fields[1]);
                    // 동일 연결에서 받은 3 → 2 → 1 순서 검증
                    int expected = view.getViewState() == RoomState.WAITING ? 3 : view.getCountdownNumber() - 1;
                    if (number != expected || number < 1) return;
                    view.showCountdown(number);
                }
                case "START_CANCELLED" -> {
                    if (fields.length == 2 && fields[1].equals("PLAYER_LEFT")
                            && view.getViewState() == RoomState.STARTING) view.showStartCancelled();
                }
                case "GAME_START" -> {
                    if (fields.length == 1 && view.getViewState() == RoomState.STARTING
                            && view.getCountdownNumber() == 1 && confirmedSettings != null && !gameStarted) {
                        GameSession session = new GameSession(localId, view.getHostId(), view.getParticipants(), confirmedSettings);
                        view.showPlaying();
                        gameStarted = true;
                        gameScreen.onStart(session);
                    }
                }
                case "QUESTION", "HINT", "ANSWER" -> {
                    if (gameStarted) gameScreen.onMessage(line);
                }
                case "ERROR" -> {
                    if (fields.length != 2) return;
                    if (localId != null) {
                        commandError = switch (fields[1]) {
                            case "NOT_HOST" -> "학칙 변경과 과거 시작은 반장만 할 수 있습니다.";
                            case "NOT_WAITING" -> "이미 시작 중이거나 과거가 진행 중입니다.";
                            case "BAD_SETTINGS" -> "허용되지 않은 학칙입니다. 기존 학칙을 유지합니다.";
                            case "BAD_ANSWER" -> "답안에 구분자나 제어 문자를 사용할 수 없습니다.";
                            case "GAME_NOT_CONNECTED" -> "게임 서버가 아직 연결되지 않았습니다.";
                            case "NOT_PLAYING" -> "게임이 시작된 뒤 답을 제출해 주세요.";
                            default -> "요청을 처리하지 못했습니다. 기존 학칙을 유지합니다.";
                        };
                        view.showCommandError(commandError);
                        if (gameStarted) gameScreen.onError(commandError);
                        return; // 명령 거부 시 연결 유지
                    }
                    endMessage = switch (fields[1]) {
                        case "DUPLICATE_NICKNAME" -> "이미 사용 중인 이름입니다.";
                        case "ROOM_FULL" -> "학동이 5명이라 입장할 수 없습니다.";
                        case "ROOM_BUSY" -> "이미 과거를 시작한 서당에는 입장할 수 없습니다.";
                        case "BAD_NICKNAME" -> "이름은 1~8자로 입력하고 | 및 제어 문자를 제외해 주세요.";
                        case "BAD_CHARACTER" -> "캐릭터 번호를 확인해 주세요.";
                        default -> "서버가 입장 요청을 처리하지 못했습니다.";
                    };
                    closed = true;
                    view.showConnectionClosed(endMessage);
                }
                default -> { } // 이 모듈에 정의되지 않은 메시지 제외
            }
        } catch (IllegalArgumentException e) {
            // 잘못된 숫자나 참가자 정보 수신 시 기존 화면 보존
        }
    }

    private static void requireId(String id) {
        if (Integer.parseInt(id) <= 0) throw new IllegalArgumentException("ID must be positive");
    }

    public void onClosed() {
        SwingUtilities.invokeLater(() -> {
            if (closed) return;
            closed = true;
            view.showConnectionClosed(endMessage);
            if (gameStarted) gameScreen.onDisconnected(endMessage);
        });
    }

    public void onConnectFailed() {
        SwingUtilities.invokeLater(() -> {
            closed = true;
            view.showConnectionClosed("서버에 연결하지 못했습니다. 서버 실행과 주소를 확인해 주세요.");
        });
    }

    public void onSendFailed() {
        SwingUtilities.invokeLater(() -> {
            if (closed) return;
            closed = true;
            endMessage = "요청을 보내지 못했습니다. 다시 접속해 주세요.";
            view.showConnectionClosed(endMessage);
            if (gameStarted) gameScreen.onDisconnected(endMessage);
        });
    }
}
