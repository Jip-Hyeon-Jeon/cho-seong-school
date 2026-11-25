package com.jiphyeonjeon.choseongschool.server.timer;

public interface TimerListener {
    void onTick(int remainingSeconds); // 1초마다 남은 시간 알림
    void onHintTime(); // 절반 시간이 지났을 때 힌트 공개 알림
    void onTimeOut(); // 0초가 되면 시간 종료 알림
}
