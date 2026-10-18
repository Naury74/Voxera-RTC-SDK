# Integration

호스트 앱에 Voxera 통화 화면을 붙이는 방법을 정리했습니다. LiveKit 호환 통화 서버와 토큰 발급은 호스트가, 연결과 상태 관리와 화면은 SDK가 맡습니다.

## 의존성

```kotlin
dependencies {
    implementation("com.naurylab:voxera-livekit-compose:0.1.0-alpha01") // LiveKit 엔진 + Compose 화면
    implementation("com.naurylab:voxera-android:0.1.0-alpha01")         // 백그라운드 유지, PiP, 근접 화면 끄기
    // implementation("com.naurylab:voxera-invitation:0.1.0-alpha01")   // Call Mode(초대·수신 화면)
    // implementation("com.naurylab:voxera-testkit:0.1.0-alpha01")      // 오프라인 데모·테스트용 가짜 엔진
}
```

Voxera 모듈은 Maven Central에 있습니다. 다만 LiveKit Android가 쓰는 `audioswitch`는 JitPack에만 있어서 JitPack 저장소도 필요합니다. 이때 그 그룹만 받도록 제한해 두세요.

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

통화 화면(`voxera-ui-compose`)은 같은 저자의 Kinetica UI 키트(`com.naurylab:kinetica-ui`, `kinetica-render-compose`, Apache 2.0)로 시트, 알림, 스위치, 움직임을 그립니다. 의존성으로 함께 들어오므로 따로 넣지 않아도 되고, Voxera 공개 API에는 Kinetica 타입이 나오지 않습니다. Kinetica(지금 쓰는 판은 `1.0.0-alpha03`)는 Maven Central에 있어서 `mavenCentral()`만 있으면 받아집니다.

Voxera 화면 문구는 영어와 한국어로 들어 있습니다. Kinetica는 그보다 많은 언어(일본어, 중국어, 스페인어 등)를 담고 있어서, 앱이 다른 언어를 지원하지 않는데 기기가 그 언어라면 Voxera 문구는 영어로, Kinetica가 그리는 일부 버튼 이름은 그 언어로 나올 수 있습니다. 앱이 지원하는 언어만 남기려면 `androidResources { localeFilters += listOf("en", "ko") }`처럼 앱에서 언어를 정해 두세요.

최소 API는 26이고, 호스트는 Kotlin 2.2 이상이면 됩니다(SDK를 언어·API 버전 2.2로 맞춰 빌드합니다). 매니페스트에 합쳐지는 권한과 서비스는 [permissions.md](permissions.md)에 있습니다.

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

- 한 SDK 인스턴스에서는 한 번에 한 통화만 연결됩니다. 통화 중에 다시 `join`하면 `VoxeraError.ConcurrentSession`이 돌아옵니다.
- 상태는 `session.state`(`Idle → Preparing → Connecting → Connected ⇄ Reconnecting → Ending → Ended/Failed`), 참가자는 `session.participants`, 내 마이크·카메라는 `session.localMedia`로 구독합니다.
- 세션을 다 쓰면 `session.close()`를 부릅니다.

### 토큰 공급자

| 공급자 | 용도 |
| --- | --- |
| `RtcCredentialsProvider { ... }` | 운영용. 호스트 서버가 토큰을 발급 |
| `LiveKitTokenSources.endpoint(url, name, headers)` | 호스트의 토큰 엔드포인트에 POST(`room_name`, `participant_name`) → `server_url`, `participant_token` |
| `LiveKitTokenSources.literal(serverUrl, token)` | 이미 받아 둔 토큰 하나로 입장 |
| `LiveKitTokenSources.developmentTokenServer(id, name)` | LiveKit Cloud 개발 토큰 서버. **테스트 전용**이다. ID만 알면 누구나 토큰을 받을 수 있다 |

