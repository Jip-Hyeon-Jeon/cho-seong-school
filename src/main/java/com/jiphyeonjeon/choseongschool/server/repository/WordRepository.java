package com.jiphyeonjeon.choseongschool.server.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jiphyeonjeon.choseongschool.server.entity.Word;

@Repository								// 테이블 이름(엔티티 클래스), 기본키(@Id) 타입
public interface WordRepository extends JpaRepository<Word, String> {
	List<Word> findByLevel(String level); //해당하는 난이도의 단어 목록 리턴
}
//아무것도 적지 않아도 Springboot가 알아서 자동기능 제공
//저장 및 수정 wordRepository.save(word)  	Word 클래스 객체를 전달해야 함
//전체 조회 wordRepository.findAll()
//Id로 단건 조회 wordRepository.findById("심심한")  실제 단어 문자열을 전달해야 함
//삭제 wordRepository.delete(word);		Word 클래스 객체를 전달해야 함
//난이도별 단어 목록 조회 findByLevel(level) 커스텀 sql