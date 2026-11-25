package com.jiphyeonjeon.choseongschool.server.timer;

public class RoundTimer implements Runnable {

    private final int totalSeconds;
    private final int hintTriggerSeconds;
    private final TimerListener listener;

    private Thread timerThread;
    private volatile boolean isRunning = false;

    public RoundTimer(int totalSeconds, TimerListener listener) {
        this.totalSeconds = totalSeconds;
        this.hintTriggerSeconds = totalSeconds / 2;
        this.listener = listener;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        timerThread = new Thread(this);
        timerThread.start();
    }

    public void stop() {
        isRunning = false;
        if (timerThread != null) {
            timerThread.interrupt();
        }
    }

    @Override
    public void run() {
        int remaining = totalSeconds;

        try {
            while (isRunning && remaining > 0) {
                listener.onTick(remaining);
                if (remaining == hintTriggerSeconds) {
                    listener.onHintTime();
                }
                Thread.sleep(1000);
                remaining--;
            }

            // 아무도 못 맞히고 시간이 0초 됐을 경우
            if (isRunning) {
                isRunning = false;
                listener.onTick(0);
                listener.onTimeOut();
            }
        } catch (InterruptedException e) {
            System.out.println("타이머 중단");
        }

    }
}
