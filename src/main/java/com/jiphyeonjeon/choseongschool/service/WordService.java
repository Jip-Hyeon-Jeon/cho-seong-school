package com.jiphyeonjeon.choseongschool.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.jiphyeonjeon.choseongschool.entity.Word;
import com.jiphyeonjeon.choseongschool.repository.WordRepository;

@Service	//이 클래스가 Spring의 서비스 컴포넌트임을 나타냄 (비즈니스 로직 담당)
public class WordService {
	
	//WordRepository(데이터베이스 접근을 위한 인터페이스)를 주입받기 위한 필드
	private final WordRepository wordRepository;
	
	//생성자를 통한 의존성 주입 (Spring이 자동으로 주입해줌)
	public WordService(WordRepository wordRepository) {
		this.wordRepository = wordRepository;
	}
	
	//CREATE: 새로운 단어 생성 및 저장
	public Word create(Word word) {
		return wordRepository.save(word);  //JPA의 save() 메서드를 통해 DB에 저장
	}
	
	//READ: 모든 단어 목록 조회
	public List<Word> read(){
		return wordRepository.findAll();
	}
	
	//READ: ID로 단어 조회 (없으면 null 리턴)
	public Word readById(String id) {
		return wordRepository.findById(id).orElse(null); //Optional에서 값이 없으면 null 리턴
	}
	
	//UPDATE: ID로 단어 조회 후 예문, 난이도, 뜻 수정
	public Word update(String id, Word updatedWord) {
		Word word = readById(id); //기존 단어 조회
		if (word == null) {
			return null; //단어가 없으면 null 리턴
		}
		
		//단어 정보 업데이트
		word.setExample(updatedWord.getExample());
		word.setLevel(updatedWord.getLevel());
		word.setMeaning(updatedWord.getMeaning());
		return wordRepository.save(word); //수정된 단어 저장 및 리턴
	}
	
	//DELETE: ID로 단어 삭제(성공 여부를 Boolean으로 리턴)
	public Boolean delete(String id) {
		if(!wordRepository.existsById(id)) {
			return false; //단어가 없으면 false 리턴
		}
		wordRepository.deleteById(id); //단어 삭제
		return true; //삭제 성공시 true 리턴
	}
}
