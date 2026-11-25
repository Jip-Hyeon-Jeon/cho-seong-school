package com.jiphyeonjeon.choseongschool.model;

public class Player {
	private String nickname;	//닉네임
	private String character;	//선택한 캐릭터의 초상화 이미지 경로
	private int score;			//점수
	
	public Player() {
		
	}
	
	//입장할 때 사용, 점수 0점
	public Player(String nickname, String character) {
		this.nickname = nickname;
		this.character = character;
	}

	public Player(String nickname, String character, int score) {
		this.nickname = nickname;
		this.character = character;
		this.score = score;
	}
	
	//정답 맞출 때 호출하는 점수 누적 함수
	public int addScore(int score) {
		this.score += score;
		return this.score;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public String getCharacter() {
		return character;
	}

	public void setCharacter(String character) {
		this.character = character;
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}
		
}
