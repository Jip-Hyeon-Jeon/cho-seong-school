package com.jiphyeonjeon.choseongschool.client.model;

import com.jiphyeonjeon.choseongschool.common.model.Difficulty;
import com.jiphyeonjeon.choseongschool.common.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.common.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.common.model.RoomState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 소켓·Swing 없이 대기실 상태 규칙만 검사한다 */
class LobbyStateTest {

    private static final ParticipantInfo JISU = new ParticipantInfo("1", "지수", 0);
    private static final ParticipantInfo YUNA = new ParticipantInfo("2", "유나", 3);

    /** 입장해 학칙까지 받은 상태 */
    private static LobbyState joined(String myId, String hostId) {
        LobbyState state = new LobbyState();
        state.welcome(myId);
        assertTrue(state.updateRoster(hostId, List.of(JISU, YUNA)));
        state.confirmSettings(LobbySettings.DEFAULT);
        return state;
    }

    @Test
    void 반장만_학칙을_바꾸고_시작할_수_있다() {
        LobbyState host = joined("1", "1");
        LobbyState student = joined("2", "1");
        assertTrue(host.isHost());
        assertTrue(host.canStart());
        assertFalse(student.isHost());
        assertFalse(student.canEditSettings());
        assertFalse(student.requestStart());
    }

    @Test
    void 요청을_보내면_서버_응답_전까지_다시_보낼_수_없다() {
        LobbyState host = joined("1", "1");
        assertTrue(host.requestSettings());
        assertTrue(host.isSettingsPending());
        assertFalse(host.requestStart());
        host.confirmSettings(new LobbySettings(Difficulty.HIGH, 20, 15));
        assertTrue(host.canStart());
        assertEquals(Difficulty.HIGH, host.settings().difficulty());
    }

    @Test
    void 본인이나_반장이_없는_목록은_받지_않는다() {
        LobbyState state = new LobbyState();
        assertFalse(state.updateRoster("1", List.of(JISU))); // 아직 WELCOME 전
        state.welcome("2");
        assertFalse(state.updateRoster("1", List.of(JISU))); // 본인 없음
        assertFalse(state.updateRoster("9", List.of(JISU, YUNA))); // 반장 없음
        assertTrue(state.participants().isEmpty());
    }

    @Test
    void 카운트다운은_3_2_1_순서만_받고_1_다음에_게임이_시작된다() {
        LobbyState state = joined("2", "1");
        assertNull(state.startGame()); // 카운트다운 전 시작 무시
        assertFalse(state.countdown(2));
        assertTrue(state.countdown(3));
        assertEquals(RoomState.STARTING, state.phase());
        assertFalse(state.countdown(1)); // 2를 건너뜀
        assertTrue(state.countdown(2));
        assertNull(state.startGame()); // 1 전에는 시작 안 함
        assertTrue(state.countdown(1));
        GameSession session = state.startGame();
        assertNotNull(session);
        assertEquals("2", session.localId());
        assertEquals("1", session.hostId());
        assertEquals(RoomState.PLAYING, state.phase());
        assertNull(state.startGame()); // 중복 시작 무시
        assertFalse(state.countdown(3)); // 게임 중 카운트다운 무시
    }

    @Test
    void 시작_취소와_게임_종료는_대기로_돌아간다() {
        LobbyState host = joined("1", "1");
        assertFalse(host.cancelStart("취소")); // 대기 중에는 취소할 것이 없음
        host.countdown(3);
        assertTrue(host.cancelStart("취소"));
        assertEquals(RoomState.WAITING, host.phase());
        assertEquals("취소", host.notice());
        assertTrue(host.canStart());

        host.countdown(3);
        host.countdown(2);
        host.countdown(1);
        host.startGame();
        host.endGame();
        assertEquals(RoomState.WAITING, host.phase());
        assertTrue(host.canStart()); // 다음 판 시작 가능
    }

    @Test
    void 연결이_끊기면_참가자와_권한이_사라진다() {
        LobbyState host = joined("1", "1");
        host.close("서버 연결이 종료되었습니다.");
        assertFalse(host.isConnected());
        assertFalse(host.isHost());
        assertFalse(host.canStart());
        assertTrue(host.participants().isEmpty());
        assertEquals("서버 연결이 종료되었습니다.", host.notice());
    }
}
