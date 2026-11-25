package com.jiphyeonjeon.choseongschool.client.model;

import com.jiphyeonjeon.choseongschool.common.LobbyConstants;
import com.jiphyeonjeon.choseongschool.common.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.common.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.common.model.RoomState;

import java.util.List;

/**
 * 클라이언트가 보는 대기실 상태 (MVC의 Model).
 * 서버 메시지와 버튼 요청을 받은 컨트롤러만 바꾸고, 화면(LobbyView)은 이 값을 그리기만 한다.
 * Swing 이벤트 스레드에서만 사용한다.
 */
public final class LobbyState {
    private List<ParticipantInfo> participants = List.of();
    private String localId;
    private String hostId;
    private boolean connected = true;
    private LobbySettings settings = LobbySettings.DEFAULT;
    private boolean settingsReceived;
    private boolean settingsPending;
    private boolean startPending;
    private RoomState phase = RoomState.WAITING;
    private int countdownNumber;
    /** 화면 상단 안내 대신 보여 줄 알림 (오류, 시작 취소, 연결 종료). 없으면 null */
    private String notice;

    // ===================== 읽기 (화면·다른 담당자용) =====================

    public List<ParticipantInfo> participants() { return participants; }
    public String localId() { return localId; }
    public String hostId() { return hostId; }
    public boolean isConnected() { return connected; }
    public LobbySettings settings() { return settings; }
    public boolean isSettingsPending() { return settingsPending; }
    public RoomState phase() { return phase; }
    public int countdownNumber() { return countdownNumber; }
    public String notice() { return notice; }

    /** 내가 반장인가 */
    public boolean isHost() {
        return connected && localId != null && localId.equals(hostId);
    }

    /** 반장이고, 대기 중이며, 서버가 확정한 학칙을 받았고, 이전 요청의 응답을 기다리는 중이 아닐 때 */
    public boolean canEditSettings() {
        return isHost() && phase == RoomState.WAITING && settingsReceived && !settingsPending && !startPending;
    }

    public boolean canStart() {
        return canEditSettings();
    }

    // ===================== 변경 (컨트롤러만 호출) =====================

    /** WELCOME: 서버가 정해 준 내 번호. 한 번만 받는다 */
    public boolean welcome(String id) {
        if (localId != null) return false;
        localId = id;
        return true;
    }

    /** PLAYERS: 본인과 반장이 목록에 있고 번호가 겹치지 않을 때만 반영 */
    public boolean updateRoster(String newHostId, List<ParticipantInfo> members) {
        if (localId == null || members.isEmpty() || members.size() > LobbyConstants.MAX_PLAYERS
                || members.stream().noneMatch(p -> p.id().equals(localId))
                || members.stream().noneMatch(p -> p.id().equals(newHostId))
                || members.stream().map(ParticipantInfo::id).distinct().count() != members.size()) {
            return false;
        }
        participants = List.copyOf(members);
        hostId = newHostId;
        notice = null;
        return true;
    }

    /** SETTINGS: 서버가 확정한 학칙. 요청 대기 상태를 푼다 */
    public void confirmSettings(LobbySettings confirmed) {
        settings = confirmed;
        settingsReceived = true;
        settingsPending = false;
        startPending = false;
    }

    /** 반장의 학칙 변경 요청. 보낼 수 있으면 응답 대기 상태가 된다 */
    public boolean requestSettings() {
        if (!canEditSettings()) return false;
        settingsPending = true;
        return true;
    }

    /** 반장의 시작 요청. 보낼 수 있으면 응답 대기 상태가 된다 */
    public boolean requestStart() {
        if (!canStart()) return false;
        startPending = true;
        return true;
    }

    /** COUNTDOWN: 대기 중이면 3, 시작 중이면 직전 숫자보다 1 작은 숫자만 받는다 */
    public boolean countdown(int number) {
        if (localId == null || participants.isEmpty() || phase == RoomState.PLAYING) return false;
        int expected = phase == RoomState.WAITING ? 3 : countdownNumber - 1;
        if (number != expected || number < 1) return false;
        phase = RoomState.STARTING;
        countdownNumber = number;
        startPending = false;
        notice = null;
        return true;
    }

    /** START_CANCELLED: 시작 중일 때만 대기로 되돌린다 */
    public boolean cancelStart(String message) {
        if (phase != RoomState.STARTING) return false;
        phase = RoomState.WAITING;
        countdownNumber = 0;
        startPending = false;
        notice = message;
        return true;
    }

    /** GAME_START: 카운트다운 1 다음에만 시작. 게임 화면에 넘길 시작 정보를 돌려준다 */
    public GameSession startGame() {
        if (phase != RoomState.STARTING || countdownNumber != 1 || !settingsReceived) return null;
        phase = RoomState.PLAYING;
        countdownNumber = 0;
        notice = null;
        return new GameSession(localId, hostId, participants, settings);
    }

    /** GAME_END: 서버도 방을 대기 상태로 되돌린다 */
    public void endGame() {
        phase = RoomState.WAITING;
        countdownNumber = 0;
        startPending = false;
        notice = null;
    }

    /** 서버 명령 거부 등 안내 문구 */
    public void showNotice(String message) {
        notice = message;
    }

    /** 연결 종료: 참가자와 권한을 비우고 이유를 남긴다 */
    public void close(String message) {
        connected = false;
        participants = List.of();
        localId = null;
        hostId = null;
        phase = RoomState.WAITING;
        countdownNumber = 0;
        settingsReceived = false;
        settingsPending = false;
        startPending = false;
        notice = message;
    }
}
