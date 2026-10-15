# Voxera RTC SDK

<p>
  <img src="https://img.shields.io/badge/minSdk-26-3DDC84" alt="minSdk 26">
  <img src="https://img.shields.io/badge/status-0.1.0--alpha01%20(개발%20중)-orange" alt="status">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License"></a>
</p>

Voxera는 Android 앱에 음성·영상 통화를 화면째로 붙이는 RTC SDK입니다. 호스트 앱이 자기 LiveKit 호환 서버 주소와
수명이 짧은 토큰만 넘기면 룸 통화를 할 수 있게 만들고 있습니다. 통화 서버와 토큰 발급은 호스트가 맡고, SDK 쪽에서 운영하는 서버는 없습니다.

> 지금은 M1(vertical RTC) 단계입니다. 오프라인 쇼케이스, 세션 상태 머신, Glass UI가 있고 실제 LiveKit 연결도 됩니다
> (BYO 설정, 음성과 영상, 음소거, 재연결, 품질 진단). 진행 표의 "검증" 칸에는 실제로 돌려 본 것만 적었습니다([docs/verification.md](docs/verification.md)).

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/images/showcase-home.png" width="200" alt="홈"><br><b>홈</b><br>오프라인 쇼케이스</td>
    <td align="center" width="25%"><img src="docs/images/showcase-voice.png" width="200" alt="1:1 음성"><br><b>1:1 음성</b><br>말할 때 퍼지는 물결, 연결 상태</td>
    <td align="center" width="25%"><img src="docs/images/showcase-group9-dark.png" width="200" alt="9인 그룹 크게 보기"><br><b>9인 그룹 (다크)</b><br>누른 사람 크게 보기</td>
    <td align="center" width="25%"><img src="docs/images/showcase-quality.png" width="200" alt="통화 품질"><br><b>통화 품질</b><br>상태 요약과 추이 그래프</td>
  </tr>
</table>

스크린샷 속 영상은 데모용 사람 윤곽 그림입니다. 실제 인물 사진은 SDK에 넣지 않았습니다.

## 모듈

| 모듈 | 종류 | 내용 |
| --- | --- | --- |
| `voxera-rtc-api` | Kotlin JVM | 엔진 중립 모델(`SessionState`, `Participant`, `VideoTrackHandle`, `QualitySnapshot`), `VoxeraError`, `RtcEngine` 포트, 서버 주소·토큰 입력 검사 |
| `voxera-core` | Kotlin JVM | `Voxera.create`, `VoxeraSession`, 세대 기반 상태 reducer |
| `voxera-livekit` | Android | LiveKit Android 2.29.0 기반 `LiveKitRtcEngineFactory`, 토큰 공급자(`LiveKitTokenSources`), 영상 트랙 조회 |
| `voxera-livekit-compose` | Compose | LiveKit 영상을 `VoxeraRoomScreen`에 그리는 렌더러 |
| `voxera-invitation` | Kotlin JVM | Call Mode: `InvitationGateway` 계약, `CallCoordinator`, 푸시 payload 파서 |
| `voxera-android` | Android | 통화 중 백그라운드 유지(`VoxeraForegroundCall`, foreground service·알림) |
| `voxera-testkit` | Kotlin JVM | `FakeRtcEngine`(가상 시간), `VoxeraDemo`·`DemoScenario` 오프라인 데모 |
| `voxera-ui-compose` | Compose | `VoxeraTheme`(Light/Dark Glass 토큰), `VoxeraRoomScreen`, 조작 막대·참가자 타일 |
| `app` | Showcase | 오프라인 쇼케이스. 배포하지 않음 |

구조와 설계 결정은 [docs/architecture.md](docs/architecture.md), 연동 방법은 [docs/integration.md](docs/integration.md), 변경 이력은 [docs/release-notes.md](docs/release-notes.md), 보안 정책은 [SECURITY.md](SECURITY.md)에 있습니다.

## 빠른 시작 (오프라인 데모)

```kotlin
val session = VoxeraDemo.createFakeSession(DemoScenario.GROUP_FOUR)
scope.launch { session.join(VoxeraDemo.ROOM_ID, DemoScenario.GROUP_FOUR.mode) }

// Compose
VoxeraRoomScreen(session = session, onExit = { /* 호스트 내비게이션 */ })
```

