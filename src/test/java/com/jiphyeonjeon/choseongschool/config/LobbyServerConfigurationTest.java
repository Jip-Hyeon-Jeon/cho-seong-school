package com.jiphyeonjeon.choseongschool.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.jiphyeonjeon.choseongschool.lobby.model.LobbySettings;
import com.jiphyeonjeon.choseongschool.lobby.model.ParticipantInfo;
import com.jiphyeonjeon.choseongschool.lobby.server.GameEvents;
import com.jiphyeonjeon.choseongschool.lobby.server.LobbyServer;

/** DB 없이 대기실 서버 Bean의 생성·게임 로직 주입·종료 정리를 확인한다 */
class LobbyServerConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(LobbyServerConfiguration.class)
			.withPropertyValues("lobby.bind-address=127.0.0.1", "lobby.port=0");

	/** 게임이 시작되면 onStart 호출을 알려 주는 테스트용 게임 로직 */
	static final class StartRecorder implements GameEvents {
		final CountDownLatch started = new CountDownLatch(1);

		@Override
		public void onStart(List<ParticipantInfo> players, LobbySettings settings, Consumer<String> broadcast) {
			started.countDown();
		}

		@Override
		public void onAnswer(ParticipantInfo player, String answer, Consumer<String> broadcast) {
		}
	}

	@Test
	void serverOpensWithSpringAndClosesWithIt() {
		int[] port = new int[1];
		runner.run(context -> {
			LobbyServer server = context.getBean(LobbyServer.class);
			port[0] = server.getPort();
			try (Socket socket = new Socket("127.0.0.1", port[0])) {
				assertEquals("WELCOME|1", join(socket));
			}
		});
		// 컨텍스트가 닫히면 소켓도 닫혀 더 이상 접속할 수 없다
		assertThrows(IOException.class, () -> new Socket("127.0.0.1", port[0]).close());
	}

	@Test
	void gameEventsBeanIsConnectedToServer() {
		StartRecorder game = new StartRecorder();
		runner.withBean(GameEvents.class, () -> game).run(context -> {
			LobbyServer server = context.getBean(LobbyServer.class);
			try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
				join(socket);
				send(socket, "START");
				// 3·2·1 카운트다운 뒤 주입된 게임 로직의 onStart가 불린다
				assertTrue(game.started.await(10, TimeUnit.SECONDS));
			}
		});
	}

	private static String join(Socket socket) throws IOException {
		send(socket, "JOIN|지수|0");
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
		return in.readLine();
	}

	private static void send(Socket socket, String line) throws IOException {
		PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
		out.println(line);
	}
}
