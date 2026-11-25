package com.jiphyeonjeon.choseongschool.client.controller;

import com.jiphyeonjeon.choseongschool.common.GameConstants.Protocol;
import com.jiphyeonjeon.choseongschool.client.GameScreenListener;
import com.jiphyeonjeon.choseongschool.common.LobbyConstants;
import com.jiphyeonjeon.choseongschool.common.model.Difficulty;
import com.jiphyeonjeon.choseongschool.client.model.GameSession;
import com.jiphyeonjeon.choseongschool.common.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.client.model.LobbyState;
import com.jiphyeonjeon.choseongschool.common.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.common.model.RoomState;
import com.jiphyeonjeon.choseongschool.client.view.LobbyView;

import java.util.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * 대기실 컨트롤러: 서버 메시지와 버튼 요청을 받아 대기실 상태(LobbyState)를 바꾸고 화면에 다시 그리게 한다.
 * 게임이 시작되면 게임 관련 메시지는 GameScreenListener로 넘긴다.
 */
public final class NetworkLobbyController {
    private final LobbyView view;
    private final LobbyState state = new LobbyState();
    private final GameScreenListener gameScreen;
    private final Consumer<String> sender;
    private String endMessage = "서버 연결이 종료되었습니다.";

    public NetworkLobbyController(LobbyView view) {
        this(view, session -> { });
    }

    public NetworkLobbyController(LobbyView view, GameScreenListener gameScreen) {
        this(view, gameScreen, line -> { });
    }

    /** @param sender 서버로 한 줄 보내는 함수 (LobbyClient::send) */
    public NetworkLobbyController(LobbyView view, GameScreenListener gameScreen, Consumer<String> sender) {
        this.view = view;
        this.gameScreen = Objects.requireNonNull(gameScreen);
        this.sender = Objects.requireNonNull(sender);
        view.setSettingsRequest(this::requestSettings);
        view.setStartRequest(this::requestStart);
    }

    /** 대기실 상태 (읽기용). Swing 스레드에서만 읽는다 */
    public LobbyState state() {
        return state;
    }

    // ===================== 버튼 요청 (Swing 스레드) =====================

    /** 반장이 학칙 버튼을 누름: 바꿀 수 있을 때만 서버로 보내고 확정을 기다린다 */
    private void requestSettings(LobbySettings proposed) {
        if (state.requestSettings()) sender.accept("SET_SETTINGS|" + proposed.fields());
        view.render(state);
    }

    /** 반장이 시작 버튼을 누름 */
    private void requestStart() {
        if (state.requestStart()) sender.accept("START");
        view.render(state);
    }

    // ===================== 서버 메시지 (수신 스레드 → Swing 스레드) =====================

    // TcpConnection에 전달하는 수신 콜백: controller::handle
    public void handle(String line) {
        SwingUtilities.invokeLater(() -> apply(line));
    }

