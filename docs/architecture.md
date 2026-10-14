# Architecture

## 모듈

```
                 app (Showcase)
                   │        │
        voxera-ui-compose   voxera-testkit (FakeRtcEngine, VoxeraDemo)
                   │        │
                   └── voxera-core ──► voxera-rtc-api
                       (Kotlin JVM)    (Kotlin JVM)
```

의존 규칙:

- `voxera-rtc-api`와 `voxera-core`는 Android·Compose·LiveKit 타입을 참조하지 않습니다. 순수 JVM 테스트로 상태 전이를 검증합니다.
- 공개 API에 RTC provider 객체(LiveKit `Room`, `Track`)를 드러내지 않습니다. 영상은 불투명한 `VideoTrackHandle`로만 전달하고,
  UI는 `LocalVoxeraVideoRenderer`로 provider 모듈이 넘긴 렌더러를 씁니다.
- 데모와 실제 엔진은 `RtcEngineKind`(`SIMULATED`/`LIVE`)로 타입이 구분되며, UI는 `SIMULATED`일 때 항상 `DEMO`를 표시합니다.
- SDK 모듈은 explicit API mode, 리소스 이름은 `voxera_` prefix입니다. DI 프레임워크 없이 생성자 주입을 씁니다.

## 세션 상태 머신

```
Idle ─join─► Preparing ─토큰 준비─► Connecting ─연결─► Connected ◄──► Reconnecting
                │                       │                  │               │ 제한 시간 초과
                └──────── 실패 ─────────┴──────────────────┴─► Failed ◄────┘ (Ending 거쳐)
 leave ─► Ending ─자원 해제─► Ended            Failed ─leave─► Ended,  Ended/Failed ─join─► Preparing(새 세대)
```

- 사용자 입력, 엔진 이벤트, 타이머는 모두 `SessionAction`으로 바뀌어 순수 함수 `SessionReducer.reduce`를 거칩니다.
  전이가 받아들여졌을 때만 `DefaultVoxeraSession`이 부수 효과(엔진 생성·해제, 타이머)를 실행합니다.
- 입장할 때마다 세대(generation)가 1씩 늘어납니다. 세대가 다르거나 종료 중에 도착한 연결 성공·엔진 이벤트·타이머는 버립니다.
- `leave()`는 진행 중인 입장 작업을 취소하고, 엔진을 `NonCancellable`로 해제한 뒤 `Ended`로 옮깁니다. 여러 번 불러도 안전합니다.
- 같은 `VoxeraSdk`에서 만든 세션은 `ActiveCallLock`으로 동시에 하나만 미디어를 씁니다. 잠금은 미디어를 해제한 뒤에 풉니다.
- 재연결은 엔진이 스스로 합니다. SDK는 `reconnectDeadlineMs`(기본 30초) 타이머만 두고 따로 재연결 루프를 돌리지 않습니다.
- 자격 증명: 만료 토큰은 `REAUTHENTICATE`로 한 번만 다시 요청, 호스트 인증 실패는 재시도하지 않음, 일시적 오류는
  `credentialAttempts`번까지 두 배씩 늘어나는 간격으로 다시 시도합니다.

## 미디어 조작

`LocalMediaState`의 `MediaToggle`은 사용자의 의도(`desired`)와 엔진이 반영한 상태(`actual`)를 함께 가집니다. 조작은 mutex로
하나씩 처리하고 매번 가장 최근 의도만 엔진에 보내므로, 빠르게 여러 번 눌러도 마지막 의도와 엔진 ack가 일치합니다. 실패하면
`actual`을 유지하고 `lastError`를 남깁니다. UI는 `actual`을 확정 상태로, `isPending`이면 진행 표시로 그립니다.

## UI (Glass)

- `VoxeraTheme`은 호스트 `MaterialTheme`를 바꾸지 않고 `VoxeraTokens`를 CompositionLocal로 전달합니다. 테마를 바꿔도 UI만 다시
  그리며 세션·트랙은 그대로입니다(Robolectric 테스트로 확인).
- 유리 표면은 반투명 tint + 위쪽 반사광 + 1dp 테두리 + 가벼운 그림자입니다. 배경 흐림은 하지 않습니다.
  `VoxeraGlassQuality.AUTO`는 배터리 절약이면 `REDUCED`, 고대비면 `OFF`(불투명)로 낮춥니다.
- 호스트 색 덮어쓰기(`VoxeraPaletteOverride`)는 글자 4.5:1, 강조 그래픽 3:1 대비에 못 미치면 버리고 기본값을 씁니다.

## 설계 문서와 다르게 정한 것

설계 문서의 Kotlin 코드는 목표 계약이므로 구현하면서 아래처럼 확정했습니다.

| 항목 | 설계 문서 | 구현 | 이유 |
| --- | --- | --- | --- |
| 진입점 | `Voxera.create(context, factory)` | `Voxera.create(factory)` | core를 순수 JVM으로 두기 위해. Context가 필요한 LiveKit factory가 Context를 받는다 |
| 모듈 | `voxera-call-state`, `voxera-ui-tokens` 분리 | core, ui-compose에 포함 | 아직 소비자가 하나뿐이라 모듈 중복을 피함. View UI를 만들 때 tokens를 분리 |
| 실패 후 | `Failed → Ending → Ended` | `Failed`에서 이미 자원 해제, `leave` 시 `Ended`, 다시 `join` 가능 | 사용자가 실패 화면을 보고 재시도·닫기를 고를 수 있게 |
| 세션 API | `state`, `participants`, `diagnostics` | `localMedia`, `engineKind`, `clock`, `join(media)` 추가 | ack 대기 표시, DEMO 표시, 통화 시간 계산에 필요 |
| 오류 | 10종 | `InvalidConfiguration`, `HostUnauthorized`, `NotConnected`, `DuplicateIdentity` 추가 | 입력 오류·호스트 인증·identity 충돌을 따로 안내 |
| 화면 공유·진단 정책 | `startScreenShare`, `DiagnosticsPolicy` | 아직 없음 | 해당 티켓(VOX-018, VOX-020)에서 동작과 함께 추가 |
| Light primary | `#008E78` | `#007D69` | 흰 글자 대비 4.09:1 → 5.08:1 (WCAG 4.5:1) |
| 토큰 | 13종 | `onPrimary`, `onDanger`, `onScrim`, `warning` 추가 | 버튼·영상 위 글자·테스트 전용 경고 문구 |
| GlassLevel.High | alpha ≥ 0.88 | 0.96 | 바텀 시트 글자 뒤로 조작 막대가 비쳐 보임 |
