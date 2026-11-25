package com.jiphyeonjeon.choseongschool.common;

/*
 * 프로젝트 전역 상수 모음 (클라이언트(Swing) + 서버(Spring Boot) 공용)
 *
 * [사용법]
 * 1) 클래스 이름으로 접근
 *    import com.jiphyeonjeon.choseongschool.common.GameConstants;
 *    frame.setSize(GameConstants.Screen.WIDTH, GameConstants.Screen.HEIGHT);
 *
 * 2) 그룹을 import 해서 짧게 쓰기
 *    import com.jiphyeonjeon.choseongschool.common.GameConstants.Screen;
 *    frame.setSize(Screen.WIDTH, Screen.HEIGHT);
 *
 * [상수 추가 규칙]
 * - 관련된 상수끼리 중첩 클래스(그룹)로 묶는다.
 *   예) 클라이언트: Screen, Color, Font / 서버: Api, Room / 공용: Game
 * - 그룹 주석에 [클라이언트], [서버], [공용] 중 어디서 쓰는지 표시한다.
 * - 그룹과 상수 위에는 마우스를 올리면 팝업으로 보이도록 한 줄 Javadoc을 단다. (예: 별표 두 개 주석 + 설명)
 * - 새 그룹은 public static final class 이름 { private 이름() {} } 형태로 만든다.
 * - 상수는 public static final 로 선언하고, 이름은 대문자 + 언더스코어로 짓는다. (예: MAX_PLAYER)
 * - 코드에 숫자/문자열을 직접 쓰지 말고 여기에 추가한 뒤 사용한다.
 */

/** 프로젝트 전역 상수 모음 (클라이언트 + 서버 공용) */
public final class GameConstants {

    private GameConstants() {
    }

    /** [클라이언트] 화면(윈도우) 관련 상수 */
    public static final class Screen {

        /** 게임 화면 가로 크기 (px, Figma 시안 기준) */
        public static final int WIDTH = 1200;

        /** 게임 화면 세로 크기 (px, Figma 시안 기준) */
        public static final int HEIGHT = 740;

        private Screen() {
        }
    }

    /** [공용] 서버-클라이언트 메시지 형식 (예: ANSWER|닉네임|단어|O) */
    public static final class Protocol {

        /** 메시지 구분자 */
        public static final String SEPARATOR = "|";

        /** 문제 출제: QUESTION|초성 */
        public static final String QUESTION = "QUESTION";

        /** 힌트 공개: HINT|뜻 */
        public static final String HINT = "HINT";

        /** 답 제출(클라이언트→서버): ANSWER|단어, 판정 결과(서버→전원): ANSWER|닉네임|단어|O 또는 X (O는 Round.Result.FIRST만, WRONG·ALREADY_SOLVED는 X) */
        public static final String ANSWER = "ANSWER";

        /** 정답 표시 */
        public static final String CORRECT = "O";

        /** 오답 표시 */
        public static final String WRONG = "X";

        /** 남은 시간(서버→전원, 1초마다): TIME|남은초|전체초 */
        public static final String TIME = "TIME";

        /** 점수 갱신(서버→전원): SCORE|닉네임|점수 */
        public static final String SCORE = "SCORE";

        /** 새 라운드 시작(서버→전원, QUESTION보다 먼저): ROUND|현재라운드|전체라운드 */
        public static final String ROUND = "ROUND";

        /** 라운드 종료·정답 공개(서버→전원): ROUND_END|정답|뜻 */
        public static final String ROUND_END = "ROUND_END";

        /** 게임 종료·최종 점수(서버→전원): GAME_END|닉네임|점수|닉네임|점수|... */
        public static final String GAME_END = "GAME_END";

        private Protocol() {
        }
    }
}
