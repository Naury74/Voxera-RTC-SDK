# Architecture

## 모듈

아래 표의 "가져오는 모듈"은 그 모듈이 API로 노출하는 Voxera 모듈입니다. 쇼케이스 앱(`app`)은 배포하지 않습니다.

| 모듈 | 하는 일 | 가져오는 모듈 |
| --- | --- | --- |
| `voxera-rtc-api` | 엔진에 매이지 않는 모델(`SessionState`, `Participant`, `VideoTrackHandle`, `QualitySnapshot`), `VoxeraError`, 엔진 포트 `RtcEngine`, 토큰 공급자 계약 | 없음 |
| `voxera-core` | 진입점 `Voxera`, `VoxeraSession`, 상태 reducer, Java 진입점 `VoxeraJava`, 진단 리포트 | rtc-api |
| `voxera-invitation` | 걸고 받기(Call Mode) 계약 `InvitationGateway`, `CallCoordinator`, 푸시 payload 파서 | core |
| `voxera-livekit` | LiveKit Android로 만든 `RtcEngine`, 토큰 공급자, 영상 트랙, 화면 공유, 카메라 효과 | core |
| `voxera-ui-compose` | 통화 화면, 입장 전 화면, 걸고 받기 화면, 테마 | core, invitation |
| `voxera-livekit-compose` | LiveKit 영상을 통화 화면에 그리는 렌더러 | livekit, ui-compose |
| `voxera-android` | foreground service와 통화 알림, PiP, 근접 화면 끄기, 시스템 통화 등록 | core |
| `voxera-testkit` | `FakeRtcEngine`(가상 시간), 오프라인 데모 시나리오 | core, invitation |

모듈 사이에는 다음 규칙을 지킵니다.

- `voxera-rtc-api`와 `voxera-core`는 Android, Compose, LiveKit 타입을 쓰지 않습니다. 그래서 상태 전이를 순수 JVM 테스트로 검증할 수 있습니다.
- 공개 API에는 LiveKit `Room`, `Track` 같은 RTC provider 객체가 나오지 않습니다. 영상은 속을 알 수 없는 `VideoTrackHandle`로만 넘기고,
  UI는 provider 모듈이 `LocalVoxeraVideoRenderer`로 넘겨준 렌더러로 그립니다.
- 데모 엔진과 실제 엔진은 `RtcEngineKind`(`SIMULATED`/`LIVE`)로 구분합니다. `SIMULATED`이면 UI에 항상 `DEMO`가 붙습니다.
- SDK 모듈은 explicit API mode로 빌드하고, 리소스 이름은 모두 `voxera_`로 시작합니다. DI 프레임워크 없이 생성자로 주입합니다.

## 세션 상태 머신

```
Idle ─join─► Preparing ─토큰 준비─► Connecting ─연결─► Connected ◄──► Reconnecting
                │                       │                  │               │ 제한 시간 초과
                └──────── 실패 ─────────┴──────────────────┴─► Failed ◄────┘ (Ending 거쳐)
 leave ─► Ending ─자원 해제─► Ended            Failed ─leave─► Ended,  Ended/Failed ─join─► Preparing(새 세대)
```

- 사용자 입력, 엔진 이벤트, 타이머는 모두 `SessionAction`이 되어 순수 함수 `SessionReducer.reduce`를 거칩니다.
  전이가 받아들여진 경우에만 `DefaultVoxeraSession`이 엔진 생성·해제나 타이머 같은 부수 효과를 실행합니다.
- 입장할 때마다 세대(generation)가 1씩 올라갑니다. 연결 성공, 엔진 이벤트, 타이머가 다른 세대의 것이거나 종료 중에 도착하면 버립니다.
- `leave()`는 진행 중인 입장 작업을 취소하고 엔진을 `NonCancellable`로 해제한 다음 `Ended`로 넘어갑니다. 여러 번 불러도 괜찮습니다.
- 같은 `VoxeraSdk`에서 만든 세션들은 `ActiveCallLock` 때문에 한 번에 하나만 미디어를 씁니다. 잠금은 미디어를 다 해제한 뒤에 풉니다.
- 재연결은 엔진이 알아서 합니다. SDK는 `reconnectDeadlineMs`(기본 30초) 타이머만 걸고, 자체 재연결 루프는 없습니다.
- 토큰이 만료되면 `REAUTHENTICATE`로 한 번만 다시 요청하고, 호스트 인증 실패는 재시도하지 않습니다. 일시적인 오류는
  간격을 두 배씩 늘리며 `credentialAttempts`번까지 다시 시도합니다.

## 미디어 조작

