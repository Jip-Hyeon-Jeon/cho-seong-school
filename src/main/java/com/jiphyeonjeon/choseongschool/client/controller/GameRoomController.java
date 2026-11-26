package com.jiphyeonjeon.choseongschool.client.controller;

import com.jiphyeonjeon.choseongschool.common.GameConstants.Protocol;
import com.jiphyeonjeon.choseongschool.client.view.GameRoomView;
import com.jiphyeonjeon.choseongschool.common.model.ItemKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

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

    /** 내 닉네임 (약과를 내가 먹었는지 구분용) */
    private String myNickname;
    /** 지금 문제를 받는 중인지 (문제가 나온 뒤 ~ 정답이 나오거나 라운드가 끝나기 전) */
    private boolean answering;
    /** 다른 학동이 약과를 먹어 답안이 막혔는지 */
    private boolean blocked;
    private Timer blockTimer;

    public GameRoomController(GameRoomView view, MessageSender sender) {
        this.view = view;
        this.sender = sender;
        view.getInputField().addActionListener(e -> submit()); // Enter
        view.getSendButton().addActionListener(e -> submit());

        handlers.put(Protocol.QUESTION, f -> {
            view.showQuestion(f[1]);
            answering = true; // 새 문제가 오면 다시 답을 받는다
            updateInput();
        });
        handlers.put(Protocol.HINT, f -> view.showHint(f[1]));
        handlers.put(Protocol.ANSWER, f -> showAnswer(f[1], f[2], f[3].equals(Protocol.CORRECT)));
        handlers.put(Protocol.TIME, f -> view.setTimeLeft(Integer.parseInt(f[1]), Integer.parseInt(f[2])));
        handlers.put(Protocol.SCORE, f -> view.setScore(f[1], Integer.parseInt(f[2])));
        handlers.put(Protocol.ROUND, f -> view.setRound(Integer.parseInt(f[1]), Integer.parseInt(f[2])));
        handlers.put(Protocol.ROUND_END, f -> {
            view.showRoundEnd(f[1], f[2]);
            view.hideItem(); // 라운드가 끝나면 아이템도 사라진다
            answering = false; // 뜻풀이 쉬는 시간
            updateInput();
        });
        handlers.put(Protocol.GAME_END, f -> endGame(f));

        // 묘수전
        handlers.put(Protocol.ITEM, f -> view.showItem(ItemKind.valueOf(f[1]), f[2], Integer.parseInt(f[3]),
                Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6])));
        handlers.put(Protocol.ITEM_GET, f -> {
            view.hideItem();
            view.showItemBubble(f[1], ItemKind.valueOf(f[2]));
        });
        handlers.put(Protocol.BLOCK, f -> block(f[1], Integer.parseInt(f[2])));
    }

    public void setGameEndListener(GameEndListener listener) {
        this.gameEndListener = listener;
    }

    public void setMyNickname(String nickname) {
        this.myNickname = nickname;
    }

    /** 입력칸은 문제를 받는 중이고 약과에 막히지 않았을 때만 열린다 */
    private void updateInput() {
        view.setInputEnabled(answering && !blocked);
    }

    /** BLOCK|먹은 닉네임|초: 내가 먹은 게 아니면 그 시간 동안 입력을 막는다 */
    private void block(String owner, int seconds) {
        if (owner.equals(myNickname)) {
            return;
        }
        blocked = true;
        view.showBlocked(owner);
        updateInput();

        if (blockTimer != null) {
            blockTimer.stop(); // 또 막히면 처음부터 다시 센다
        }
        blockTimer = new Timer(seconds * 1000, e -> {
            blocked = false;
            view.showBlocked(null);
            updateInput();
        });
        blockTimer.setRepeats(false);
        blockTimer.start();
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
            answering = false;
            updateInput();
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
        answering = false;
        updateInput();
        if (gameEndListener != null) {
            gameEndListener.onGameEnd(scores);
        }
    }
}