- 토큰이 만료되면 `CredentialReason.REAUTHENTICATE`로 한 번만 다시 요청합니다. 호스트 인증 실패(401/403)는 재시도하지 않고 바로 `HostUnauthorized`로 끝납니다.
- 기본은 `wss://`만 받습니다. 로컬 개발 서버용 `ws://10.0.2.2`, `ws://localhost`는 `ServerUrlPolicy(allowInsecureLocalhost = true)`일 때만 쓰며, 이 옵션은 debug 빌드에서만 켜세요.

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

`VoxeraRoomScreen`이 알아서 하는 일은 다음과 같습니다.

- 상태에 따라 연결 중, 통화, 종료, 실패 화면으로 바뀝니다. 다시 해도 결과가 같은 오류라면 실패 화면에 재시도 버튼을 띄우지 않습니다.
- 영상 통화가 2명이면 상대가 전체 화면에, 내 화면은 끌어서 옮길 수 있는 작은 창에 나옵니다(누르면 서로 바뀜). 3명부터는 격자입니다. 타일을 누르면 그 사람을 크게 보고, 나머지는 아래 줄에 모두 놓여 옆으로 넘겨 봅니다. 위쪽 버튼으로 갤러리 보기와 발표자 보기를 오갑니다. 9명이 넘으면 쪽을 넘겨 봅니다.
- 참가자마다 보내는 영상 비율이 달라도(세로로 든 휴대전화, 가로 노트북 카메라) 조금만 잘리면 타일을 채우고, 많이 잘리면 영상 전체를 보여 주며 남는 곳은 어둡게 둡니다. 크게 보는 자리는 넓이 75%, 격자와 아래 줄은 50%보다 적게 보일 때 전체를 보여 주고, 내 작은 창은 늘 채우며, 화면 공유는 늘 전체를 보여 줍니다. 상대가 휴대전화를 돌리면 부드럽게 다시 맞춥니다. 가로로 눕힌 휴대전화에서는 칸이 납작해지지 않게 4명까지 한 줄, 그보다 많으면 두 줄로 놓습니다.
- 직접 만든 렌더러를 쓴다면 `VoxeraVideoRenderer`의 `Render(track, mirror, modifier, onFrameSize)`를 구현해 프레임 크기를 알려 주세요. 구현하지 않으면 늘 타일을 채웁니다. `asComposeRenderer()`는 이미 알려 줍니다.
- 영상 통화에서 4초 동안 아무 조작이 없으면 조작 막대를 숨깁니다(`autoHideControls = false`로 끌 수 있음). TalkBack이 켜져 있으면 숨기지 않습니다. 숨어 있을 때 화면을 누르면 먼저 막대가 나옵니다.
- 반응(👍 ❤️ 😂 👏 😮 🎉)을 주고받습니다(`reactionsEnabled = false`로 끌 수 있음).
- 채팅을 주고받습니다. 시트를 닫아 둔 사이 온 메시지는 위에 잠깐 미리 보이고, 채팅 버튼에 읽지 않은 수가 붙습니다(`chatEnabled = false`로 끌 수 있음).
- 반응 시트에서 손을 들고 내립니다. 손을 든 사람은 타일과 아바타에 손 배지가 붙고 참가자 목록 맨 위로 갑니다(`handRaiseEnabled = false`로 끌 수 있음).
- 오디오 출력 목록에는 연결된 장치만 나옵니다. 통화 중 이어폰이 연결되면 그쪽으로 옮기고, 오디오 버튼(막대가 좁으면 더보기 버튼) 위에 되돌리기 버튼이 있는 말풍선을 8초 띄웁니다. 끊기면 남은 장치를 6초 알려 줍니다. 조작 막대가 숨어 있으면 말풍선과 함께 꺼냅니다.
- 품질 시트(왕복 시간, 지터, 손실, 비트레이트, 프레임과 그 추이), 오디오 출력 선택, 참가자 목록, 카메라 효과가 들어 있습니다.

테마는 `theme = VoxeraThemeConfig(mode, glassQuality, palette, fontFamily, highContrast)`로 바꿉니다. 호스트의 `MaterialTheme`는 건드리지 않습니다. 색을 덮어쓸 때 대비가 모자라면 그 값은 버리고 기본색을 씁니다.

### 채팅과 손들기를 직접 쓰기

