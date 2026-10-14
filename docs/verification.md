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
| 카메라 게시 새 경로 (fc6a57a) | 처리기 단 카메라 트랙 게시, 끄기=muted, 켜기=해제 | 별도 AVD `voxera_verify`(API 36)에서 CAMERA 게시·muted true→false 확인 | Pass |
| 흑백 효과 적용 | 내 미리보기·송신 영상이 흑백 | 미리보기가 흑백으로 바뀜 | Pass |
| 효과 성능 폴백 | 예산(60프레임 평균 15ms) 초과 시 원본으로, 통화 유지 | 에뮬레이터(소프트웨어 GL)에서 수 초 안에 폴백·안내 문구, 통화 유지 | Pass |
| 실제 폰의 효과 처리 시간 | 예산 안 | 아래 Fold7 표 참고 | Pass |

## 실기기 Galaxy Z Fold7 (SM-F966N, API 36, 접은 상태) — 2026-10-15

로컬 livekit-server(`--dev`)를 adb reverse로 Fold7에, 에뮬레이터 `voxera_verify`에는 10.0.2.2로 연결했다. 토큰은 로컬 endpoint가 발급했다.

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| 실카메라·마이크 영상 통화 | 두 트랙 게시, 상대 영상 표시 | MICROPHONE·CAMERA 게시, RTT 8ms, 30fps | Pass |
| 흑백 효과 처리 시간 (eb2367f) | 프레임당 15ms 안, 폴백 없음 | 5.0ms | Pass |
| 선명 효과 처리 시간 | 같음 | 7.4~7.8ms | Pass |
| 양방향 음성 | 서로의 발화가 SFU를 거쳐 감지됨 | 서버 speakersChanged에 두 참가자 모두, 에뮬레이터에서 Fold7 말하기 링 표시, VOICE_COMMUNICATION 재생 생성 | Pass |
| 음성 모드 원격 영상 수신 (c480523 이전) | 영상 받지 않음 | 1.8Mbps·25fps로 상대 카메라 수신 | Fail → 수정 |
| 음성 모드 원격 영상 수신 (c480523) | 오디오만 | 수신 97kbps, 수신 프레임 N/A | Pass |
| 오디오 출력 전환 | 스피커↔휴대전화가 실제 경로에 반영 | `dumpsys audio` 통신 장치가 speaker→earpiece→speaker | Pass |
| 백그라운드 마이크 | 홈으로 나가도 녹음 유지 | 20초 뒤 silenced:false, 통화 서비스 0xC0 | Pass |
| 화면 공유(전체 화면) | 승인→공유→상대 수신→중지 | 경고 배너, 공유 서비스 0x20, 에뮬레이터에 "Fold7 · screen" 표시, 중지 후 서비스·타일 정리 | Pass |
| 입장 전 화면 → 입장 | 권한 허용 상태에서 바로 입장 | 정상 | Pass |
| 블루투스 이어폰 자동 선택 (Galaxy Buds2) | 연결돼 있으면 BT로 시작 | 오디오 시트에 블루투스 선택됨, 통신 장치 bt_sco_hs | Pass |
| 블루투스 ↔ 스피커 전환 | 실제 경로 반영 | bt_sco_hs → speaker → bt_sco_hs | Pass |
| 블루투스 끊김(E12) | 스피커로 복귀, 목록 갱신 | 블루투스를 끄자 speaker, 목록에서 블루투스 사라짐 | Pass |
| 블루투스 재연결 | 목록 갱신, BT로 복귀 | 이어폰이 다시 붙자 bt_sco_hs로 돌아오고 선택됨 | Pass |

## 에뮬레이터 voxera_verify (API 36) — 2026-10-15, 새 통화 화면·PiP

