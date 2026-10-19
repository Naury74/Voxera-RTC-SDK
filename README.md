<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/images/logo-dark.png">
    <img src="docs/images/logo-light.png" alt="Voxera" width="420">
  </picture>
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.naurylab/voxera-core"><img src="https://img.shields.io/maven-central/v/com.naurylab/voxera-core?label=Maven%20Central&color=007D69" alt="Maven Central"></a>
  <img src="https://img.shields.io/badge/minSdk-26-3DDC84" alt="minSdk 26">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License"></a>
</p>

**Voxera**는 Android 앱에 음성 통화와 영상 통화를 화면까지 한 번에 붙이는 SDK입니다.
앱은 LiveKit 호환 서버 주소와 수명이 짧은 토큰만 넘기면 됩니다. 연결, 재연결, 상태 관리, 통화 화면은 SDK가 맡고, 통화 서버와 토큰 발급은 앱이 운영합니다.

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/images/showcase-group4.png" width="200" alt="4명 영상 통화"><br><b>그룹 영상 통화</b></td>
    <td align="center" width="25%"><img src="docs/images/showcase-group9-dark.png" width="200" alt="9명 영상 통화, 어두운 테마"><br><b>9명, 어두운 테마</b></td>
    <td align="center" width="25%"><img src="docs/images/showcase-chat.png" width="200" alt="채팅 시트"><br><b>채팅</b></td>
    <td align="center" width="25%"><img src="docs/images/showcase-quality.png" width="200" alt="통화 품질 시트"><br><b>통화 품질</b></td>
  </tr>
</table>

화면 속 사람은 오프라인 데모가 코드로 그리는 일러스트입니다.

## 기능

- **통화 화면**: `VoxeraRoomScreen` 하나로 연결 중, 통화, 종료, 실패 화면을 띄웁니다. 1:1은 상대를 전체 화면에, 나를 끌어 옮기는 작은 창에 두고, 3명부터는 격자, 9명을 넘으면 쪽을 넘겨 봅니다. 누른 사람을 크게 봅니다.
- **영상 비율 맞춤**: 세로 휴대전화와 가로 노트북 영상이 섞여도, 조금만 잘리면 타일을 채우고 많이 잘리면 영상 전체를 보여 줍니다. 화면 공유는 늘 전체를 보여 줍니다.
- **통화 중 기능**: 채팅, 반응, 손들기, 화면 공유, 카메라 색 효과, 오디오 출력 전환(스피커, 수화부, 유선, 블루투스), 참가자 목록, 통화 품질
- **입장 전 화면**: 음성과 영상을 고르고, 카메라 미리보기와 마이크 레벨을 봅니다.
- **상태 관리**: 재연결 제한 시간, 만료된 토큰 다시 받기, 연타해도 어긋나지 않는 마이크와 카메라 상태를 SDK가 처리합니다.
- **백그라운드**: 앱이 뒤로 가도 통화가 이어지고, 통화 알림에서 끊을 수 있습니다. PiP, 귀에 대면 화면 끄기, 휴대전화가 오면 보류했다 이어 가기를 지원합니다.
- **걸고 받기(Call Mode)**: 걸려 온 통화와 거는 통화 화면, 초대 상태 정리, 푸시 payload 파서. 초대 전송은 앱 서버가 맡습니다.
- **호환**: 채팅은 LiveKit 표준 형식이라 LiveKit Meet 같은 다른 클라이언트와도 주고받습니다.
- **접근성과 테마**: TalkBack, 글꼴 200%, 320dp 폭 화면, 고대비. 밝은 테마와 어두운 테마, 브랜드 색을 지원하고 앱의 `MaterialTheme`는 건드리지 않습니다.
- **데모와 테스트**: 네트워크 없이 도는 데모 통화와 가짜 엔진이 있습니다. Java 앱은 `VoxeraJava`를 씁니다.

## 샘플 앱

