package com.jiphyeonjeon.choseongschool.client;

import javax.swing.JComponent;

/** 화면 한 장을 보여 주는 곳. 실제 실행은 MainFrame, 테스트는 창 없이 기록만 한다 */
public interface ScreenHost {

    /** Swing 이벤트 스레드에서 호출한다 */
    void show(JComponent screen);
}
