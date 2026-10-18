# Changelog

## 0.1.0-alpha01

첫 공개 버전입니다. 알파 동안에는 공개 API가 바뀔 수 있습니다.
모든 모듈은 `com.naurylab:voxera-*:0.1.0-alpha01` 좌표로 함께 배포합니다.

### 통화 세션 (`voxera-core`, `voxera-rtc-api`)

- `Voxera.create(engines)`로 SDK를 만들고 `createSession`으로 통화 세션을 엽니다. 세션 하나가 입장부터 종료까지를 맡습니다.
- 상태는 `Idle → Preparing → Connecting → Connected ⇄ Reconnecting → Ending → Ended`이고, 실패하면 `Failed`입니다. 입장할 때마다 세대 번호가 올라가서, 이전 연결의 늦은 이벤트가 새 통화를 흔들지 않습니다.
- 재연결은 엔진에 맡기고, SDK는 제한 시간(기본 30초)만 잽니다. 넘기면 `Failed`가 되고 다시 입장할 수 있습니다.
- 토큰은 호스트가 `RtcCredentialsProvider`로 넘깁니다. 만료되면 `REAUTHENTICATE` 이유로 한 번만 다시 요청하고, 호스트 인증 실패(401/403)는 다시 시도하지 않습니다.
- 마이크, 카메라, 화면 공유는 원하는 상태와 실제로 반영된 상태를 함께 알려 줍니다. 버튼을 빠르게 눌러도 마지막 의도가 반영됩니다.
- 한 SDK에서는 한 번에 한 통화만 미디어를 씁니다.
- 채팅(`chat`, `sendChatMessage`), 손들기(`setHandRaised`), 반응 6종(`sendReaction`)을 주고받습니다. 받은 글은 길이와 보내는 빈도를 제한합니다.
- 진단 리포트(`SessionDiagnostics`)는 상태 변화와 품질 표본을 JSON으로 만듭니다. 토큰, 서버 주소, 룸 이름, 참가자 이름은 넣지 않습니다.
- Java 호스트는 `VoxeraJava`로 콜백 방식 API를 씁니다.

### LiveKit 엔진 (`voxera-livekit`, `voxera-livekit-compose`)

- LiveKit Android 2.29.0으로 실제 통화를 합니다. 그룹 통화에서는 `adaptiveStream`과 `dynacast`를 켜서 작은 타일과 화면 밖 타일은 낮은 화질만 받습니다.
- 토큰 공급자 `LiveKitTokenSources`: 호스트 토큰 엔드포인트, 이미 받아 둔 토큰, LiveKit Cloud 개발 토큰 서버(테스트 전용)
- 화면 공유, 오디오 출력 전환(스피커, 수화부, 유선, 블루투스), 통화 품질 통계(왕복 시간, 지터, 손실, 비트레이트, 프레임)
- 카메라 색 효과(밝기, 대비, 채도). 처리가 늦어지거나 기기가 뜨거워지면 원본 영상으로 돌아갑니다.
- 채팅은 LiveKit 표준 채팅과 같은 텍스트 스트림(주제 `lk.chat`)이라 LiveKit Meet 같은 다른 클라이언트와도 주고받습니다.
- `asComposeRenderer()`로 LiveKit 영상을 Voxera 통화 화면에 그립니다. 받은 영상의 프레임 크기를 회전까지 반영해 화면에 알려 줍니다.

### 통화 화면 (`voxera-ui-compose`)

- `VoxeraRoomScreen`: 연결 중, 통화, 종료, 실패 화면을 상태에 맞춰 바꿉니다.
  - 1:1 영상은 상대를 전체 화면에, 나를 끌어 옮기는 작은 창에 둡니다. 3명부터는 격자이고, 9명을 넘으면 쪽을 넘겨 봅니다.
  - 타일을 누르면 그 사람을 크게 보고, 나머지는 아래 줄에서 옆으로 넘겨 봅니다. 갤러리 보기와 말하는 사람을 크게 보는 보기를 오갑니다.
  - 참가자마다 영상 비율이 달라도 조금만 잘리면 타일을 채우고, 많이 잘리면 영상 전체를 보여 줍니다. 화면 공유는 늘 전체를 보여 줍니다.
  - 음성 그룹 통화는 아바타 격자이고, 사람이 많으면 위아래로 넘깁니다.
  - 조작 막대, 상단 바, 채팅 시트, 반응과 손들기, 참가자 목록, 오디오 출력 시트, 품질 시트와 추이 그래프, 카메라 효과 시트
  - 참가자 입장과 퇴장, 채팅, 오디오 장치 연결과 끊김을 화면 위 알림으로 알려 줍니다.
  - 영상 통화에서는 4초 동안 조작이 없으면 조작 막대를 숨깁니다. TalkBack이 켜져 있으면 숨기지 않습니다.
