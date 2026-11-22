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

        /** 게임 화면 가로 크기 (px) */
        public static final int WIDTH = 1600;

        /** 게임 화면 세로 크기 (px) */
        public static final int HEIGHT = 900;

        private Screen() {
        }
    }
}
