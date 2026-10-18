# Release Notes

## 0.1.0-alpha01 (개발 중)

### 추가

- `voxera-core`: `Voxera.create`, `VoxeraSession`(세대 기반 상태 머신, 재연결 제한 시간, 만료 토큰 1회 재요청), Java facade(`VoxeraJava`), 진단 리포트(`SessionDiagnostics`)
- `voxera-livekit`: LiveKit Android 2.29.0 엔진, 토큰 공급자(`LiveKitTokenSources`), 영상 트랙, 화면 공유, 카메라 색 효과(처리가 느리거나 기기가 뜨거워지면 자동으로 꺼짐), 품질 통계, 반응 데이터 메시지
- `voxera-ui-compose`: Glass 테마(Light/Dark, 고대비, 색 덮어쓰기 대비 검사), `VoxeraRoomScreen`(보기 방식이 바뀔 때 타일이 움직이는 영상 무대, 크게 보기와 발표자 보기, 끌어 옮기는 내 화면, 조작 막대 자동 숨김, 품질 시트와 추이, 반응), `VoxeraPrejoinScreen`(권한, 카메라 미리보기, 마이크 레벨), `VoxeraCallScreen`
- `voxera-android`: foreground service와 통화 알림(경과 시간·종료 버튼), `VoxeraPictureInPicture`, `VoxeraProximityScreenOff`
- `voxera-invitation`: Call Mode(`InvitationGateway`, `CallCoordinator`, 푸시 payload 파서)
- `voxera-testkit`: 가짜 엔진과 오프라인 데모 시나리오

### 바뀐 점

- `VoxeraVideoRenderer`에 프레임 크기를 알리는 `Render(track, mirror, modifier, onFrameSize)`가 생겼습니다. 기본 구현이 있어 기존 렌더러는 그대로 동작합니다(늘 채움). `LiveKitVideoTracks.createView`에는 `onFrameSize`가 붙었습니다.
- 영상 타일이 영상 비율을 보고 채울지 전체를 보여 줄지 정합니다(통화 화면 설명 참고).

### 검증

에뮬레이터(API 26, 36)와 Galaxy Z Fold7에서 확인했습니다. 무엇을 확인했고 무엇을 못 했는지는 [verification.md](verification.md)에 있습니다.
