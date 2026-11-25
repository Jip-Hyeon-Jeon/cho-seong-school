package com.jiphyeonjeon.choseongschool.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** 10명이 같은 순간에 정답을 보내도 FIRST는 딱 1명 */
    @RepeatedTest(50)
    void 동시_정답이어도_승자는_한명() throws InterruptedException {
        Round round = new Round("사필귀정");
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger firstCount = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            String nickname = "player" + i;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                if (round.tryAnswer(nickname, "사필귀정") == Round.Result.FIRST) {
                    firstCount.incrementAndGet();
                }
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }

        assertEquals(1, firstCount.get());
    }
}
