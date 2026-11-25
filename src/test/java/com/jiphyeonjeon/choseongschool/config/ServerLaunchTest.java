package com.jiphyeonjeon.choseongschool.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.jiphyeonjeon.choseongschool.lobby.model.Difficulty;
import com.jiphyeonjeon.choseongschool.lobby.server.LobbyServer;
import com.jiphyeonjeon.choseongschool.service.WordService;

/**
 * Spring 앱 하나를 실행하면 DB 계층(WordService)과 대기실 서버가 함께 뜨는지 확인한다.
 * 실제 MariaDB 대신 테스트 전용 H2 메모리 DB를 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
		"spring.datasource.hikari.jdbc-url=jdbc:h2:mem:launch",
		"spring.datasource.hikari.driver-class-name=org.h2.Driver",
		"spring.datasource.hikari.username=sa",
		"spring.datasource.hikari.password=",
		"lobby.bind-address=127.0.0.1",
		"lobby.port=0" })
class ServerLaunchTest {

	@Autowired
	private LobbyServer lobbyServer;

	@Autowired
	private WordService wordService;

	@Test
	void springAppStartsDbAndLobbyServerTogether() throws Exception {
		assertEquals(0, wordService.readByLevel(Difficulty.LOW.getLabel(), 10).size()); // DB 조회가 동작한다
		try (Socket socket = new Socket("127.0.0.1", lobbyServer.getPort())) {
			new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8).println("JOIN|지수|0");
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			assertEquals("WELCOME|1", in.readLine()); // 대기실 서버가 접속을 받는다
		}
	}
}
