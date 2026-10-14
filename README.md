# Voxera RTC SDK

<p>
  <img src="https://img.shields.io/badge/minSdk-26-3DDC84" alt="minSdk 26">
  <img src="https://img.shields.io/badge/status-0.1.0--alpha01%20(개발%20중)-orange" alt="status">
</p>

**Voxera**는 Android 앱에 음성·영상 통화 화면을 붙이는 UI 포함 RTC SDK입니다. 호스트 앱이 자신의 LiveKit 호환 서버 주소와
짧은 수명 토큰을 넘기면 룸 통화를 할 수 있도록 만드는 것이 목표입니다. SDK 제공자는 통화 서버를 운영하거나 토큰을 발급하지 않습니다.

> 현재 단계: **M0 foundation**. 서버 없이 도는 오프라인 쇼케이스와 세션 상태 머신, Glass UI가 들어 있습니다.
> 실제 LiveKit 연결(`voxera-livekit`)은 아직 없습니다. 아래 표의 "검증"은 실제로 돌린 테스트만 적습니다.

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/images/showcase-home.png" width="200" alt="홈"><br><b>홈</b><br>오프라인 쇼케이스</td>
    <td align="center" width="25%"><img src="docs/images/showcase-voice.png" width="200" alt="1:1 음성"><br><b>1:1 음성</b><br>발화 고리·통화 시간</td>
    <td align="center" width="25%"><img src="docs/images/showcase-group9-dark.png" width="200" alt="9인 그룹"><br><b>9인 그룹 (다크)</b><br>3×3 격자·발화자 강조</td>
    <td align="center" width="25%"><img src="docs/images/showcase-quality.png" width="200" alt="통화 품질"><br><b>통화 품질</b><br>송수신 분리·N/A·데모 표시</td>
  </tr>
</table>

화면의 영상은 데모용 추상 그라디언트이며 인물 이미지는 번들하지 않습니다.

## 모듈

| 모듈 | 종류 | 내용 |
| --- | --- | --- |
| `voxera-rtc-api` | Kotlin JVM | 엔진 중립 모델(`SessionState`, `Participant`, `VideoTrackHandle`, `QualitySnapshot`), `VoxeraError`, `RtcEngine` 포트, 서버 주소·토큰 입력 검사 |
| `voxera-core` | Kotlin JVM | `Voxera.create`, `VoxeraSession`, 세대 기반 상태 reducer |
| `voxera-testkit` | Kotlin JVM | `FakeRtcEngine`(가상 시간), `VoxeraDemo`·`DemoScenario` 오프라인 데모 |
| `voxera-ui-compose` | Compose | `VoxeraTheme`(Light/Dark Glass 토큰), `VoxeraRoomScreen`, 조작 막대·참가자 타일 |
| `app` | Showcase | 오프라인 쇼케이스. 배포하지 않음 |

구조와 결정 사항은 [docs/architecture.md](docs/architecture.md)에 있습니다.

## 빠른 시작 (오프라인 데모)

```kotlin
val session = VoxeraDemo.createFakeSession(DemoScenario.GROUP_FOUR)
scope.launch { session.join(VoxeraDemo.ROOM_ID, DemoScenario.GROUP_FOUR.mode) }

// Compose
VoxeraRoomScreen(session = session, onExit = { /* 호스트 내비게이션 */ })
```

데모 세션은 네트워크에 연결하지 않으며 화면에 항상 `DEMO`를 표시합니다. 품질 값은 `MetricSource.SIMULATED`입니다.

## 호스트 토큰 공급자 (목표 사용법)

```kotlin
val sdk = Voxera.create(engineFactory) // 실제 통화용 LiveKit factory는 다음 단계(VOX-007)
val session = sdk.createSession(
    VoxeraSessionConfig(credentials = RtcCredentialsProvider { request ->
        myBackend.getLiveKitCredentials(room = request.roomId) // 호스트 서버가 짧은 토큰 발급
    }),
)
```

- API Secret·Signing Key를 앱·SDK·저장소에 넣지 않습니다. 토큰은 호스트 서버가 발급합니다.
- 기본으로 `wss://`만 허용하고, `ServerUrlPolicy(allowInsecureLocalhost = true)`일 때만 로컬 개발 주소에 `ws://`를 씁니다.
- `RtcCredentials.toString()`은 토큰을 가립니다. SDK는 토큰을 저장하지 않습니다.
- 만료 토큰은 `CredentialReason.REAUTHENTICATE`로 한 번만 다시 요청하고, 호스트 인증 실패(401/403)는 다시 시도하지 않습니다.

## 진행 현황

| 티켓 | 내용 | 상태 | 검증 |
| --- | --- | --- | --- |
| VOX-001 | Gradle 멀티모듈·버전 카탈로그 | 완료 | `test lint assembleDebug assembleRelease` 통과 |
| VOX-002 | API 모델·오류 체계 | 완료 | JVM 단위 테스트. binary API dump는 아직 없음 |
| VOX-003 | 세션 상태 reducer | 완료 | 전이·경합 JVM 테스트 |
| VOX-004 | Fake RTC Engine | 완료 | 1/3/8명 원격 참가자 시뮬레이션 테스트 |
| VOX-005 | 오프라인 쇼케이스 | 완료 | 에뮬레이터(API 36, 폴더블)에서 5가지 시나리오 수동 확인 |
| VOX-006 | 자격 증명·URL 검사 | 완료 | JVM 단위 테스트 |
| VOX-UI-001~003 | Glass 토큰·유리 표면·불투명 폴백 | 부분 | 대비 테스트 통과. API 26/30 실기기 확인 안 함 |
| VOX-007 이후 | LiveKit 연결, BYO 설정, 사전 대기 화면, 화면 공유, 초대 등 | 미착수 | - |

## 빌드

JDK는 Android Studio에 포함된 JBR을 씁니다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test lint :app:assembleDebug :app:assembleRelease
```

AGP 9.4.1, Kotlin 2.4.20, compileSdk/targetSdk 37, minSdk 26. 버전은 `gradle/libs.versions.toml`에서만 관리합니다.

## 알려진 제한

- 실제 RTC 연결이 없어 2기기 통화·재연결·장치 라우팅은 검증하지 않았습니다.
- 배경 흐림(backdrop blur)은 하지 않습니다. 영상 Surface 뒤를 흐리게 하는 보편적인 Android 방법이 없어 반투명 tint·테두리로 대신합니다.
- TalkBack·글꼴 200%·태블릿 레이아웃은 기본 의미 정보와 최소 터치 영역만 반영했고 기기별 점검은 하지 않았습니다.
- 공개 라이선스는 아직 정하지 않았습니다([NOTICE](NOTICE)).
