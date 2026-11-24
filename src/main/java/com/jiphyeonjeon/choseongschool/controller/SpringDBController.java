package com.jiphyeonjeon.choseongschool.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jiphyeonjeon.choseongschool.entity.Word;
import com.jiphyeonjeon.choseongschool.service.WordService;

@RestController
@RequestMapping("/word")
public class SpringDBController {

	private final WordService service;

	// 생성자 주입 (@Autowired 생략 가능)
	public SpringDBController(WordService service) {
		this.service = service;
	}

	// 전체 단어 목록 조회
	@GetMapping("/list")
	public List<Word> read() {
		return service.read();
	}

	// 단어 등록 (JSON 요청 본문)
	@PostMapping
	public Word create(@RequestBody Word word) {
		return service.create(word);
	}

	// 단어 등록 (폼 데이터 요청)
	@PostMapping("/submitForm")
	public Word submit(@RequestParam(name = "word") String word,
			@RequestParam(name = "example") String example,
			@RequestParam(name = "level") String level,
			@RequestParam(name = "meaning") String meaning) {
		System.out.println(word + " : " + example);
		return service.create(new Word(word, example, level, meaning));
	}
}