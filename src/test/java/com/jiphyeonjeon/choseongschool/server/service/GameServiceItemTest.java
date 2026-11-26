package com.jiphyeonjeon.choseongschool.server.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.jiphyeonjeon.choseongschool.common.model.Difficulty;
import com.jiphyeonjeon.choseongschool.common.model.GameMode;
import com.jiphyeonjeon.choseongschool.common.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.common.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.server.entity.Word;

/** 묘수전: 아이템이 나오고, 단어를 입력한 학동이 먹으면 효과가 적용되는지 확인한다 */
class GameServiceItemTest {

    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final ParticipantInfo alice = new ParticipantInfo("1", "영희", 0);
    private final ParticipantInfo bob = new ParticipantInfo("2", "철수", 1);
    private GameService game;

    @AfterEach
    void tearDown() {
        game.destroy();
    }

    @Test
    void itemAppearsAndFirstTyperGetsIt() throws Exception {
        WordService wordService = mock(WordService.class);
        when(wordService.readByLevel(anyString(), anyInt()))
                .thenReturn(List.of(new Word("사과", "ㅅㄱ를 먹었다", "하", "과일")));
        when(wordService.readItemWords()).thenReturn(List.of("일석이조"));
        game = new GameService(wordService, new ItemService());

        // 한 라운드짜리 게임이라 마지막 라운드에서 아이템이 꼭 나온다
        LobbySettings settings = mock(LobbySettings.class);
        when(settings.difficulty()).thenReturn(Difficulty.LOW);
        when(settings.rounds()).thenReturn(1);
        when(settings.seconds()).thenReturn(10);
        when(settings.mode()).thenReturn(GameMode.ITEM);
        game.onStart(List.of(alice, bob), settings, lines::add);

        waitFor("ITEM|");
        assertTrue(find("ITEM|").contains("|일석이조|"));

        game.onAnswer(bob, "일석이조", null);
        waitFor("ITEM_GET|");
        String get = find("ITEM_GET|");
        assertTrue(get.startsWith("ITEM_GET|철수|"));

        if (get.endsWith("YAKGWA")) {
            assertTrue(lines.contains("BLOCK|철수|5"));
            game.onAnswer(alice, "사과", null); // 막혀 있으니 무시된다
            assertFalse(lines.contains("ANSWER|영희|사과|O"));
        } else {
            // 곶감: 둘 다 0점이라 바꿔도 0점
            assertTrue(lines.contains("SCORE|철수|0"));
        }

        game.onAnswer(alice, "일석이조", null); // 이미 먹은 아이템
        assertFalse(lines.contains("ITEM_GET|영희|GOTGAM") || lines.contains("ITEM_GET|영희|YAKGWA"));
    }

    private void waitFor(String prefix) throws InterruptedException {
        for (int i = 0; i < 50 && find(prefix) == null; i++) {
            Thread.sleep(100);
        }
        assertTrue(find(prefix) != null, prefix + " 메시지가 오지 않음: " + lines);
    }

    private String find(String prefix) {
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        return null;
    }
}