- `VoxeraPrejoinScreen`: 입장 전에 음성과 영상을 고르고, 카메라 미리보기와 마이크 레벨을 봅니다. 권한은 이 화면에서 켤 때만 묻습니다.
- `VoxeraCallScreen`: 걸려 온 통화와 거는 통화 화면
- `VoxeraTheme`: 밝은 테마와 어두운 테마, 고대비, 색 덮어쓰기. 덮어쓴 색이 글자 4.5:1, 강조 그래픽 3:1 대비에 못 미치면 기본색을 씁니다. 호스트의 `MaterialTheme`는 건드리지 않습니다.
- 유리 표면은 Android 12 이상에서 뒤의 영상을 실제로 흐리게 비추고, 그 아래 버전과 배터리 절약 모드에서는 반투명 단색으로 그립니다. 고대비에서는 불투명하게 그립니다.
- 시트, 알림, 스위치, 말풍선, 그래프, 움직임은 Kinetica UI 키트(`com.naurylab:kinetica-ui`)로 그립니다. Voxera 공개 API에는 Kinetica 타입이 나오지 않습니다.
- 한국어와 영어 문구, TalkBack 읽기 순서와 동작 이름, 글꼴 200%, 320dp 폭 화면을 지원합니다. 시스템의 '애니메이션 삭제'를 켜면 반복 움직임을 끕니다.

### Android 통합 (`voxera-android`)

- `VoxeraForegroundCall`: 통화 중 앱이 뒤로 가도 마이크와 카메라가 끊기지 않게 foreground service를 띄웁니다. 통화 알림에는 경과 시간과 종료 버튼이 있고, 상황에 따라 아이콘 배지와 문구가 바뀝니다.
- `VoxeraPictureInPicture`: 홈으로 나가면 작은 창으로 바꾸고, 창에 마이크 버튼과 종료 버튼을 둡니다.
- `VoxeraProximityScreenOff`: 수화부로 들을 때 귀에 대면 화면을 끕니다.
- `VoxeraTelecomCall`: 통화를 Android 통화 시스템에 올립니다. 휴대전화가 오면 보류하고, 끝나면 이어 갑니다.

### Call Mode (`voxera-invitation`)

- 전화처럼 걸고 받는 흐름의 계약 `InvitationGateway`와 상태를 정리하는 `CallCoordinator`, 푸시 payload 파서 `InvitationPayloads`
- 같은 통화의 중복되거나 순서가 뒤바뀐 이벤트는 revision으로 걸러 냅니다. 벨 제한 시간이 지나면 부재중으로 끝납니다.
- 초대와 푸시 전송은 호스트 서버가 맡습니다. payload에는 토큰을 넣지 않습니다.

### 테스트 도구 (`voxera-testkit`)

- `FakeRtcEngine`: 가상 시간으로 도는 가짜 엔진. 참가자, 말하기, 네트워크 끊김을 흉내 냅니다.
- `VoxeraDemo`와 `DemoScenario`: 네트워크 없이 도는 데모 통화(1:1 음성과 영상, 4명, 9명, 12명, 화면 공유, 불안정한 네트워크). 데모 화면에는 늘 `DEMO` 표시가 붙습니다.

### 알려진 제한

- 배경 흐림 같은 카메라 배경 효과는 없습니다. 색 효과만 있습니다.
- 진행자 기능(다른 사람의 마이크 끄기, 내보내기)은 없습니다. LiveKit 서버 API가 필요한 일이라 호스트 서버가 맡아야 합니다.
- `apiCheck`는 Kotlin JVM 모듈(rtc-api, core, invitation, testkit)의 공개 API만 검사합니다. Android 모듈은 검사 도구가 AGP 9의 내장 Kotlin을 아직 지원하지 않습니다.
- 확인한 기기와 하지 못한 항목은 [docs/verification.md](docs/verification.md)에 있습니다.
