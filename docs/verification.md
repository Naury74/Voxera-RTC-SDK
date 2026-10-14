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

## 아직 확인하지 않은 것

실기기 2대 음성 송수신(소리가 실제로 들리는지), LiveKit Cloud(wss)·개발 토큰 서버·토큰 endpoint 방식, 블루투스·유선
이어폰 전환, 와이파이↔LTE 전환, API 26/30 기기, TalkBack, 장시간 통화.