데모(가짜 엔진)와 로컬 livekit-server + `lk room join --publish-demo` 봇으로 확인했다.

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| 조작 막대 자동 숨김 | 영상 통화 4초 무조작 시 숨김, 누르면 다시 | 숨김·복귀, Robolectric 테스트도 추가 | Pass |
| 내 영상 끌어 옮기기 | 가까운 모서리로 붙음 | 오른쪽 아래 → 왼쪽 위로 끌자 왼쪽 위에 붙음 | Pass |
| 품질 시트 추이 그래프 | 상태 요약과 항목별 꺾은선, 값 없으면 N/A | 안정·RTT/지터/손실/비트레이트 그래프, 수신 프레임 N/A | Pass |
| 데모 효과 미리보기 | 흑백 선택 시 내 영상이 회색 | 회색으로 바뀜 | Pass |
| PiP 자동 진입(데모·실통화) | 홈으로 나가면 PiP, 상대 한 명만 | 3:4 창에 원격 영상(실통화는 봇 영상) | Pass |
| PiP 마이크 버튼 | 누르면 마이크 꺼짐 표시 | 창에 마이크 꺼짐 표시 | Pass |
| PiP 종료 버튼 | 통화 종료 | "Call ended · 0:59" 한 줄로 바뀜 | Pass |
| PiP에서 복귀(E20) | 영상 다시 붙음, 중복 렌더 없음 | 전체 화면으로 돌아와 원격·내 영상 정상 | Pass |
| PiP 창 닫기 | 통화는 foreground service로 이어짐 | 서비스 유지, 서버에 두 참가자 ACTIVE, 앱을 다시 열면 2:13 진행 중 화면 | Pass |
| 다른 통화 수신(E14, `emu gsm call`) | 안내 표시, 끝나면 사라짐 | 벨이 울리는 동안 "다른 통화가 오디오를 쓰고 있습니다", 취소 후 사라짐 | Pass |
| 연결 설정 입력 (115e651 이전) | 입력한 글자 그대로 | 방 이름이 "tteam-", 주소가 중간에서 끊김 | Fail → 수정 |
| 연결 설정 입력 (115e651) | 같음 | 주소·방 이름·이름이 한 번에 정확히 들어감 | Pass |

PiP는 실기기에서 아직 확인하지 않았다(NOT RUN). 발열 때 효과 끄기(E31)는 열 상태를 바꿀 방법이 없어 단위 테스트만 했다.

## API 26 (minSdk) — 2026-10-14, AVD `voxera_api26`(Pixel, API 26, google_apis arm64)

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| debug 설치·홈 | 정상 표시 | 정상 | Pass |
| 실통화 영상 입장 (5cc358a 이전) | 연결 | **크래시**: 에뮬레이터가 마이크를 열지 못하자 WebRtcAudioRecord가 AssertionError를 던져 프로세스 종료 | Fail → 수정 |
| 실통화 영상 입장 (527331f) | 크래시 없이 마이크 없이 입장, 장치 안내 | 원격 영상·내 카메라 정상, CAMERA 트랙 게시, "마이크나 카메라를 쓸 수 없습니다" 배너 | Pass |
| 통화 유지 서비스 | 타입 없이 foreground 시작 | isForeground=true | Pass |
| release(R8) 데모 그룹·Call Mode 수락 | 크래시 없음 | 정상 | Pass |

API 30·31·33·34·35 기기는 아직 확인하지 않았다. API 26에서 유리 표면 그림자가 안쪽에 한 겹 더 보이는 시각 차이가 있다.

## 아직 확인하지 않은 것

사람 귀로 듣는 음성 품질(실기기 2대), LiveKit Cloud(wss)와 개발 토큰 서버(Sandbox) 방식, release 빌드의 실통화(wss 필요),
유선 이어폰 전환, 펼친 화면(Fold7 안쪽), 실기기 PiP, 실제 발열 상황, 와이파이↔LTE 전환, API 26/30 기기, TalkBack, 장시간 통화, 실제 호스트 서버·푸시로 하는 Call Mode.
