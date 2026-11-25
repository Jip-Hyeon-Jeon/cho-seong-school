package com.jiphyeonjeon.choseongschool.server.config;

import java.io.IOException;
import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jiphyeonjeon.choseongschool.server.GameEvents;
import com.jiphyeonjeon.choseongschool.server.LobbyServer;

/**
 * Spring 앱을 실행하면 DB와 함께 대기실 TCP 서버(LobbyServer)도 열리도록 등록한다.
 * Spring 컨테이너가 서버를 하나만 만들고, 앱이 종료될 때 close()로 소켓과 스레드를 정리한다.
 */
@Configuration
public class LobbyServerConfiguration {

	private static final Logger log = LoggerFactory.getLogger(LobbyServerConfiguration.class);

	/**
	 * @param bindAddress 접속을 받을 주소 (0.0.0.0이면 같은 Wi-Fi의 다른 PC도 접속 가능)
	 * @param port        대기실 포트 (0이면 빈 포트를 자동 선택, 테스트용)
	 * @param gameEvents  게임 진행 로직. GameEvents 구현체가 Bean으로 있으면 주입되고, 없으면 카운트다운까지만 동작
	 */
	@Bean(destroyMethod = "close")
	public LobbyServer lobbyServer(@Value("${lobby.bind-address:0.0.0.0}") String bindAddress,
			@Value("${lobby.port:50024}") int port,
			ObjectProvider<GameEvents> gameEvents) throws IOException {
		GameEvents events = gameEvents.getIfAvailable();
		LobbyServer server = new LobbyServer(InetAddress.getByName(bindAddress), port, events);
		log.info("대기실 서버: {}:{}", bindAddress, server.getPort());
		if (events == null) {
			log.warn("GameEvents 구현체가 없어 게임 시작 카운트다운까지만 동작합니다.");
		}
		return server;
	}
}