`LocalMediaState`의 `MediaToggle`에는 사용자가 원하는 상태(`desired`)와 엔진에 실제로 반영된 상태(`actual`)가 같이 들어 있습니다.
조작은 mutex로 하나씩 처리하고 그때마다 가장 최근 의도만 엔진에 보내기 때문에, 버튼을 빠르게 연타해도 마지막 의도와 엔진 ack가
어긋나지 않습니다. 실패하면 `actual`은 그대로 두고 `lastError`에 남깁니다. UI는 `actual`을 확정된 상태로 그리고, `isPending`이면 진행 중 표시를 합니다.

## 통화 화면

- `VoxeraTheme`은 앱의 `MaterialTheme`를 건드리지 않고 `VoxeraTokens`를 CompositionLocal로 내려 줍니다. 테마를 바꾸면 화면만 다시
  그리고 세션과 트랙은 그대로 둡니다(Robolectric 테스트로 확인).
- 통화 화면은 Kinetica UI 키트의 `KineticaScaffold` 위에 그립니다. 영상 무대를 배경으로 기록해 두고, 그 위의 조작 막대, 상단 바,
  시트가 뒤의 영상을 실제로 흐리게 비춥니다(Android 12 이상). 블러를 못 쓰는 기기에서는 반투명 단색에 외곽선을 두릅니다.
- 유리 품질 `VoxeraGlassQuality.AUTO`는 배터리 절약 모드에서 `REDUCED`(블러 없이 거의 불투명), 고대비 모드에서 `OFF`(불투명)로 낮아집니다.
- 영상 무대는 `stageModel`이 누구를 크게 볼지 정하고, 자리 계산과 쪽 나누기는 Kinetica 참가자 격자가 합니다. 모든 타일이 한 격자 안에 있어서
  보기 방식을 바꿔도 타일(영상 View)을 새로 만들지 않고 자리만 옮깁니다.
- 타일은 렌더러가 알려 준 프레임 크기로 채울지 전체를 보여 줄지 정합니다. 크게 보는 자리는 넓이 75%, 격자와 아래 줄은 50%보다 적게 보이면
  전체를 보여 주고, 내 작은 창은 늘 채우며, 화면 공유는 늘 전체를 보여 줍니다.
- 시스템 애니메이션 배율이 0이면 말할 때 퍼지는 물결 같은 반복 애니메이션과 튕김 효과를 끕니다.
- 앱이 덮어쓴 색(`VoxeraPaletteOverride`)이 글자 4.5:1, 강조 그래픽 3:1 대비에 못 미치면 버리고 기본값을 씁니다.
- 시트, 알림, 스위치, 선택 묶음, 말풍선, 추이 그래프, 움직임 효과는 Kinetica 부품을 `implementation`으로 씁니다. Voxera 공개 API에는
  Kinetica 타입이 나오지 않아서, 나중에 다른 부품으로 바꾸어도 앱 코드는 그대로입니다.

## 처음 설계와 다르게 정한 것

처음 설계에서 목표로 삼은 계약을 실제로 구현하면서 아래처럼 바꾸거나 확정했습니다.

