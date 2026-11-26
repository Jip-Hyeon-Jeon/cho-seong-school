package com.jiphyeonjeon.choseongschool.server.service;

import java.util.List;
import java.util.Random;

import com.jiphyeonjeon.choseongschool.common.GameConstants;
import com.jiphyeonjeon.choseongschool.common.GameConstants.Protocol;
import com.jiphyeonjeon.choseongschool.common.model.ItemKind;
import com.jiphyeonjeon.choseongschool.common.model.Item;
import org.springframework.stereotype.Service;


// 아이템을 언제 띄울지, 어떤 단어와 경로로 띄울지, 입력이 아이템 단어인지, 약과에 막혔는지.
// 아이템은 한 번에 하나만 뜬다. 게임 진행(GameService)이 잠금(synchronized) 안에서 부른다.
@Service
public class ItemService {

    private final Random random = new Random();

    private List<String> words = List.of(); // 아이템에 넣을 네 글자 단어 목록
    private boolean shownThisGame;          // 이번 게임에 아이템이 한 번이라도 나왔는지 확인하는 flag 변수 (전체 게임 중 아이템이 최소 1번은 나오게 보장)
    private Item current;               	// 지금 등장한 있는 아이템. 없으면 null

    private String blockOwnerId;            // 약과를 먹은 사용자의 ID
    private long blockEndTime;              // 약과의 효과가 끝나는 시각(ms)

    // 새 게임을 시작할 때 호출
    public void startGame(List<String> itemWords) {
        words = itemWords;		// 단어 목록 받음
        shownThisGame = false;	// 최소 한번이라도 아이템이 나왔는지 flag 변수
        clear();				// 등장한 아이템과 아이템의 효과를 지움
    }

    // 등장한 아이템과 아이템의 효과를 지우는 함수
    public void clear() {
        current = null;
        blockOwnerId = null;
    }

    // 등장한 아이템을 지우는 함수 (다음 문제로 넘어갈 때 호출)
    public void hideItem() {
        current = null;
    }
    
    // 전체 게임 중 한번이라도 아이템이 나온 적 있는지 확인하는 함수
    // 마지막 라운드이고 한번도 단어가 나온 적 없는 경우 무조건 아이템 등장
    public boolean hasMustShown(boolean lastRound) { // 반드시 아이템을 등장시켜야 하니?
    	return lastRound && !shownThisGame; 
    }

    // 아이템 등장 확률 처리
    // 흠 아이템 무조건 한번은 나오게 할거면 다 지워도 됨
    public boolean shouldShow(boolean lastRound) {
    	// 예외 처리
        if (words.isEmpty()) { // 아이템 단어 목록이 빈 경우
            return false;
        }
        if (hasMustShown(lastRound)) {	// 무조건 아이템이 나와야 하는 경우 (전체 게임 중 한번도 아이템이 안나온 경우 대비)
            return true;					
        }
        // 라운드 당 아이템이 등장할 확률에 따라 아이템이 나올 수도 안나올 수도
        return random.nextDouble() < GameConstants.Item.CHANCE_PER_ROUND;	
    }

    // 문제가 나온 뒤 몇 ms뒤에 아이템이 등장할지 (MIN_DELAY_MS ~ MAX_DELAY_MS 사이)
    public int delayMillis(boolean lastRound) {
        if (hasMustShown(lastRound)) {				// 무조건 아이템이 나와야 하는 경우 (전체 게임 중 한번도 아이템이 안나온 경우 대비)
            return GameConstants.Item.MIN_DELAY_MS; // 꼭 내야 하는 아이템은 바로 등장 시킴
        }
        
        int range = GameConstants.Item.MAX_DELAY_MS - GameConstants.Item.MIN_DELAY_MS; 	// 최소 대기 시간과 최대 대기 시간 차이
        return GameConstants.Item.MIN_DELAY_MS + random.nextInt(range + 1);	//그 중 랜덤한 시간내에 등장시킴
    }

    /**
     * 새 아이템을 띄우고 보낼 메시지 한 줄(ITEM|...)을 돌려준다.
     * 뽑은 단어가 이번 라운드 정답과 같으면 띄우지 않고 null
     */
    // 아이템을 등장 시키는 함수
    public String show(String currentAnswer) {
    	
        String itemWord = words.get(random.nextInt(words.size()));	//4글자 단어 전체 목록에서 랜덤으로 아이템 단어 하나 뽑음
        
        ItemKind kind = null; 				
        //아이템 종류 선택
        if (random.nextDouble() < GameConstants.Item.GOTGAM_RATIO) {
            kind = ItemKind.GOTGAM; 		//곶감 지정
        }
        else
        	kind = ItemKind.YAKGWA;			//약과 지정
        
        // 아이템 생성		아이템 종류, 아이템 단어, 현재 시각 + 아이템이 화면을 가로지르는 시간 
        Item item = new Item(kind, itemWord, System.currentTimeMillis() + GameConstants.Item.CROSS_MILLIS);
        
        if (item.matches(currentAnswer)) {	
            return null;
        }
        
        current = item;
        shownThisGame = true;

        int[] path = randomPath();
        String s = Protocol.SEPARATOR;
        return Protocol.ITEM + s + kind.name() + s + itemWord
                + s + path[0] + s + path[1] + s + path[2] + s + path[3];
    }

    /**
     * 아이템이 지나갈 직선 경로 {시작x, 시작y, 끝x, 끝y}. 화면 크기를 1000으로 본 천분율이다.
     * 위, 왼쪽, 오른쪽, 왼쪽 위 모서리, 오른쪽 위 모서리 중 한 곳에서 맞은편으로 간다
     */
    private int[] randomPath() {
        int side = random.nextInt(5);
        if (side == 0) { // 위 → 아래
            return new int[] {between(100, 900), 0, between(100, 900), 1000};
        } else if (side == 1) { // 왼쪽 → 오른쪽
            return new int[] {0, between(150, 750), 1000, between(150, 750)};
        } else if (side == 2) { // 오른쪽 → 왼쪽
            return new int[] {1000, between(150, 750), 0, between(150, 750)};
        } else if (side == 3) { // 왼쪽 위 → 오른쪽 아래
            return new int[] {0, between(0, 200), 1000, between(700, 1000)};
        } else { // 오른쪽 위 → 왼쪽 아래
            return new int[] {1000, between(0, 200), 0, between(700, 1000)};
        }
    }

    private int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    // 사용자의 입력이 아이템 단어와 동일한 경우 처리하는 함수
    public Item take(String input) {
        if (current == null || current.isGone() || !current.matches(input)) {
            return null;
        }
        Item taken = current;
        current = null; // 방금 획득된 아이템은 지움
        return taken;
    }

    // 약과를 획득한 사용자를 제외하고 다른 사용자들이 BLOCK_SECONDS초 동안 답안을 낼 수 없게 막는 함수
    public void block(String ownerId) {
        blockOwnerId = ownerId;
        blockEndTime = System.currentTimeMillis() + GameConstants.Item.BLOCK_SECONDS * 1000;
    }
    
    // 약과의 효과로 이 사용자의 입력이 막혀있는지 확인하는 함수
    public boolean isBlocked(String playerId) {
        if (blockOwnerId == null || blockOwnerId.equals(playerId)) {	// 약과를 획득한 사용자는 block 하지 않음
            return false;
        }
        return System.currentTimeMillis() < blockEndTime;
    }
}