데모 세션은 네트워크를 전혀 쓰지 않고, 화면에는 늘 `DEMO` 표시가 붙습니다. 품질 값도 `MetricSource.SIMULATED`로 나옵니다.

## 실제 통화 (호스트 토큰 공급자)

```kotlin
val engines = LiveKitRtcEngineFactory(applicationContext)
val sdk = Voxera.create(engines)
val session = sdk.createSession(
    VoxeraSessionConfig(credentials = RtcCredentialsProvider { request ->
        myBackend.getLiveKitCredentials(room = request.roomId) // 호스트 서버가 짧은 토큰 발급
    }),
)

// Compose: 실제 영상 렌더러를 넘긴다.
CompositionLocalProvider(LocalVoxeraVideoRenderer provides engines.videoTracks.asComposeRenderer()) {
    VoxeraRoomScreen(session = session, onExit = { /* 호스트 내비게이션 */ })
}
```

마이크와 카메라 권한은 사용자가 입장 버튼을 누른 직후에 호스트가 요청합니다. manifest에 합쳐지는 권한과 런타임 요청 방식은 [docs/permissions.md](docs/permissions.md)에 정리했습니다.

보안 기본값은 이렇습니다.

- API Secret과 Signing Key는 앱, SDK, 저장소 어디에도 두지 않습니다. 토큰은 호스트 서버가 발급합니다.
- 기본은 `wss://`만 받습니다. 로컬 개발 주소의 `ws://`는 `ServerUrlPolicy(allowInsecureLocalhost = true)`일 때만 씁니다.
- SDK는 토큰을 저장하지 않고, `RtcCredentials.toString()`에서도 토큰을 가립니다.
- 토큰이 만료되면 `CredentialReason.REAUTHENTICATE`로 딱 한 번 다시 요청합니다. 호스트 인증 실패(401/403)는 재시도하지 않습니다.

## 진행 현황