화면을 직접 만들 때는 세션 API를 씁니다. 받은 글은 사용자 입력 그대로이므로 일반 글자로만 그리고 로그에 남기지 마세요.

```kotlin
session.chat.collect { messages -> render(messages) }   // 이번 통화의 메시지(최대 200개), 통화가 끝나면 비워짐
session.sendChatMessage("안녕하세요")                     // 비었거나 1,000자를 넘거나 너무 빠르면 Unsupported
session.setHandRaised(true)                              // 내 상태는 participants의 handRaisedAtMs로 보임
```

한 사람이 5초에 5개를 넘게 보낸 채팅은 버립니다. 손들기는 나중에 들어온 사람에게도 다시 알립니다. 채팅은 LiveKit 표준 채팅과 같은 텍스트 스트림(주제 `lk.chat`)이라 LiveKit Meet 같은 다른 클라이언트와도 주고받을 수 있습니다. 손들기는 데이터 메시지(주제 `voxera.hand`)입니다. 둘 다 토큰에 따로 권한을 줄 필요가 없습니다.

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

마이크와 카메라 권한은 이 화면에서 켜거나 입장할 때만 묻습니다. 거부하면 그 장치 없이 입장하고, "다시 묻지 않음" 상태라면 설정 화면으로 안내합니다. 마이크 레벨 막대도 있는데, 들어온 소리는 레벨 계산에만 쓰고 마이크는 화면이 앞에 있을 때만 엽니다.

## 백그라운드·PiP·근접 화면 끄기 (`voxera-android`)

```kotlin
// 입장 직후(앱이 앞에 있을 때)
val keepAlive = VoxeraForegroundCall.keepWhileConnected(context, session, title = "Support call")
val proximity = VoxeraProximityScreenOff.whileOnEarpiece(context, session)

// Activity.onCreate
val pip = VoxeraPictureInPicture.attach(this, session, onHangUp = { lifecycleScope.launch { session.leave() } })
val inPip by pip.isInPictureInPicture.collectAsStateWithLifecycle()
```

- foreground service: 통화가 연결돼 있는 동안 마이크와 카메라가 끊기지 않게 합니다. 알림은 통화 형식이라 경과 시간과 종료 버튼이 있고, Android 12+에서는 상태 표시줄에 통화 칩이 뜹니다. Android 13+에서는 `POST_NOTIFICATIONS` 권한이 있어야 알림이 보입니다. 알림의 작은 아이콘은 Voxera 마크이고, 상황에 따라 오른쪽 아래에 배지가 붙고 본문 문구도 바뀝니다. 상황이 겹치면 먼저 알아야 할 것 하나만 보여 주며 순서는 표와 같습니다.

  | 상황 | 배지 | drawable 이름 |
  | --- | --- | --- |
  | 다시 연결하는 중 | 점 세 개 | `voxera_ic_notification_reconnecting` |
  | 다른 통화 때문에 보류 | 일시정지 | `voxera_ic_notification_on_hold` |
  | 내 화면 공유 중 | 화면과 위쪽 화살표 | `voxera_ic_notification_sharing` |
  | 마이크 꺼짐 | 사선 | `voxera_ic_notification_muted` |
  | 영상 통화 | 카메라 | `voxera_ic_notification_video` |
  | 그 밖의 통화 | 없음 | `voxera_ic_notification` |

  앱 아이콘으로 바꾸려면 앱의 `res/drawable`에 같은 이름의 흰색 단색 아이콘(24dp)을 두면 됩니다. 일부만 두면 나머지는 Voxera 아이콘이 쓰입니다.
- PiP: 통화 중에 홈으로 나가면 저절로 작은 창으로 바뀝니다(Android 12+는 시스템 제스처, 11 이하는 `onUserLeaveHint`). 창에는 마이크 버튼과 종료 버튼이 있습니다. Activity에는 다음 설정이 필요합니다.

  ```xml
  <activity
      android:supportsPictureInPicture="true"
      android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation" ... />
  ```

  Android 11 이하에서는 화면 공유 승인 창처럼 앱이 스스로 다른 화면을 띄우기 직전에 `pip.ignoreNextUserLeave()`를 불러 주세요. 그러지 않으면 PiP로 들어가 버립니다.
