package com.jiphyeonjeon.choseongschool.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RoundTest {

    @Test
    void 오답은_WRONG() {
        Round round = new Round("사필귀정");
        assertEquals(Round.Result.WRONG, round.tryAnswer("유나", "사필귀점"));
    }

    @Test
    void 앞뒤_공백은_무시한다() {
        Round round = new Round("사필귀정");
        assertEquals(Round.Result.FIRST, round.tryAnswer("유나", " 사필귀정 "));
    }

    @Test
    void 두번째_정답자는_ALREADY_SOLVED() {
        Round round = new Round("사필귀정");
        round.tryAnswer("유나", "사필귀정");
        assertEquals(Round.Result.ALREADY_SOLVED, round.tryAnswer("지수", "사필귀정"));
        assertEquals("유나", round.getWinner());
    }

    /** 10명이 동시에 정답을 보내도 FIRST는 딱 1명 (50번 반복) */
    @RepeatedTest(50)
    void 동시_정답이어도_승자는_한명() throws InterruptedException {
        Round round = new Round("사필귀정");
        List<String> firsts = Collections.synchronizedList(new ArrayList<>()); // 여러 스레드가 함께 쓰는 리스트
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            String nickname = "player" + i;
            Thread t = new Thread(() -> {
                if (round.tryAnswer(nickname, "사필귀정") == Round.Result.FIRST) {
                    firsts.add(nickname);
                }
            });
            threads.add(t);
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join(); // 모든 스레드가 끝날 때까지 기다린다
        }

        assertEquals(1, firsts.size());
    }
}
