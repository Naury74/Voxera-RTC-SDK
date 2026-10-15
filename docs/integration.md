# Integration

호스트 앱에 Voxera 통화 화면을 붙이는 방법입니다. 통화 서버(LiveKit 호환)와 토큰 발급은 호스트가 맡고, SDK는 연결·상태·화면을 맡습니다.

## 의존성

```kotlin
dependencies {
    implementation("com.naurylab:voxera-livekit-compose:0.1.0-alpha01") // LiveKit 엔진 + Compose 화면
    implementation("com.naurylab:voxera-android:0.1.0-alpha01")         // 백그라운드 유지, PiP, 근접 화면 끄기
    // implementation("com.naurylab:voxera-invitation:0.1.0-alpha01")   // Call Mode(초대·수신 화면)
    // implementation("com.naurylab:voxera-testkit:0.1.0-alpha01")      // 오프라인 데모·테스트용 가짜 엔진
}
```

LiveKit Android는 `audioswitch`를 JitPack에서 받습니다. JitPack은 그 그룹만 허용하세요.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("com.github.davidliu") } }
    }
}
```

- 최소 API 26. 호스트는 Kotlin 2.2 이상이면 됩니다(SDK는 언어·API 버전을 2.2로 맞춰 배포합니다).
- 매니페스트에 합쳐지는 권한과 서비스는 [permissions.md](permissions.md)에 있습니다.

## 세션 만들기

```kotlin
val engines = LiveKitRtcEngineFactory(applicationContext)
val sdk = Voxera.create(engines)            // 앱에서 하나
val session = sdk.createSession(
    VoxeraSessionConfig(
        credentials = RtcCredentialsProvider { request ->
            // 호스트 서버가 짧은 수명 토큰을 발급한다. request.roomId, request.reason을 참고한다.
            myBackend.liveKitCredentials(room = request.roomId)
        },
    ),
)

scope.launch {
    when (val result = session.join("support-42", CallMode.VIDEO)) {
        is JoinResult.Connected -> Unit
        is JoinResult.Rejected -> showError(result.error)
    }
}
```

- 같은 SDK에서는 한 번에 한 통화만 연결됩니다. 두 번째 `join`은 `VoxeraError.ConcurrentSession`입니다.
- 상태는 `session.state`(`Idle → Preparing → Connecting → Connected ⇄ Reconnecting → Ending → Ended/Failed`), 참가자는 `session.participants`, 내 미디어는 `session.localMedia`로 관찰합니다.
- 다 쓰면 `session.close()`.

### 토큰 공급자

| 공급자 | 용도 |
| --- | --- |
| `RtcCredentialsProvider { ... }` | 운영. 호스트 서버가 토큰을 발급 |
| `LiveKitTokenSources.endpoint(url, name, headers)` | 호스트의 토큰 endpoint에 POST(`room_name`, `participant_name`) → `server_url`, `participant_token` |
| `LiveKitTokenSources.literal(serverUrl, token)` | 이미 받은 토큰 하나 |
| `LiveKitTokenSources.developmentTokenServer(id, name)` | LiveKit Cloud 개발 토큰 서버. **테스트 전용** — ID만 알면 누구나 토큰을 받는다 |

- 토큰이 만료되면 `CredentialReason.REAUTHENTICATE`로 한 번만 다시 요청합니다. 호스트 인증 실패(401/403)는 다시 시도하지 않고 `HostUnauthorized`로 끝납니다.
- 기본으로 `wss://`만 허용합니다. 로컬 개발 서버는 `ServerUrlPolicy(allowInsecureLocalhost = true)`일 때만 `ws://10.0.2.2`, `ws://localhost`를 씁니다(debug 빌드에서만 켜세요).

## 통화 화면

```kotlin
CompositionLocalProvider(LocalVoxeraVideoRenderer provides engines.videoTracks.asComposeRenderer()) {
    VoxeraRoomScreen(
        session = session,
        onExit = { navController.popBackStack() },
        onRejoin = { scope.launch { session.join(roomId, mode) } },
        onShareScreen = rememberScreenShareLauncher(session),
        onSaveReport = { saveDiagnostics() },
        pictureInPicture = inPip,
        onEnterPictureInPicture = { pip.enter() },
    )
}
```

화면이 하는 일:

- 연결 중 → 통화 → 종료·실패 화면을 상태에 따라 바꿉니다. 실패 화면은 다시 해도 같은 결과인 오류에는 재시도 버튼을 숨깁니다.
- 영상 통화: 2명은 상대 전체 화면 + 끌어 옮기는 내 화면(누르면 서로 바뀜), 그 이상은 격자. 타일을 누르면 그 사람을 크게 보고, 상단 버튼으로 갤러리 ↔ 발표자 보기를 바꿉니다. 9명이 넘으면 쪽을 넘깁니다.
- 영상 통화에서 4초 동안 조작이 없으면 조작 막대를 숨깁니다(`autoHideControls = false`로 끔). TalkBack이 켜져 있으면 숨기지 않습니다.
- 반응(👍 ❤️ 😂 👏 😮 🎉)을 보내고 받습니다(`reactionsEnabled = false`로 끔).
- 품질 시트(왕복 시간·지터·손실·비트레이트·프레임과 추이), 오디오 출력 선택, 참가자 목록, 카메라 효과.

테마는 `theme = VoxeraThemeConfig(mode, glassQuality, palette, fontFamily, highContrast)`로 바꿉니다. 호스트 `MaterialTheme`는 건드리지 않으며, 대비가 부족한 색 덮어쓰기는 받아들이지 않습니다.

### 입장 전 화면