- 근접 화면 끄기: 음성 통화를 수화부(휴대전화)로 들을 때 귀에 대면 화면이 꺼집니다. 음성 통화는 수화부로, 영상 통화는 스피커로 시작하고, 이어폰이 연결돼 있으면 이어폰이 우선입니다.

- **시스템 통화 등록(Telecom)**: `VoxeraTelecomCall.registerWhileConnected(context, session, title)`을 부르면 통화가 Android 통화 목록에 올라갑니다. 휴대전화가 걸려 와서 받으면 시스템이 Voxera 통화를 보류하고(이때 마이크를 끕니다), 그 전화가 끝나면 통화를 이어 가며 마이크를 원래대로 켭니다. 차량이나 시계에서 끊으면 `onHangUp`(기본은 `session.leave()`)이 불립니다. 시스템에는 `title`만 넘기고 전화번호나 토큰은 넘기지 않습니다.

넷 다 돌려받은 `AutoCloseable`을 닫거나 Activity가 끝나면 정리됩니다.

## Call Mode (`voxera-invitation`)

초대를 보내고 받고 수락하거나 거절하는 일은 호스트 서버와 푸시가 맡습니다. SDK는 그 상태를 정리하고 화면을 그립니다.

```kotlin
val coordinator = CallCoordinator(gateway = myInvitationGateway, session = session).also { it.start() }
VoxeraCallScreen(coordinator = coordinator, session = session, onExit = { finish() })
```

- 호스트는 `InvitationGateway`(`initiate`, `accept`, `decline`, `cancel`, `hangup`, `observeInvitations`)를 구현합니다.
- 푸시 data는 `InvitationPayloads.parse(data)`로 읽습니다. **payload에 토큰을 넣지 마세요.** 토큰은 수락한 뒤에 `RtcCredentialsProvider`가 따로 받아 옵니다.
- 같은 `callId`로 중복되거나 순서가 뒤바뀐 이벤트는 revision으로 걸러 냅니다. 벨 제한 시간이 지나면 `MISSED`로 끝납니다.

## 진단 리포트

```kotlin
val diagnostics = SessionDiagnostics(session).also { it.start() }
val json = diagnostics.snapshot(DiagnosticContext(appVersion, "Android ${Build.VERSION.RELEASE}", Build.MODEL), sdkVersion).toJson()
```

리포트에는 상태 변화와 품질 표본만 들어갑니다. 토큰, 서버 주소, 룸 이름, 참가자 이름은 넣지 않습니다. 어디에 저장하고 누구와 공유할지는 호스트가 정합니다.

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

콜백은 모두 넘겨준 executor에서 불립니다. 기다리는 작업이나 리스너는 `VoxeraCancellable.cancel()`로 멈춥니다.

## 오프라인 데모

```kotlin
val session = VoxeraDemo.createFakeSession(DemoScenario.GROUP_FOUR)
scope.launch { session.join(VoxeraDemo.ROOM_ID, DemoScenario.GROUP_FOUR.mode) }
VoxeraRoomScreen(session = session, onExit = { })
```

네트워크를 전혀 쓰지 않고, 화면에는 늘 `DEMO` 표시가 붙습니다. 품질 값도 `MetricSource.SIMULATED`로 나옵니다.

## 보안

- API Secret과 Signing Key는 앱, SDK, 저장소 어디에도 넣지 마세요. 토큰은 호스트 서버가 발급합니다.
- SDK는 토큰을 메모리에만 두고 디스크나 로그에 남기지 않습니다. `RtcCredentials.toString()`에서도 토큰을 가립니다.
- SDK가 TLS를 끄거나 네트워크 보안 정책을 우회하는 일은 없습니다.
- 반응이나 초대 같은 데이터 메시지는 정해진 형식만 받고 나머지는 버립니다.

보안 문제를 발견했다면 [SECURITY.md](../SECURITY.md)의 방법으로 알려 주세요.
