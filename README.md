# 초성서당 (Cho-Seong-School)

초성 힌트를 보고 단어를 맞히는 멀티플레이어 퀴즈 게임입니다. 최대 5명이 같은 대기실에 모여 반장이 정한 학칙(난이도·문제 수·풀이 시간)으로 겨룹니다.
JAVA프로그래밍및실습II 팀 프로젝트(집현전)입니다.

## 기술 스택

- Java 21, Gradle
- 화면: Swing
- 통신: Socket(TCP), Thread
- 데이터: Spring Boot, Spring Data JPA, MariaDB (단어 조회)

## 화면 흐름

로그인 → 대기실(학칙 설정, 3-2-1 카운트다운) → 게임(문제·힌트·정답 입력) → 결과 → 대기실 또는 로그인

## 실행 방법

Java 21이 필요하고, 프로젝트 루트에서 실행합니다.

```powershell
.\gradlew.bat clean build
.\gradlew.bat runServer
.\gradlew.bat runClient
```

- `runServer`: DB 연결과 대기실 TCP 서버(기본 포트 50024)를 함께 실행합니다. DB 계정은 프로젝트 루트의 `application-secret.properties`(git 제외)나 환경 변수 `DB_USERNAME`, `DB_PASSWORD`로 넣습니다.
- `runClient`: 게임 창을 엽니다. 같은 Wi-Fi의 다른 PC에서도 서버 주소를 입력해 접속할 수 있습니다.
- DB 없이 대기실만 확인하려면 `.\gradlew.bat runLobbyServer`를 사용합니다. 이때는 게임 로직이 연결되지 않습니다.

대기실 메시지 규약과 게임 연결 방법은 [docs/lobby.md](docs/lobby.md)에 있습니다.

## 패키지 구조

패키지는 역할별로 나뉩니다. 클라이언트(Swing)와 서버(Spring + 소켓)가 한 프로젝트에 있어서 아래 표로 위치를 구분합니다.

| 패키지 | 역할 | 쓰는 쪽 |
|---|---|---|
| `view` | 로그인·대기실·게임·결과 화면을 그리기만 함 | 클라이언트 |
| `controller` | 화면 이벤트와 서버 메시지를 받아 상태를 바꾸고 화면 갱신 | 클라이언트 |
| `model` | 참가자, 대기실 상태, 학칙, 난이도 등 데이터와 규칙 | 둘 다 |
| `client` | 화면 전환과 서버 접속을 조립 (`ClientMain`이 시작점) | 클라이언트 |
| `network` | 한 줄 단위 TCP 송수신 | 둘 다 |
| `server` | 대기실 소켓 서버, 게임 로직 연결점(`GameEvents`) | 서버 |
| `config` · `entity` · `repository` · `service` | Spring 설정과 단어 DB 조회 | 서버 |
| `common` | 공용 상수와 화면 테마 | 둘 다 |
| `timer` | 라운드 타이머(아직 게임 로직에 연결되지 않음) | 서버 |

## 설계 요약

- **MVC**: 대기실은 `LobbyState`(모델), `NetworkLobbyController`(컨트롤러), `LobbyView`(뷰)로 나뉩니다. 뷰는 상태를 들고 있지 않고 `render`로 그리기만 합니다.
- **인터페이스로 분리**: `GameEvents`(서버가 게임 로직을 부르는 통로), `GameScreenListener`(대기실이 게임 화면에 넘기는 통로), `LoginListener`로 서로의 구현을 몰라도 연결됩니다.
- **소켓 서버**: 수업의 채팅 서버처럼 접속마다 Thread를 만들고, 모두가 함께 쓰는 방 상태는 `synchronized`로 한 번에 한 Thread만 바꿉니다.

## 팀

집현전 (4명). 작업은 이슈 → 브랜치 → PR 순서로 진행합니다.
