# 대기실·소켓 연결

최대 5명의 입장·퇴장, 반장 위임, 학칙 공유와 서버 기준 3→2→1 카운트다운을 제공한다.
카운트다운 중 참가자가 나가면 시작을 취소한다. 문제 출제·점수·DB·게임 종료는 별도 연결 대상이다.

## 실행과 검사

Java 21, 프로젝트 루트에서 실행한다.

```powershell
.\gradlew.bat clean build
.\gradlew.bat runServer
.\gradlew.bat runClient
```

`runServer`는 Spring 앱 하나로 DB와 대기실 TCP 서버를 함께 실행한다(`config.LobbyServerConfiguration`).
DB 계정은 프로젝트 루트의 `application-secret.properties`(gitignore)나 환경 변수 `DB_USERNAME`, `DB_PASSWORD`로 넣는다.
대기실 주소와 포트는 `application.properties`의 `lobby.bind-address`(기본 `0.0.0.0`, 같은 Wi-Fi의 다른 PC도 접속), `lobby.port`(기본 50024)로 바꾼다.
Ctrl+C로 종료하면 Spring이 `LobbyServer.close()`로 소켓과 스레드를 정리한다.

DB 없이 대기실만 확인할 때는 `.\gradlew.bat runLobbyServer --args="50024 127.0.0.1"`을 쓰고 Enter로 종료한다. 이때는 게임 로직이 연결되지 않는다.
클라이언트(`client.ClientMain`)는 창 하나에서 로그인 → 대기실 → 게임 화면을 차례로 보여 준다.
테스트는 루프백 소켓으로 정원·반장 권한과 위임·설정 검증·카운트다운 취소/재시작·게임 메시지 전달을 확인한다.
별도 PR 소스나 미리보기 실행기는 필요하지 않다. 실제 MariaDB와 여러 PC/LAN 환경은 별도 검증 대상이다.

## 로그인과 화면 연결

Swing 이벤트 스레드에서 화면과 클라이언트를 만들고, 로그인 입력 검증 성공 시 한 번 접속한다.

```java
LobbyView lobby = new LobbyView();
LobbyClient client = new LobbyClient(lobby, gameScreenListener);
frame.setContentPane(new LobbyContainer(lobby));
frame.revalidate();
frame.repaint();
client.connect(serverIp, 50024, nickname, characterIndex);
```

캐릭터 번호는 세종대왕/퇴계이황/율곡이이/신사임당/이순신 순서의 0~4다. 로그인, 대기실 초상 파일(`ch번호.png`), 게임 화면 `Avatar`가 같은 번호를 쓴다. 닉네임은 1~8자이며 `|`와 제어 문자는 금지한다.
실제 조립은 `client.ClientFlow`가 한다. `LoginController`가 검증 후 `LoginListener`로 접속 정보를 넘기면 위 순서로 대기실에 접속한다.
대기실 `나가기`는 연결을 닫고 로그인 화면으로 돌아간다. 창 종료 시 `client.close()`를 호출하고 재접속은 새 객체로 한다.
화면 크기는 `GameConstants.Screen`(현재 1200×740)을 따른다.

## 게임 연결

`GameScreenListener`는 Swing 이벤트 스레드에서 호출된다.

- `onStart(GameSession)`: 참가자·학칙 전달 및 게임 화면으로 전환.
- `onMessage(String)`: 게임 컨트롤러의 `handle(line)` 호출.
- `onPlayers(List<ParticipantInfo>)`: 게임 중 참가자 목록 갱신.
- `onDisconnected(String)`, `onError(String)`: 입력 비활성화 또는 오류 안내.

PR #2의 컨트롤러에는 `new GameRoomController(gameView, client::send)`로 기존 연결을 넘긴다.
`gameView.setPlayers(...)`에는 참가자의 닉네임 목록을 전달한다. 소켓·수신 스레드를 새로 만들지 않는다.

서버는 `LobbyServer(bindAddress, port, GameEvents)`로 게임 로직을 연결한다.
`GameEvents` 구현체를 `@Component`로 등록하면 `runServer` 실행 시 Spring이 자동으로 주입한다. `WordService` 같은 DB Bean도 생성자로 받을 수 있다.
`onStart`에서 출제·문제 타이머를 시작하고 `onAnswer`에서 정답을 판정한 뒤 `broadcast`로 전송한다.
DB 조회로 방 이벤트 스레드를 막지 않는다. `onLeave`, `onRoomEmpty`와 서버 종료 시 타이머·자원을 정리한다.
`GameEvents` Bean이 없거나 `runLobbyServer`로 실행하면 게임 로직이 없어 카운트다운까지만 제공한다.

## 대기실 메시지 제안

UTF-8 한 줄 메시지, 구분자 `|`, 최대 4096자. 아래 규약은 팀 리뷰를 위한 제안이다.

| 방향 | 메시지 |
|---|---|
| 입장 요청 / 응답 | `JOIN|닉네임|캐릭터번호` / `WELCOME|참가자ID` |
| 참가자 목록 | `PLAYERS|반장ID|인원수|ID|닉네임|캐릭터번호|...` |
| 설정 요청 / 공유 | `SET_SETTINGS|LOW/MEDIUM/HIGH|5/10/15/20|15/30` / `SETTINGS|난이도|문제수|초` |
| 시작 요청 / 진행 | `START` / `COUNTDOWN|3`, `COUNTDOWN|2`, `COUNTDOWN|1`, `GAME_START` |
| 시작 취소 / 퇴장 / 오류 | `START_CANCELLED|PLAYER_LEFT` / `QUIT` / `ERROR|오류코드` |
| 답 제출 / 결과 | `ANSWER|단어` / `ANSWER|닉네임|단어|O/X` |
| 문제 / 힌트 | `QUESTION|초성` / `HINT|뜻` |

기본 학칙은 하/10문제/30초다. 게임 중 새 입장과 설정 변경은 거부한다.
문제 타이머의 남은 시간 메시지와 게임 종료 후 대기실 복귀 규약은 아직 없다.
DB PR #5의 하/중/상은 `Difficulty.getLabel()`과 대응한다. 실제 출제·DB 연결은 별도 구현이 필요하다.
이순신 초상(ch4.png)은 PR #10 병합 전까지 "초상 준비 중"으로 표시하며, 기와·단청은 Java2D 재현이라 Figma 원본과 완전히 같지는 않다.