```kotlin
VoxeraPrejoinScreen(
    roomName = roomId,
    displayName = myName,
    initialMode = CallMode.VIDEO,
    onJoin = { choice -> scope.launch { session.join(roomId, choice.mode, MediaPreferences(choice.microphone, choice.camera)) } },
    onBack = { navController.popBackStack() },
    preview = { modifier -> LiveKitCameraPreviewContent(modifier) },
)
```

마이크·카메라 권한은 이 화면에서 켜거나 입장할 때만 묻고, 거부하면 그 미디어 없이 입장합니다. "다시 묻지 않음"이면 설정 화면으로 안내합니다. 마이크 레벨 막대가 있으며, 소리는 레벨 계산에만 쓰고 화면이 앞에 있을 때만 마이크를 엽니다.

## 백그라운드·PiP·근접 화면 끄기 (`voxera-android`)

```kotlin
// 입장 직후(앱이 앞에 있을 때)
val keepAlive = VoxeraForegroundCall.keepWhileConnected(context, session, title = "Support call")
val proximity = VoxeraProximityScreenOff.whileOnEarpiece(context, session)

// Activity.onCreate
val pip = VoxeraPictureInPicture.attach(this, session, onHangUp = { lifecycleScope.launch { session.leave() } })
val inPip by pip.isInPictureInPicture.collectAsStateWithLifecycle()
```

- **foreground service**: 통화가 연결된 동안 마이크·카메라를 유지합니다. 알림은 통화 형식이라 경과 시간과 종료 버튼이 있고 Android 12+에서는 상태 표시줄 통화 칩이 생깁니다. Android 13+는 `POST_NOTIFICATIONS`가 있어야 알림이 보입니다.
- **PiP**: 통화 중 홈으로 나가면 자동으로 작은 창이 되고(Android 12+는 시스템 제스처, 11 이하는 `onUserLeaveHint`), 창에 마이크·종료 버튼이 있습니다. Activity에 다음이 필요합니다.

  ```xml
  <activity
      android:supportsPictureInPicture="true"
      android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation" ... />
  ```

  Android 11 이하에서 화면 공유 승인처럼 앱이 다른 화면을 띄우기 직전에는 `pip.ignoreNextUserLeave()`를 부르세요.
- **근접 화면 끄기**: 음성 통화를 휴대전화(수화부)로 들을 때 귀에 대면 화면을 끕니다. 음성 통화는 수화부, 영상 통화는 스피커로 시작하며 이어폰이 있으면 이어폰이 먼저입니다.

세 가지 모두 반환한 `AutoCloseable`을 닫거나 Activity가 끝나면 정리됩니다.

## Call Mode (`voxera-invitation`)

초대·수신·수락·거절은 호스트 서버와 푸시가 맡고, SDK는 상태 정리와 화면을 맡습니다.

```kotlin
val coordinator = CallCoordinator(gateway = myInvitationGateway, session = session).also { it.start() }
VoxeraCallScreen(coordinator = coordinator, session = session, onExit = { finish() })
```

- `InvitationGateway`를 호스트가 구현합니다(`initiate`, `accept`, `decline`, `cancel`, `hangup`, `observeInvitations`).
- 푸시 data는 `InvitationPayloads.parse(data)`로 읽습니다. **payload에 토큰을 넣지 마세요.** 수락한 뒤 토큰은 `RtcCredentialsProvider`가 따로 받습니다.
- 같은 `callId`의 중복·역순 이벤트는 revision으로 정리하고, 울림 제한 시간이 지나면 `MISSED`로 끝냅니다.

## 진단 리포트

```kotlin
val diagnostics = SessionDiagnostics(session).also { it.start() }
val json = diagnostics.snapshot(DiagnosticContext(appVersion, "Android ${Build.VERSION.RELEASE}", Build.MODEL), sdkVersion).toJson()
```

리포트에는 상태 변화와 품질 표본만 들어가며 토큰·서버 주소·룸 이름·참가자 이름은 들어가지 않습니다. 저장과 공유는 호스트가 정합니다.

## Java

```java
VoxeraClient client = VoxeraJava.create(
    new LiveKitRtcEngineFactory(context),
    (request, callback) -> myBackend.fetch(request.getRoomId(), callback::onCredentials),
    ContextCompat.getMainExecutor(context));
client.joinRoom("support-42", CallMode.VOICE, callback);
client.addStateListener(state -> render(state));
client.sendReaction(Reaction.HEART, null);
```

모든 콜백은 넘긴 executor에서 불립니다. `VoxeraCancellable.cancel()`로 기다리는 작업과 리스너를 멈춥니다.

## 오프라인 데모

```kotlin
val session = VoxeraDemo.createFakeSession(DemoScenario.GROUP_FOUR)
scope.launch { session.join(VoxeraDemo.ROOM_ID, DemoScenario.GROUP_FOUR.mode) }
VoxeraRoomScreen(session = session, onExit = { })
```

네트워크에 연결하지 않으며 화면에 항상 `DEMO`를 표시합니다. 품질 값은 `MetricSource.SIMULATED`입니다.

## 보안

- API Secret·Signing Key를 앱·SDK·저장소에 넣지 마세요. 토큰은 호스트 서버가 발급합니다.
- SDK는 토큰을 메모리에만 두고 저장·기록하지 않습니다. `RtcCredentials.toString()`은 토큰을 가립니다.
- TLS를 끄거나 네트워크 보안 정책을 우회하지 않습니다.
- 반응·초대 같은 데이터 메시지는 정해진 형식만 받아들이고 나머지는 버립니다.

문제를 찾으면 [SECURITY.md](../SECURITY.md)를 참고하세요.