[voxera-showcase.apk](https://github.com/Naury74/Voxera-RTC-SDK/releases/latest/download/voxera-showcase.apk)를 받으면 소스 없이 통화 화면을 써 볼 수 있습니다. 네트워크 없이 도는 데모 통화(1:1, 4명, 9명, 12명, 화면 공유, 불안정한 네트워크)와 걸고 받기 데모가 있고, 직접 운영하는 LiveKit 서버에 실제로 연결할 수도 있습니다. Android 8.0 이상에서 설치되고, 처음에는 "출처를 알 수 없는 앱" 설치를 허용해야 합니다.

## 설치

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // LiveKit Android가 쓰는 audioswitch는 JitPack에만 있습니다. 그 그룹만 받게 제한해 두세요.
        maven("https://jitpack.io") { content { includeGroup("com.github.davidliu") } }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.naurylab:voxera-livekit-compose:0.1.0-alpha01") // LiveKit 엔진 + 통화 화면
    implementation("com.naurylab:voxera-android:0.1.0-alpha01")         // 백그라운드 유지, PiP, 통화 알림
}
```

LiveKit으로 통화하고 Voxera 화면을 쓴다면 위 두 모듈이면 됩니다. 모든 모듈은 같은 버전을 씁니다.

| 모듈 | 용도 |
| --- | --- |
| `voxera-livekit-compose` | LiveKit 영상을 통화 화면에 그리는 렌더러. `voxera-livekit`과 `voxera-ui-compose`를 함께 가져옵니다 |
| `voxera-livekit` | LiveKit Android SDK로 통화하는 엔진, 토큰 공급자 |
| `voxera-ui-compose` | 통화 화면, 입장 전 화면, 걸고 받기 화면, 테마 |
| `voxera-android` | 백그라운드 유지, 통화 알림, PiP, 근접 화면 끄기, 시스템 통화 등록 |
| `voxera-invitation` | 걸고 받기 계약과 상태 정리, 푸시 payload 파서 |
| `voxera-testkit` | 오프라인 데모와 테스트용 가짜 엔진 |
| `voxera-core`, `voxera-rtc-api` | 통화 세션과 모델. 위 모듈이 알아서 가져옵니다 |

minSdk 26, Java 17, Kotlin 2.2 이상에서 씁니다. 따로 R8 규칙을 넣지 않아도 됩니다.

## 사용법

```kotlin
val engines = LiveKitRtcEngineFactory(applicationContext)
val voxera = Voxera.create(engines)          // 앱에서 하나
val session = voxera.createSession(
    VoxeraSessionConfig(credentials = RtcCredentialsProvider { request ->
        myBackend.liveKitCredentials(room = request.roomId) // 앱 서버가 짧은 토큰을 발급
    }),
)

scope.launch { session.join("support-42", CallMode.VIDEO) }

CompositionLocalProvider(LocalVoxeraVideoRenderer provides engines.videoTracks.asComposeRenderer()) {
    VoxeraRoomScreen(session = session, onExit = { navController.popBackStack() })
}
```

통화를 마치면 `session.leave()`, 세션을 다 쓰면 `session.close()`를 부릅니다.

서버 없이 화면만 보려면 데모 세션을 씁니다. 화면에는 늘 `DEMO` 표시가 붙습니다.

```kotlin
val session = VoxeraDemo.createFakeSession(DemoScenario.GROUP_FOUR)
scope.launch { session.join(VoxeraDemo.ROOM_ID, DemoScenario.GROUP_FOUR.mode) }
VoxeraRoomScreen(session = session, onExit = { })
```

입장 전 화면, 화면 공유, PiP, 통화 알림, 채팅을 직접 다루는 방법은 [연동 가이드](docs/integration.md)에 있습니다.

## 주의사항

- 통화 서버(직접 운영하는 LiveKit 서버나 LiveKit Cloud)와 토큰을 발급하는 앱 서버가 필요합니다. API Secret은 앱에 넣지 마세요.
- 기본은 `wss://`만 받습니다. 로컬 개발 서버의 `ws://`는 `ServerUrlPolicy(allowInsecureLocalhost = true)`로 debug 빌드에서만 켜세요.
- SDK는 토큰을 메모리에만 두고 로그와 디스크에 남기지 않습니다. 자세한 내용은 [SECURITY.md](SECURITY.md)에 있습니다.
- 유리 표면의 블러는 Android 12 이상에서 보이고, 그 아래와 배터리 절약 모드에서는 반투명한 색으로 그립니다.
- 카메라 효과는 밝기, 대비, 채도 조절입니다. 배경 흐림은 없습니다. 다른 사람을 내보내는 진행자 기능도 없습니다(LiveKit 서버 API로 앱 서버에서 하세요).
- 화면 문구는 영어와 한국어입니다. 앱 언어만 남기려면 `androidResources.localeFilters`를 쓰세요.
- 통화 화면은 [Kinetica](https://github.com/Naury74/Kinetica-Kit-SDK) UI 키트로 그립니다. 앱이 kinetica-ui를 직접 쓴다면 1.0.0-alpha05 이상을 쓰세요.
- 알파 버전이라 공개 API가 바뀔 수 있습니다.

## 문서

[연동 가이드](docs/integration.md) · [권한](docs/permissions.md) · [아키텍처](docs/architecture.md) · [검증 기록](docs/verification.md) · [변경 이력](CHANGELOG.md) · [기여](CONTRIBUTING.md) · [배포](docs/publishing.md)

## License

Apache License 2.0으로 배포합니다. 자세한 내용은 [LICENSE](LICENSE)를 보세요. 일부 아이콘은 [Material Icons](https://github.com/google/material-design-icons)를 씁니다([NOTICE](NOTICE)).
