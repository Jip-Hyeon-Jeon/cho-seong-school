package com.jiphyeonjeon.choseongschool.controller;

import com.jiphyeonjeon.choseongschool.common.GameConstants.Protocol;
import com.jiphyeonjeon.choseongschool.view.GameRoomView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

/**
 * 게임 화면 ↔ 서버 연결.
 * sender: 서버로 한 줄 보내는 함수 (소켓 연결 담당 쪽에서 out::println 같은 걸 넘겨준다)
 * handle(): 소켓 수신 스레드가 서버 메시지 한 줄을 받을 때마다 호출한다
 */
public class GameRoomController {

    /** 게임 종료 시 넘겨주는 최종 점수 한 줄 (서버가 보낸 순서 그대로) */
    public record FinalScore(String nickname, int score) {
    }

    private static final Pattern SPLIT = Pattern.compile(Pattern.quote(Protocol.SEPARATOR));

    private final GameRoomView view;
    private final Consumer<String> sender;
    /** 결과 화면 담당이 연결한다. 연결 전에는 아무것도 하지 않는다 */
    private Consumer<List<FinalScore>> gameEndListener = scores -> { };

    public GameRoomController(GameRoomView view, Consumer<String> sender) {
        this.view = view;
        this.sender = sender;
        view.getInputField().addActionListener(e -> submit()); // Enter
        view.getSendButton().addActionListener(e -> submit());
    }

    /** GAME_END를 받으면 Swing 스레드에서 최종 점수를 넘겨받을 곳 (결과 화면 전환용) */
    public void setGameEndListener(Consumer<List<FinalScore>> listener) {
        this.gameEndListener = listener;
    }

    private void submit() {
        String text = view.getInputField().getText().trim();
        // 구분자가 들어가면 메시지 칸이 밀리므로 보내지 않는다 (서버도 같은 검사를 해야 한다)
        if (text.isEmpty() || text.contains(Protocol.SEPARATOR)) {
            return;
        }
        sender.accept(Protocol.ANSWER + Protocol.SEPARATOR + text);
        view.getInputField().setText("");
    }

    public void handle(String line) {
        String[] parts = SPLIT.split(line, -1); // -1: "HINT|" 처럼 빈 마지막 칸도 유지
        Runnable update;
        try {
            update = toViewUpdate(parts);
        } catch (NumberFormatException e) {
            return; // 숫자 칸에 숫자가 아닌 값이 온 메시지는 버린다
        }
        if (update != null) {
            // 수신 스레드에서 호출되므로 화면 변경은 Swing 스레드로 넘긴다
            SwingUtilities.invokeLater(update);
        }
    }

    /** 메시지 한 줄 → 화면 변경. 칸이 모자라거나 모르는 메시지면 null */
    private Runnable toViewUpdate(String[] p) {
        return switch (p[0]) {
            case Protocol.QUESTION -> p.length < 2 ? null : () -> {
                view.showQuestion(p[1]);
                view.setInputEnabled(true); // 새 문제가 오면 다시 답을 받는다
            };
            case Protocol.HINT -> p.length < 2 ? null : () -> view.showHint(p[1]);
            case Protocol.ANSWER -> p.length < 4 ? null : () -> {
                boolean correct = Protocol.CORRECT.equals(p[3]);
                view.showBubble(p[1], p[2], correct);
                if (correct) { // O는 첫 정답자뿐이므로 이번 라운드는 끝난 것
                    view.jump(p[1]);
                    view.setInputEnabled(false);
                }
            };
            case Protocol.TIME -> {
                if (p.length < 3) {
                    yield null;
                }
                int seconds = Integer.parseInt(p[1]);
                int total = Integer.parseInt(p[2]);
                yield () -> view.setTimeLeft(seconds, total);
            }
            case Protocol.SCORE -> {
                if (p.length < 3) {
                    yield null;
                }
                int score = Integer.parseInt(p[2]);
                yield () -> view.setScore(p[1], score);
            }
            case Protocol.ROUND -> {
                if (p.length < 3) {
                    yield null;
                }
                int round = Integer.parseInt(p[1]);
                int total = Integer.parseInt(p[2]);
                yield () -> view.setRound(round, total);
            }
            case Protocol.ROUND_END -> p.length < 3 ? null : () -> {
                view.showRoundEnd(p[1], p[2]);
                view.setInputEnabled(false); // 뜻풀이 쉬는 시간
            };
            case Protocol.GAME_END -> {
                List<FinalScore> scores = finalScores(p);
                yield scores == null ? null : () -> {
                    view.setInputEnabled(false);
                    gameEndListener.accept(scores);
                };
            }
            default -> null; // 다른 담당자의 메시지는 무시
        };
    }

    /** GAME_END|닉네임|점수|닉네임|점수... → 최종 점수 목록. 닉네임·점수 짝이 안 맞으면 null */
    private static List<FinalScore> finalScores(String[] p) {
        if (p.length < 3 || p.length % 2 == 0) {
            return null;
        }
        List<FinalScore> scores = new ArrayList<>();
        for (int i = 1; i < p.length; i += 2) {
            scores.add(new FinalScore(p[i], Integer.parseInt(p[i + 1])));
        }
        return List.copyOf(scores);
    }
}
