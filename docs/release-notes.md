# Release Notes

## 0.1.0-alpha01 (개발 중)

### 추가

- `voxera-core`: `Voxera.create`, `VoxeraSession`(세대 기반 상태 머신, 재연결 제한 시간, 만료 토큰 1회 재요청), Java facade(`VoxeraJava`), 진단 리포트(`SessionDiagnostics`)
- `voxera-livekit`: LiveKit Android 2.29.0 엔진, 토큰 공급자(`LiveKitTokenSources`), 영상 트랙, 화면 공유, 카메라 색 효과(성능·발열 시 자동 해제), 품질 통계, 반응 데이터 메시지
- `voxera-ui-compose`: Glass 테마(Light/Dark, 고대비, 색 덮어쓰기 대비 검사), `VoxeraRoomScreen`(움직이며 바뀌는 영상 무대, 크게 보기·발표자 보기, 끌어 옮기는 내 화면, 조작 막대 자동 숨김, 품질 시트·추이, 반응), `VoxeraPrejoinScreen`(권한·미리보기·마이크 레벨), `VoxeraCallScreen`
- `voxera-android`: foreground service와 통화 알림(경과 시간·종료 버튼), `VoxeraPictureInPicture`, `VoxeraProximityScreenOff`
- `voxera-invitation`: Call Mode(`InvitationGateway`, `CallCoordinator`, 푸시 payload 파서)
- `voxera-testkit`: 가짜 엔진과 오프라인 데모 시나리오

### 검증

실제로 확인한 범위는 [verification.md](verification.md)에 있습니다(에뮬레이터 API 26·36, Galaxy Z Fold7).