    private void apply(String line) {
        if (!state.isConnected() || line == null) return;
        String[] fields = line.split("\\|", -1);
        try {
            switch (fields[0]) {
                case "WELCOME" -> {
                    if (fields.length != 2) return;
                    requireId(fields[1]);
                    state.welcome(fields[1]);
                }
                case "PLAYERS" -> {
                    List<ParticipantInfo> members = parsePlayers(fields);
                    if (members == null || !state.updateRoster(fields[1], members)) return;
                    view.render(state);
                    if (isPlaying()) gameScreen.onPlayers(state.participants());
                }
                case "SETTINGS" -> {
                    if (state.localId() == null || fields.length != 4) return;
                    state.confirmSettings(new LobbySettings(Difficulty.valueOf(fields[1]),
                            Integer.parseInt(fields[2]), Integer.parseInt(fields[3])));
                    view.render(state);
                }
                case "COUNTDOWN" -> {
                    if (fields.length == 2 && state.countdown(Integer.parseInt(fields[1]))) view.render(state);
                }
                case "START_CANCELLED" -> {
                    if (fields.length == 2 && fields[1].equals("PLAYER_LEFT")
                            && state.cancelStart("학동이 나가 시작을 취소했습니다. 다시 시작할 수 있습니다.")) {
                        view.render(state);
                    }
                }
                case "GAME_START" -> {
                    if (fields.length != 1) return;
                    GameSession session = state.startGame();
                    if (session == null) return;
                    view.render(state);
                    gameScreen.onStart(session);
                }
                case "ERROR" -> {
                    if (fields.length != 2) return;
                    if (state.localId() != null) {
                        String message = commandErrorMessage(fields[1]);
                        state.showNotice(message);
                        view.render(state);
                        if (isPlaying()) gameScreen.onError(message);
                        return; // 명령 거부 시 연결 유지
                    }
                    endMessage = joinErrorMessage(fields[1]);
                    close(endMessage);
                }
                // 대기실 메시지가 아니면 게임 메시지: 게임 중에만 게임 화면으로 그대로 전달
                // (QUESTION, TIME, SCORE, GAME_END 등의 해석은 게임 화면 컨트롤러가 맡는다)
                default -> {
                    if (!isPlaying()) return;
                    gameScreen.onMessage(line);
                    if (fields[0].equals(Protocol.GAME_END)) {
                        // 서버도 방을 대기 상태로 되돌리므로 다음 카운트다운·시작을 받을 수 있게 초기화
                        state.endGame();
                        view.render(state);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            // 잘못된 숫자나 참가자 정보 수신 시 기존 화면 보존
        }
    }

    /** PLAYERS|반장ID|인원|ID|이름|캐릭터|... → 참가자 목록. 형식이 틀리면 null */
    private static List<ParticipantInfo> parsePlayers(String[] fields) {
        if (fields.length < 3) return null;
        requireId(fields[1]);
        int count = Integer.parseInt(fields[2]);
        if (count < 1 || count > LobbyConstants.MAX_PLAYERS || fields.length != 3 + count * 3) return null;
        List<ParticipantInfo> members = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int offset = 3 + i * 3;
            String id = fields[offset];
            requireId(id);
            String name = fields[offset + 1];
            if (!ids.add(id) || !names.add(name) || name.chars().anyMatch(Character::isISOControl)) return null;
            members.add(new ParticipantInfo(id, name, Integer.parseInt(fields[offset + 2])));
        }
        return members;
    }

    private static String commandErrorMessage(String code) {
        return switch (code) {
            case "NOT_HOST" -> "학칙 변경과 과거 시작은 반장만 할 수 있습니다.";
            case "NOT_WAITING" -> "이미 시작 중이거나 과거가 진행 중입니다.";
            case "BAD_SETTINGS" -> "허용되지 않은 학칙입니다. 기존 학칙을 유지합니다.";
            case "BAD_ANSWER" -> "답안에 구분자나 제어 문자를 사용할 수 없습니다.";
            case "GAME_NOT_CONNECTED" -> "게임 서버가 아직 연결되지 않았습니다.";
            case "NOT_PLAYING" -> "게임이 시작된 뒤 답을 제출해 주세요.";
            default -> "요청을 처리하지 못했습니다. 기존 학칙을 유지합니다.";
        };
    }

    private static String joinErrorMessage(String code) {
        return switch (code) {
            case "DUPLICATE_NICKNAME" -> "이미 사용 중인 이름입니다.";
            case "ROOM_FULL" -> "학동이 5명이라 입장할 수 없습니다.";
            case "ROOM_BUSY" -> "이미 과거를 시작한 서당에는 입장할 수 없습니다.";
            case "BAD_NICKNAME" -> "이름은 1~8자로 입력하고 | 및 제어 문자를 제외해 주세요.";
            case "BAD_CHARACTER" -> "캐릭터 번호를 확인해 주세요.";
            default -> "서버가 입장 요청을 처리하지 못했습니다.";
        };
    }

    private static void requireId(String id) {
        if (Integer.parseInt(id) <= 0) throw new IllegalArgumentException("ID must be positive");
    }

    private boolean isPlaying() {
        return state.phase() == RoomState.PLAYING;
    }

    /** 연결 종료를 상태와 화면에 반영하고, 그때 게임 중이었는지 돌려준다 */
    private boolean close(String message) {
        boolean wasPlaying = isPlaying();
        state.close(message);
        view.render(state);
        return wasPlaying;
    }

    // ===================== 연결 이벤트 (LobbyClient가 호출) =====================

    public void onClosed() {
        SwingUtilities.invokeLater(() -> {
            if (!state.isConnected()) return;
            if (close(endMessage)) gameScreen.onDisconnected(endMessage);
        });
    }

    public void onConnectFailed() {
        SwingUtilities.invokeLater(() -> close("서버에 연결하지 못했습니다. 서버 실행과 주소를 확인해 주세요."));
    }

    public void onSendFailed() {
        SwingUtilities.invokeLater(() -> {
            if (!state.isConnected()) return;
            endMessage = "요청을 보내지 못했습니다. 다시 접속해 주세요.";
            if (close(endMessage)) gameScreen.onDisconnected(endMessage);
        });
    }
}
