package com.jiphyeonjeon.choseongschool.server.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.jiphyeonjeon.choseongschool.server.entity.Word;
import com.jiphyeonjeon.choseongschool.server.repository.WordRepository;

@Service	//이 클래스가 Spring의 서비스 컴포넌트임을 나타냄 (비즈니스 로직 담당)
public class WordService {
	
	//WordRepository(데이터베이스 접근을 위한 인터페이스)를 주입받기 위한 필드
	private final WordRepository wordRepository;
	
	//생성자를 통한 의존성 주입 (Spring이 자동으로 주입해줌)
	public WordService(WordRepository wordRepository) {
		this.wordRepository = wordRepository;
	}
	
	//READ: 난이도에 맞는 단어를 섞어서 count개 조회.
	public List<Word> readByLevel(String level, int count) {
		List<Word> words = new ArrayList<>(wordRepository.findByLevel(level));	//해당 난이도 해당하는 전체 단어 목록 가져옴
		Collections.shuffle(words);		//단어 목록을 섞음
		return words.subList(0, Math.min(count, words.size()));		//전체 단어 목록에서 총 문제 수만큼만 뽑아서 전달. 만약 count개가 전체 단어 목록 수보다 많을 경우 전체 단어 목록 리턴
	}

}
