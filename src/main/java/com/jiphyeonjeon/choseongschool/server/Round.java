package com.jiphyeonjeon.choseongschool.server;

/** 한 라운드의 정답 판정. 여러 ClientHandler 스레드가 동시에 호출해도 첫 정답자 1명만 인정한다. */
public class Round {

    /** 판정 결과 */
    public enum Result {
        /** 이번 라운드 첫 정답자 */
        FIRST,
        /** 오답 */
        WRONG,
        /** 정답이지만 이미 다른 사람이 맞힘 */
        ALREADY_SOLVED
    }

    private final String answer;
    private String winner;

    public Round(String answer) {
        this.answer = answer;
    }

    /** synchronized: 동시에 들어와도 한 스레드씩 실행되므로 winner는 한 번만 정해진다 */
    public synchronized Result tryAnswer(String nickname, String input) {
        if (!answer.equals(input.trim())) {
            return Result.WRONG;
        }
        if (winner != null) {
            return Result.ALREADY_SOLVED;
        }
        winner = nickname;
        return Result.FIRST;
    }

    public synchronized String getWinner() {
        return winner;
    }
}
