package com.jiphyeonjeon.choseongschool.controller;

import com.jiphyeonjeon.choseongschool.common.GameConstants.Protocol;
import com.jiphyeonjeon.choseongschool.view.GameRoomView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;

/**
 * 게임 화면의 Controller (MVC).
 * - 입력창 Enter / 제출 버튼 → 서버로 "ANSWER|단어" 전송
 * - 서버 메시지 한 줄 → 메시지 이름별 처리기(커맨드 패턴)가 화면을 바꾼다
 */
public class GameRoomController {

    /** 서버로 한 줄 보내는 곳 (소켓 담당이 client::send를 넘겨준다) */
    public interface MessageSender {
        void send(String line);
    }

    /** 게임이 끝나면 최종 점수를 받을 곳 (옵저버 패턴, 결과 화면 담당이 등록한다) */
    public interface GameEndListener {
        void onGameEnd(List<FinalScore> scores);
    }

    /** 서버 메시지 한 종류를 처리하는 커맨드. f[0]은 메시지 이름, f[1]부터 내용 칸 */
    private interface MessageHandler {
        void handle(String[] f);
    }

    /** 최종 점수 한 줄 (서버가 보낸 순서 그대로) */
    public static class FinalScore {
        private final String nickname;
        private final int score;

        public FinalScore(String nickname, int score) {
            this.nickname = nickname;
            this.score = score;
        }

        public String nickname() {
            return nickname;
        }

        public int score() {
            return score;
        }
    }

    private final GameRoomView view;
    private final MessageSender sender;
    private final Map<String, MessageHandler> handlers = new HashMap<>();
    private GameEndListener gameEndListener;

    public GameRoomController(GameRoomView view, MessageSender sender) {
        this.view = view;
        this.sender = sender;
        view.getInputField().addActionListener(e -> submit()); // Enter
        view.getSendButton().addActionListener(e -> submit());

        handlers.put(Protocol.QUESTION, f -> {
            view.showQuestion(f[1]);
            view.setInputEnabled(true); // 새 문제가 오면 다시 답을 받는다
        });
        handlers.put(Protocol.HINT, f -> view.showHint(f[1]));
        handlers.put(Protocol.ANSWER, f -> showAnswer(f[1], f[2], f[3].equals(Protocol.CORRECT)));
        handlers.put(Protocol.TIME, f -> view.setTimeLeft(Integer.parseInt(f[1]), Integer.parseInt(f[2])));
        handlers.put(Protocol.SCORE, f -> view.setScore(f[1], Integer.parseInt(f[2])));
        handlers.put(Protocol.ROUND, f -> view.setRound(Integer.parseInt(f[1]), Integer.parseInt(f[2])));
        handlers.put(Protocol.ROUND_END, f -> {
            view.showRoundEnd(f[1], f[2]);
            view.setInputEnabled(false); // 뜻풀이 쉬는 시간
        });
        handlers.put(Protocol.GAME_END, f -> endGame(f));
    }

    public void setGameEndListener(GameEndListener listener) {
        this.gameEndListener = listener;
    }

    private void submit() {
        String text = view.getInputField().getText().trim();
        // 구분자가 들어가면 메시지 칸이 밀리므로 보내지 않는다 (서버도 같은 검사를 한다)
        if (text.isEmpty() || text.contains(Protocol.SEPARATOR)) {
            return;
        }
        sender.send(Protocol.ANSWER + Protocol.SEPARATOR + text);
        view.getInputField().setText("");
    }

    /** 소켓 수신 스레드가 서버 메시지 한 줄을 받을 때마다 호출한다 */
    public void handle(String line) {
        String[] f = line.split("\\|", -1); // |는 정규식 기호라 \\|, -1은 "HINT|"처럼 끝의 빈 칸도 남긴다
        MessageHandler handler = handlers.get(f[0]);
        if (handler == null) {
            return; // 게임 화면이 모르는 메시지
        }
        SwingUtilities.invokeLater(() -> { // 화면은 Swing 스레드에서만 바꾼다
            try {
                handler.handle(f);
            } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
                // 칸이 모자라거나 숫자 칸에 숫자가 아닌 값이 온 메시지는 버린다
            }
        });
    }

    private void showAnswer(String nickname, String word, boolean correct) {
        view.showBubble(nickname, word, correct);
        if (correct) { // O는 첫 정답자뿐이라 이번 라운드는 끝난 것
            view.jump(nickname);
            view.setInputEnabled(false);
        }
    }

    /** GAME_END|닉네임|점수|닉네임|점수|... */
    private void endGame(String[] f) {
        if (f.length < 3 || f.length % 2 == 0) {
            return; // 닉네임·점수 짝이 안 맞으면 버린다
        }
        List<FinalScore> scores = new ArrayList<>();
        for (int i = 1; i < f.length; i += 2) {
            scores.add(new FinalScore(f[i], Integer.parseInt(f[i + 1])));
        }
        view.setInputEnabled(false);
        if (gameEndListener != null) {
            gameEndListener.onGameEnd(scores);
        }
    }
}
