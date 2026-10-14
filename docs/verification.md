# 수동 검증 기록

형식: Date / Device / API / Revision / Environment / Scenario / Expected / Actual / Result.
실제 2기기 통화가 아니라 **에뮬레이터 1대 + LiveKit CLI 봇**으로 확인한 결과다. 실기기 2대 통화는 아직 하지 않았다.

공통 환경: 2026-10-14, Android Emulator `voxera_phone`(Pixel 8, API 36, en-US), 로컬 `livekit-server 1.13.10 --dev`
(127.0.0.1:7880, 개발 기본 키), 상대 참가자 `lk room join --publish-demo`(LiveKit CLI 2.19.0, 데모 영상만 게시),
연결 방식 "기존 토큰" + 로컬 개발 서버 허용(`ws://10.0.2.2:7880`). Revision은 각 항목의 커밋.

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| 음성 입장 (c7b3ebd) | Connected, 참가자 2명, DEMO 표시 없음 | 연결·상대 표시·통화 시간 정상 | Pass |
| 입장 직후 마이크 상태 (c421351) | 서버 게시 상태와 버튼 일치 | 수정 전: 서버는 게시, 버튼은 꺼짐 → 수정 후 일치 | Pass(수정 후) |
| 음소거 | 서버 트랙 `muted: true` | `muted: true` 확인 | Pass |
| 오디오 출력 시트 | 실제 장치 목록 | 에뮬레이터에서 Speakerphone 1개 표시 | Pass |
| 서버에서 강퇴 | Ended(REMOTE_ENDED), 통화 시간 표시 | "The call was ended by the other side.", 0:41 | Pass |
| 다시 입장 | 같은 세션 새 세대로 Connected | 정상 | Pass |
| 서버 강제 종료(kill -9) | Reconnecting → 제한 시간 뒤 Failed(NETWORK) | 배너 표시 후 VOX-NETWORK, 다시 시도 버튼 | Pass |
| 서버 복구 후 다시 시도 | Connected | 정상 | Pass |
| 종료 버튼 | Ended(USER_LEFT), 서버에서 참가자 제거 | 정상 | Pass |
| 잘못된 서명 토큰 | 인증 오류, 토큰·서버 문구 비노출 | VOX-AUTH-EXPIRED, 서버 응답 문구 미표시 | Pass |
| release 빌드 없이 ws:// (debug 정책 없음) | 평문 차단을 설정 오류로 안내 | 수정 전: VOX-INTERNAL → 수정 후 SERVER_URL_INSECURE로 분류(단위 테스트) | Pass(단위 테스트) |
| 영상 입장 (0fbdf5d) | 원격 영상 전체 화면, 내 영상 모서리 | 봇 데모 영상·에뮬레이터 가상 카메라 표시 | Pass |
| 카메라 끄기 | 내 타일이 아바타로, 서버 카메라 트랙 muted | 정상 | Pass |
| 통화 품질 (e32415b) | 실제 측정값, 데모 표시 없음 | RTT 1ms, 지터 4ms, 손실 0%, 업 2.1Mbps, 다운 1.3Mbps, 21fps | Pass |
| 입장 전 카메라 미리보기 (2c657a2) | 권한 허용 후 미리보기, 입장 시 통화 카메라와 충돌 없음 | 가상 카메라 미리보기 → 입장 후 영상 게시 | Pass |
| 입장 시 마이크 거부(E09) | 마이크 없이 입장, 서버에 카메라 트랙만 | 정상 | Pass |
| 통화 중 권한 없는 마이크 켜기 | 이유 안내 배너 | "Allow microphone access in Settings." | Pass |
| 두 번 거부 후(E11) | 대화상자 반복 없이 설정 안내, 돌아오면 다시 읽음 | "설정 열기" → 앱 설정 → 허용 후 배너 사라짐 | Pass |
| 통화 중 홈으로 나가기 (f54949b) | 마이크가 무음 처리되지 않음 | FGS types=0x80(microphone), `silenced:false`. 서비스 없던 이전 시험에선 백그라운드 전환 시 `silenced` | Pass |
| 통화 종료 후 서비스 | 서비스·알림 제거 | 둘 다 0건 | Pass |
| release(R8) 빌드 데모 | 크래시 없음 | 4인 데모 정상 | Pass |
| release에서 ws:// | 평문 차단을 설정 오류로 | VOX-CONFIG, 크래시 없음 | Pass |
| 토큰 endpoint 방식 | 로컬 endpoint가 발급한 토큰으로 입장 | `ep-Endpoint` ACTIVE | Pass |
| 토큰 endpoint 중단 | 재시도 후 도달 불가 안내 | VOX-SERVER-UNREACHABLE, 다시 시도 | Pass |
| 데모 Call Mode (8042994) | 수신→수락→통화→종료, 발신→상대 수락 | 정상 | Pass |
| 진단 리포트 저장 (82016bc) | JSON에 상태·품질만, 토큰·서버·룸 이름·참가자 없음 | 상태 6건·품질 표본 저장, 민감 문자열 0건 | Pass |
| 화면 공유 승인 취소(E21) (3f8c8c7) | 기존 카메라·마이크 유지 | 서버에 MICROPHONE·CAMERA 그대로 | Pass |
| 화면 공유 시작 | 서버 SCREEN_SHARE 트랙, 공유 경고 배너 | 트랙 생성, LiveKit 공유 서비스(0x20)·통화 서비스(0xC0) 동시 실행 | Pass |
| 시스템에서 공유 중지(E22) | 트랙·서비스·배너 정리 | 셋 다 정리됨 | Pass |
| 6개 조작 버튼(360dp 폭 상당) | 종료 버튼이 잘리지 않음 | 48dp 원으로 모두 표시 | Pass |
| 320dp·글꼴 200% (ace4948) | 조작이 화면 안·48dp 이상, 아래 버튼까지 스크롤 | Robolectric 테스트 + 에뮬레이터(density 540, font 2.0)에서 홈·통화·더보기·종료·수신 화면 확인 | Pass |
| 로컬 Maven 게시본 설치 (e7f72c5) | 빈 소비자 앱이 Kotlin 2.2.21로 R8 release 빌드 | `samples/consumer` assembleRelease 통과 | Pass |
| 카메라 색 효과 (fc6a57a) | 효과 적용·성능 폴백, 카메라 게시 정상 | 조회표·세션 단위 테스트만. **실기기 미확인** — 이 변경으로 실통화 카메라 게시 경로가 바뀌었다 | NOT RUN |

## 아직 확인하지 않은 것

실기기 2대 음성 송수신(소리가 실제로 들리는지), LiveKit Cloud(wss)와 개발 토큰 서버(Sandbox) 방식, release 빌드의 실통화(wss 필요),
블루투스·유선 이어폰 전환, 와이파이↔LTE 전환, API 26/30 기기, TalkBack, 장시간 통화, 실제 호스트 서버·푸시로 하는 Call Mode.
