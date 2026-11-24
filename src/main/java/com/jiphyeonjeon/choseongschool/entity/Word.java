package com.jiphyeonjeon.choseongschool.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Word {
	@Id 	//기본키 명시
	private String word;
	private String example;
	private String level;
	private String meaning;
	
	//기본 생성자 반드시 넣어줌
	public Word() {
		
	}
	
	//전체 필드 생성자
	public Word(String word, String example, String level, String meaning) {
		this.word = word;
		this.example = example;
		this.level = level;
		this.meaning = meaning;
	}
	
	//getter, setter
	public String getWord() {
		return word;
	}

	public void setWord(String word) {
		this.word = word;
	}

	public String getExample() {
		return example;
	}

	public void setExample(String example) {
		this.example = example;
	}

	public String getLevel() {
		return level;
	}

	public void setLevel(String level) {
		this.level = level;
	}

	public String getMeaning() {
		return meaning;
	}

	public void setMeaning(String meaning) {
		this.meaning = meaning;
	}
	
}