| 항목 | 처음 설계 | 구현 | 이유 |
| --- | --- | --- | --- |
| 진입점 | `Voxera.create(context, factory)` | `Voxera.create(factory)` | core를 순수 JVM으로 두기 위해. Context가 필요한 LiveKit factory가 Context를 받는다 |
| 모듈 | `voxera-call-state`, `voxera-ui-tokens` 분리 | core, ui-compose에 포함 | 쓰는 곳이 하나뿐이라 모듈을 나누지 않음. View 화면을 만들게 되면 토큰을 나눔 |
| 실패 후 | `Failed → Ending → Ended` | `Failed`에서 이미 자원 해제, `leave` 시 `Ended`, 다시 `join` 가능 | 사용자가 실패 화면을 보고 다시 시도할지 닫을지 고를 수 있게 |
| 세션 API | `state`, `participants`, `diagnostics` | `localMedia`, `engineKind`, `clock`, `join(media)` 추가 | ack 대기 표시, DEMO 표시, 통화 시간 계산에 필요 |
| 오류 | 10종 | `InvalidConfiguration`, `HostUnauthorized`, `NotConnected`, `DuplicateIdentity` 추가 | 입력 오류, 호스트 인증 실패, identity 충돌을 따로 안내하려고 |
| 화면 공유·진단 | `startScreenShare`, `DiagnosticsPolicy` | `startScreenShare(consent)`, `SessionDiagnostics` | 화면 공유는 시스템 승인 결과를 받아 시작하고, 진단 리포트는 정책 객체 없이 민감한 값을 처음부터 넣지 않음 |
| Light primary | `#008E78` | `#007D69` | 흰 글자 대비 4.09:1 → 5.08:1 (WCAG 4.5:1) |
| 토큰 | 13종 | `onPrimary`, `onDanger`, `onScrim`, `warning` 추가 | 버튼 글자, 영상 위 글자, 테스트 전용 경고 문구에 필요 |
| GlassLevel.High | alpha ≥ 0.88 | 0.96 | 바텀 시트 글자 뒤로 조작 막대가 비쳐 보임 |
| PiP | UI 축소만 언급 | `voxera-android`의 `VoxeraPictureInPicture`(자동 진입·마이크/종료 버튼), `VoxeraRoomScreen(pictureInPicture)` | Activity·PendingIntent가 필요한 부분은 android 모듈, 그리는 부분은 ui 모듈에 둠 |
| 다른 통화 끼어듦 | Telecom 연동 | `VoxeraTelecomCall`(androidx core-telecom, 선택 사용) + `LocalMediaState.audioInterrupted` | 시스템이 보류를 맡고, 보류된 VoIP 통화를 시스템이 다시 잇지 않으므로 오디오를 돌려받으면 앱이 이어 간다 |
| 아이콘 | 내부 | `VoxeraIcons` 공개 | 호스트가 Voxera 화면과 같은 모양의 조작을 만들 수 있게 |
| 데모 효과 | 미지원 표시 | 가짜 영상에 효과 적용 | 오프라인 데모에서 효과 화면을 끝까지 써 볼 수 있게 |
| 반응 | 없음 | `Reaction` 6종, `VoxeraSession.reactions/sendReaction`, LiveKit 데이터 메시지(주제 `voxera.reaction`) | 정해진 종류만 주고받아 네트워크에서 온 임의 문자열을 그리지 않음. 1초 5개 제한 |
| 통화 알림 | 진행 알림 | CallStyle(경과 시간·종료 버튼) | 상태 표시줄 통화 칩, 앱을 열지 않고 끊기 |
| 조작 막대 자동 숨김 | 숨김 컨트롤 | 영상 통화 4초, 터치 탐색 중에는 숨기지 않음 | TalkBack 사용자가 종료 버튼을 잃지 않게 |
| 바탕·표면 색 | canvas #081322 / #F5FAFE 등 남색 계열 | 무채색 #0B0E14 / #F2F4F7, 강조색은 그대로 | 남색 바탕과 그라디언트 때문에 화면이 탁해 보여서. 대비 테스트는 계속 통과 |
| 크게 보기 | tap-to-pin | 격자 탭 = 크게 보기, 갤러리 ↔ 발표자 보기 전환, 발표자 전환 1.5초 지연 | 그룹 통화에서 한 사람에게 집중 |
| 크게 보기 작은 줄 | 넘치는 인원은 "+N" | 모든 참가자를 한 줄에 두고 좌우(가로 화면은 위아래)로 넘김, 보이는 칸 근처만 그림 | 숨은 사람을 보려고 화면을 바꾸지 않아도 되게. 영상 렌더는 보이는 만큼만 |
| 오디오 장치 목록 | 고정 목록 | 연결된 장치만 표시, 새 장치 연결·끊김을 화면 위 알림으로 안내(전환 시 되돌리기) | 없는 블루투스를 고르게 하지 않음. 이어폰을 끼면 바로 옮기되 사용자가 되돌릴 수 있게 |
| 채팅 | 없음 | `ChatMessage`, `VoxeraSession.chat/sendChatMessage`, LiveKit 표준 채팅 텍스트 스트림(주제 `lk.chat`, 메시지 ID = 스트림 ID) | LiveKit Meet 같은 다른 클라이언트와도 서로 읽히게. 받는 쪽은 4,000바이트를 넘는 스트림을 끝까지 읽지 않고 버린다 |
| 손들기 | 없음 | `Participant.handRaisedAtMs`, `setHandRaised`, 데이터 메시지(`voxera.hand`) + 새 참가자에게 다시 보냄 | 참가자 속성(attributes)은 토큰에 `canUpdateOwnMetadata` 권한이 있어야 해서, 기본 토큰으로도 되게 함 |
| 그룹 통화 수신 | 기본값 | `adaptiveStream`·`dynacast` 켬 | 작은 타일·PiP·화면 밖 타일은 낮은 화질만 받고, 아무도 안 보는 화질 계층은 보내지 않음 |
| 진행자 기능 | 없음 | 제공하지 않음 | 남의 마이크 끄기·내보내기는 LiveKit 서버 API(관리자 권한)가 필요해 클라이언트 SDK 몫이 아님 |
| 화면 구성 요소 | Voxera 자체 유리 컴포넌트 | 시트·아일랜드 알림·스위치·선택 묶음·채팅 말풍선·추이 그래프·블러·움직임 효과는 Kinetica UI 키트(`implementation`)로 그리고, Voxera 공개 컴포넌트 이름과 매개변수는 그대로 둠 | 영상 위 유리에 실제 블러, 뒤 화면이 물러나는 시트, 차례로 뜨는 알림 같은 완성도를 직접 만들지 않고 가져오기 위해. 공개 API에 Kinetica 타입을 내보내지 않아 나중에 바꾸거나 빼기 쉽다 |