| 티켓 | 내용 | 상태 | 검증 |
| --- | --- | --- | --- |
| VOX-001 | Gradle 멀티모듈·버전 카탈로그 | 완료 | `test lint assembleDebug assembleRelease` 통과 |
| VOX-002 | API 모델·오류 체계 | 완료 | JVM 단위 테스트. binary API dump는 아직 없음 |
| VOX-003 | 세션 상태 reducer | 완료 | 전이·경합 JVM 테스트 |
| VOX-004 | Fake RTC Engine | 완료 | 1/3/8명 원격 참가자 시뮬레이션 테스트 |
| VOX-005 | 오프라인 쇼케이스 | 완료 | 에뮬레이터(API 36, 폴더블)에서 시나리오 5개 직접 확인 |
| VOX-006 | 자격 증명·URL 검사 | 완료 | JVM 단위 테스트 |
| VOX-UI-001~003 | Glass 토큰·유리 표면·불투명 폴백 | 완료 | 화면 색은 모두 토큰. 배경 흐림 대신 반투명 단색과 외곽선을 쓰며 API 26~36 에뮬레이터와 Fold7에서 같은 모습. 대비 테스트 통과 |
| VOX-007~009 | LiveKit 연결·참가자·마이크/카메라 어댑터 | 완료 | 매핑 JVM 테스트, 에뮬레이터와 로컬 LiveKit으로 실통화 |
| VOX-010 | BYO 설정(개발 토큰 서버, 기존 토큰, endpoint) | 부분 | 기존 토큰과 endpoint는 로컬 서버와 LiveKit Cloud(wss, release 빌드 포함)로 확인. 개발 토큰 서버(Sandbox)는 확인 안 함 |
| VOX-011 | 입장 전 화면(권한, 카메라 미리보기) | 완료 | 거부, 영구 거부, 설정에서 돌아오기를 에뮬레이터에서 확인. 마이크 레벨 막대는 Fold7에서 확인 |
| VOX-019 | 백그라운드 통화 유지, PiP, Telecom | 완료 | foreground service로 녹음 유지. PiP 자동 진입, 마이크 버튼, 복귀는 Fold7 실통화로 확인. Telecom 등록(core-telecom)으로 휴대전화가 오면 보류·마이크 끄기, 끝나면 이어 가기를 에뮬레이터에서 확인 |
| VOX-022~024 | Call Mode 계약, 가짜 호스트 샘플, 수신 화면 | 완료 | 경합 JVM 테스트, 데모 화면 확인. 실제 호스트 서버와 푸시는 확인 안 함 |
| VOX-025 | Java facade | 완료 | Java 소스 테스트 |
| VOX-014 | 실제 영상 렌더링 | 완료 | 에뮬레이터에서 원격 영상과 내 영상 확인 |
| VOX-016 | 재연결, 오류 화면 | 완료 | 서버를 강제로 끄면 Reconnecting → Failed, 다시 시도까지 확인 |
| VOX-020 | 품질 진단(실제 통계) | 완료 | 샘플러 JVM 테스트, 실통화에서 값 표시 확인 |
| VOX-018 | 화면 공유 | 완료 | 에뮬레이터 실통화에서 시작, 승인 취소, 시스템에서 중지 확인 |
| VOX-021 | 진단 리포트(로컬 JSON) | 완료 | 민감한 값이 빠지는지 JVM 테스트, 실제 저장된 파일 확인 |
| VOX-017 | 카메라 색 효과(밝기, 대비, 채도) | 완료 | 효과 켜기·끄기, 성능·발열 시 원본으로 폴백, 처리 시간 지표. Fold7에서 프레임당 5.0~7.8ms(예산 15ms). 배경 흐림은 v1.1 범위 |
| VOX-026 | 접근성, 작은 화면, 큰 글꼴 | 부분 | 320dp·글꼴 200% 테스트와 에뮬레이터 확인, TalkBack 읽기 순서 지정. TalkBack을 켜고 직접 써 보지는 않음 |
| VOX-028 | 배포 리허설 | 부분 | 로컬 Maven과 `samples/consumer`로 확인, POM에 Apache 2.0 라이선스. Maven Central 게시는 안 함 |
| VOX-027 | 기기·API 매트릭스 | 완료 | 에뮬레이터 API 26·30·31·33·34·35·36과 Fold7(API 36)에서 PiP, 종료 화면, 실통화, 통화 서비스 확인. 크래시 없음 |
| VOX-015 | 장치 전환 실기기 검증 | 부분 | Fold7에서 스피커↔휴대전화↔블루투스 전환, 블루투스 끊김과 재연결, 백그라운드 마이크 확인. 유선은 안 함 |
| VOX-029 | 공개 문서·라이선스 | 완료 | Apache 2.0(LICENSE, NOTICE, POM), 연동 가이드, 릴리스 노트, SECURITY.md |
| 추가 | 통화 중 반응, 통화 알림(경과 시간, 종료), 수화부 근접 화면 끄기, 발열과 다른 통화 대응 | 부분 | 반응 보내기, 알림에서 종료, 다른 통화 안내는 에뮬레이터 실통화로 확인. 실통화 반응 받기와 근접 센서는 실기기가 필요 |
| 추가 | 채팅, 손들기, 그룹 통화 화질 조절(adaptiveStream·dynacast), 넘기는 작은 줄, 오디오 장치 알림 | 완료 | 데모와 로컬 서버 에뮬레이터 2대 실통화로 채팅·손들기·늦게 온 사람의 손 상태 확인. 실제 블루투스 이어폰 연결 알림은 실기기 필요 |

## 빌드

JDK는 Android Studio에 들어 있는 JBR을 씁니다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test lint :app:assembleDebug :app:assembleRelease
```

AGP 9.4.1, Kotlin 2.4.20, compileSdk/targetSdk 37, minSdk 26. 의존성 버전은 `gradle/libs.versions.toml` 한 곳에서만 바꿉니다.

## 알려진 제한

- 실기기는 Fold7 한 대뿐이라, 2대 통화는 Fold7과 에뮬레이터를 LiveKit Cloud로 연결해 확인했습니다. 실제 폰 두 대 사이의 통화와 유선 이어폰 전환은 아직 확인하지 않았습니다.
- 배경 흐림(backdrop blur)은 없습니다. 영상 Surface 뒤를 흐리게 할 마땅한 방법이 Android에 없어서 반투명 tint와 테두리로 대신했습니다.
- TalkBack, 글꼴 200%, 태블릿 레이아웃은 기본 semantics와 최소 터치 영역까지만 맞췄습니다. 기기별로 점검하지는 않았습니다.

## License

Apache License 2.0으로 배포합니다. 전문은 [LICENSE](LICENSE)에 있습니다. 일부 아이콘은 [Material Icons](https://github.com/google/material-design-icons)에서 가져왔습니다([NOTICE](NOTICE)).
