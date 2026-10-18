# 수동 검증 기록

항목: Date / Device / API / Revision / Environment / Scenario / Expected / Actual / Result.
첫 표는 에뮬레이터 한 대와 LiveKit CLI 봇으로 확인한 결과다. 실기기 두 대 사이의 통화는 아직 해 보지 않았다.

공통 환경: 2026-10-14, Android Emulator `voxera_phone`(Pixel 8, API 36, en-US), 로컬 `livekit-server 1.13.10 --dev`
(127.0.0.1:7880, 개발 기본 키), 상대 참가자 `lk room join --publish-demo`(LiveKit CLI 2.19.0, 데모 영상만 게시),
연결 방식은 "기존 토큰"에 로컬 개발 서버 허용(`ws://10.0.2.2:7880`). Revision은 항목마다 괄호 안의 커밋이다.

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

## 실기기 Galaxy Z Fold7 — 2026-10-15 오전, 새 무대·PiP·알림

로컬 livekit-server에 `lk room join --publish-demo` 봇 두 개(bot-demo, bot-two)와 Fold7(접은 상태, 실카메라)을 붙여 3인 통화로 확인했다.

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| 데모 4인 격자 → 크게 보기 | 누른 사람이 커지고 나머지는 작은 줄로 이동 | 타일이 움직이며 바뀜 | Pass |
| 실통화 3인 격자 | 실영상 3개, 마지막 줄 가운데 | 정상 | Pass |
| 실통화 크게 보기·다른 사람으로 바꾸기 | 전환 중에도 영상이 끊기지 않음 | 영상 계속 재생, 오류 로그 없음 | Pass |
| 발표자 보기 | 원격 참가자가 크게 | bot-demo가 크게, 나머지 작은 줄 | Pass |
| PiP 자동 진입 | 홈으로 나가면 PiP | Samsung PiP 창에 원격 영상 | Pass |
| PiP 마이크 버튼 | 서버에서 음소거 | MICROPHONE muted=true, 창에 꺼짐 표시. 다시 누르면 false | Pass |
| PiP 복귀 (6bd1526 이전) | 크게 보던 사람 유지 | 격자로 풀림 | Fail → 수정 |
| PiP 복귀 (6bd1526) | 같음 | bot-two 크게 보기 유지, 영상 다시 붙음 | Pass |
| 입장 전 마이크 레벨 (6bd1526 이전) | 말하면 막대가 참 | 원시 표본이 0(VOICE_COMMUNICATION 입력) | Fail → 수정 |
| 입장 전 마이크 레벨 (6bd1526) | 같음 | 말소리에 막대 9/16칸, 아바타 고리 | Pass |
| 수화부 근접 화면 끄기 | 수화부일 때 근접 잠금, 스피커면 놓음 | PROXIMITY_SCREEN_OFF_WAKE_LOCK 'voxera:proximity' 잡힘 → 스피커로 바꾸자 사라짐 | Pass |
| 실제로 귀에 대면 화면 꺼짐 (4f60e0d 이전) | 화면 꺼짐 | 음성 통화가 스피커로 시작해 근접 잠금이 안 걸림 | Fail → 수정 |
| 실제로 귀에 대면 화면 꺼짐 (4f60e0d) | 음성 통화는 수화부로 시작, 가리면 화면 꺼짐 | 경로 earpiece, 센서를 가리자 mProximityPositive=true·mScreenState=OFF, 떼자 ON | Pass |
| 연결 화면 움직임 (b8ec293) | 아바타 숨쉬기·진행 막대 빛 | 연속 캡처로 확인 | Pass |
| 마이크 버튼 움직임 | 처리 중 테두리 맥박, 아이콘 바뀜 | 처리 중 파란 테두리·흐린 아이콘 → 꺼짐 아이콘 | Pass |
| 종료 화면 움직임 | 통화 시간 0부터 올라감, 버튼 차례로 | 0:40 → 0:50, 닫기 버튼이 늦게 선명해짐 | Pass |
| 바텀 시트 | 아래에서 올라옴 | 중간 프레임에서 올라오는 중 | Pass |
| 수락 버튼 고리 (63f8f4f 이전) | 둥근 고리가 퍼짐 | 위·오른쪽이 평평하게 잘림 | Fail → 수정 |
| 수락 버튼 고리 (63f8f4f) | 같음 | 가장 크게 퍼진 순간까지 둥글게 보임 | Pass |
| 실통화 카메라 끄기·켜기 | 서버 muted 바뀜, 내 화면은 아바타 ↔ 영상 | CAMERA muted true → false, 작은 화면 아바타 → 영상 | Pass |
| 실통화 반응 보내기 | 떠오르는 하트, 상대가 받음 | 하트와 "나" 이름표, 봇이 데이터 받음 | Pass |
| 펼친 안쪽 화면(1968×2184, 약 750dp 폭) 홈 | 가운데 정렬, 너비 제한 | 560dp 폭으로 가운데 | Pass |
| 펼친 화면 4인·9인 격자 | 조작 8개가 막대에 다 들어감, 격자 꽉 참 | 더보기 없이 8개, 3×3 | Pass |
| 펼친 화면 크게 보기 | 큰 타일 + 아래 줄, 넘치면 +N | 작은 줄 5칸 + "+3명 더" (이후 넘기는 줄로 바뀜, 아래 표) | Pass |
| 펼친 화면 1:1·수신 | 내 화면 160dp, 버튼 잘림 없음 | 정상 | Pass |
| 통화 알림 종료 버튼 | 펼친 알림의 "전화 끊기"로 종료 | 경과 시간 표시, 누르자 종료·서비스 정리·서버에서 퇴장 | Pass |
| 음성 그룹 격자 (6bd1526 이전) | 마지막 줄 가운데 | 왼쪽으로 쏠림 | Fail → 수정 |

## 에뮬레이터 voxera_verify (API 36) — 2026-10-15, 새 통화 화면·PiP

데모(가짜 엔진)와, 로컬 livekit-server에 `lk room join --publish-demo` 봇을 붙인 실통화로 확인했다.

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

| 통화 알림(4ad32d1) | 경과 시간·종료 버튼 | CallStyle 알림에 00:08 흐름, "Hang Up" 누르자 통화 종료·서비스 정리·서버에서 퇴장 | Pass |
| 반응 보내기(ef59db5) | 데이터 메시지로 전송, 내 화면에 떠오름 | 봇 로그 `received data "MTpjbGFw"`(= "1:clap"), 내 화면에 박수와 "You" | Pass |
| 반응 받기(실통화) | 원격 참가자 반응 표시 | 주제를 붙여 보내는 두 번째 클라이언트가 없어 확인 못 함. 해석은 단위 테스트 | NOT RUN |
| 데모 반응 | 원격 반응이 이름과 함께 떠오름 | 정상 | Pass |
| 가로 모드 수신 화면(0766e21 이전) | 버튼이 화면 안 | 수락·거절이 아래로 잘림 | Fail → 수정 |
| 가로 모드 수신·음성·그룹·1:1 | 잘림·겹침 없음 | 정상 | Pass |
| 입장 전 마이크 레벨 | 말하면 막대가 참 | 에뮬레이터 입력이 모두 0(호스트 마이크가 들어오지 않음) | NOT RUN |

실기기 PiP, 근접 화면 끄기, 마이크 레벨은 위 Fold7 표에서 확인했다. 발열 때 효과 끄기(E31)는 열 상태를 바꿀 방법이 없어 단위 테스트만 했다.

## Telecom 등록 (에뮬레이터 API 36) — 2026-10-15

로컬 서버 음성 통화 중 `emu gsm call`로 휴대전화를 걸고 통화 키로 받았다.

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| 통화 등록 | 시스템 통화 목록에 self-managed 통화 | `dumpsys telecom`에 ACTIVE, self_mng, 보류 가능 | Pass |
| 휴대전화를 받음 | Voxera 통화 보류, 마이크 끔 | ON_HOLD, 서버에서 MICROPHONE muted=true | Pass |
| 휴대전화가 끝남 (이어 가기 추가 전) | 다시 이어짐 | ON_HOLD로 남음(시스템은 VoIP 통화를 다시 잇지 않음) | Fail → 수정 |
| 휴대전화가 끝남 (수정 후) | 같음 | ACTIVE로 돌아오고 마이크 muted=false | Pass |
| 차량·시계에서 끊기 | onHangUp | 해당 장치가 없어 확인 못 함 | NOT RUN |

## TalkBack (에뮬레이터 API 36) — 2026-10-15

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| TalkBack을 켠 영상 통화 | 조작 막대를 자동으로 숨기지 않음 | 9초 넘게 그대로 보임(평소 4초에 숨김) | Pass |
| 읽는 이름 | 타일·버튼마다 이름과 상태 | "Alex, Speaking", "4 participants", "Stable", Mic/Camera/Flip/More/End | Pass |
| 손가락으로 항목 넘기기·두 번 탭 | 상단 → 영상 → 조작 막대 순서, 두 번 탭으로 동작 | adb가 넣은 터치는 TalkBack 제스처로 인식되지 않아 확인 못 함. 사람이 직접 써 봐야 함 | NOT RUN |

## LiveKit Cloud (wss) — 2026-10-15

테스트용 Cloud 프로젝트에 연결했다. 토큰은 이 Mac의 `lk` 설정으로 15분짜리만 발급했고 API Secret은 앱·저장소에 들어가지 않는다. 상대는 `lk room join --publish-demo` 봇.

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| debug 빌드, 토큰 endpoint 방식 | `wss://` 서버로 입장, 영상 주고받기 | 서버에 마이크·카메라 2트랙, 봇 영상 표시 | Pass |
| 비행기 모드 20초 | "다시 연결하는 중" 안내 후 복구 | 2초 안에 안내, 네트워크 복구 4초 뒤 정상, 두 참가자 다시 ACTIVE | Pass |
| release(R8) 빌드, 기존 토큰 방식 | 같음 | 입장, 2트랙 게시, 봇 영상 표시, 크래시 없음 | Pass |
| 로그에 토큰 노출 | 없음 | logcat에 `eyJ` 0건 | Pass |
| 2대 통화(Fold7 + 에뮬레이터, 인터넷 경유) | 서로 보고 들음 | 양쪽 마이크·카메라 게시, 서로 상대 영상 표시. Fold7 쪽 말소리에 에뮬레이터가 "말하는 중" 표시. 에뮬레이터 마이크는 무음만 보내 반대 방향 소리는 확인 못 함 | Pass |
| 2대 통화 품질(Fold7에서) | 값이 나옴 | 상태 안정, RTT 42ms, 지터 29ms, 손실 0%, 업로드 2.3Mbps. 에뮬레이터 카메라라 수신은 42kbps·9fps | Pass |

## API 매트릭스 (에뮬레이터, Pixel, google_apis arm64) — 2026-10-15

데모 4인 → 크게 보기, 홈 버튼으로 PiP, 데모 음성 → 종료 요약, 로컬 서버 봇과 영상 실통화, 통화 서비스 foreground, 크래시 로그를 차례로 확인했다.

| API | PiP | 종료 요약 | 실통화 입장 | 통화 서비스 | 크래시 |
| --- | --- | --- | --- | --- | --- |
| 26 | Pass (onUserLeaveHint 경로) | Pass | Pass | Pass | 0 |
| 30 | Pass | Pass | Pass | Pass | 0 |
| 31 | Pass | Pass (다시 돌림) | Pass | Pass | 0 |
| 33 | Pass | Pass | Pass | Pass | 0 |
| 34 | Pass | Pass | Pass | Pass | 0 |
| 35 | Pass | Pass | Pass | Pass (연결에 15초 걸려 다시 확인, 마이크·카메라 타입) | 0 |

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

사람 귀로 듣는 음성 품질(실기기 2대), 개발 토큰 서버(Sandbox) 방식,
유선 이어폰 전환, 실제 발열 상황, 와이파이↔LTE 전환, API 26/30 기기, TalkBack, 장시간 통화, 실제 호스트 서버·푸시로 하는 Call Mode.

## 에뮬레이터 voxera_verify (API 36) — 2026-10-15, 넘기는 작은 줄·오디오 장치 알림

데모(가짜 엔진)로 확인했다. 1:1 음성 데모는 8초에 데모 이어폰("Galaxy Buds (demo)")을 연결하고 45초에 끊는다.

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| 9인 크게 보기 작은 줄 넘기기 | "+N" 없이 8명이 한 줄, 끌면 넘어감 | You·Alex·Taylor·Jamie → 끌기 후 Jamie·Riley·Casey·Jordan, 놓으면 미끄러지다 멈춤 | Pass |
| 첫 구현의 작은 줄 끌기 | 같음 | 타일이 먼저 이벤트를 받아 줄이 움직이지 않음 → 터치 여유 거리를 넘을 때만 가로채도록 수정 | Fail → 수정 |
| 작은 줄 타일 누르기 | 그 사람을 크게 보기 | Taylor 누르자 Taylor가 큰 화면, 줄은 처음으로 | Pass |
| 이어폰 없을 때 오디오 시트 | 휴대전화·스피커만 | 휴대전화·스피커 두 줄 | Pass |
| 통화 중 이어폰 연결 | 자동 전환, 상단 알림과 되돌리기 | "Galaxy Buds (demo) · Sound and mic moved to this device · Undo", 막대 아이콘 블루투스로 | Pass |
| 알림의 되돌리기 | 이전 장치로 돌아감 | 휴대전화로 돌아가고 알림 닫힘 | Pass |
| 이어폰 연결 중 오디오 시트 | 블루투스 줄이 생김 | 휴대전화·스피커·블루투스(Galaxy Buds (demo)) 선택됨 | Pass |
| 통화 중 이어폰 끊김 | 알림, 남은 장치로 | "Galaxy Buds (demo) disconnected · Now playing on: Phone" | Pass |
| 음성 그룹 10명 이상 세로 넘기기 | 모두 표시 | 해당 데모 없음. 2026-10-18 로컬 실통화 20명으로 확인(아래 "음성 그룹 20명") | NOT RUN → Pass |
| 실제 블루투스 이어폰 연결 알림 (Fold7) | 위와 같음 | 기기 미사용 | NOT RUN |

## 채팅·손들기 — 2026-10-15

데모(voxera_verify, API 36)와, 로컬 livekit-server에 에뮬레이터 두 대(voxera_verify API 36 "Mina", voxera_api35 "Joon")를 토큰 endpoint로 붙인 실통화로 확인했다.

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| 데모 그룹 손들기 | 타일 손 배지, "손을 들었습니다" 알림 | Taylor 타일 왼쪽 위 배지, "Taylor raised a hand", 25초에 내려감 | Pass |
| 데모 채팅 미리보기·배지 | 시트를 닫은 동안 온 글이 위에 잠깐, 배지 숫자 | "Alex · Loud and clear 👍", 더보기 버튼 배지 2, 더보기 목록 "2 unread messages" | Pass |
| 채팅 시트 보내기 | 내 말풍선 오른쪽, 키보드 위로 시트, 키보드 보내기 키 | 두 줄 글 말풍선, 데모 답장 👍, 열고 나면 배지 사라짐 | Pass |
| 미리보기 누르기 | 채팅 시트가 열림 | 열림(다크 모드) | Pass |
| 내 손들기(음성) | 반응 시트 "손 들기", 위에 "손을 들었어요 · 손 내리기" | 그대로, 누르면 내려감 | Pass |
| 숨은 조작 막대 | 격자 타일을 누르면 먼저 막대가 나옴 | 처음 구현은 누른 사람이 크게 보기로 바뀌어 막대를 꺼낼 길이 없었음 → 수정 후 막대만 나옴 | Fail → 수정 |
| 입력 칸 | 화면이 매초 다시 그려져도 글자를 잃지 않음 | 문자열 입력 칸은 "HHi everyone, I c"처럼 겹치고 빠짐 → TextFieldState로 바꾼 뒤 정상(긴 adb 입력이 16자 안팎에서 끊기는 것은 에뮬레이터 쪽 한계로, 단어별로 넣으면 모두 들어감) | Fail → 수정 |
| 실통화 채팅 | 상대에게 미리보기·배지 | Mina "Hello Joon, real chat here" → Joon 화면 미리보기와 배지 1 | Pass |
| 실통화 손들기 | 상대 아바타에 배지 | Joon이 손 들기 → Mina 화면 Joon 아바타에 배지 | Pass |
| 늦게 들어온 사람의 손들기 | 이미 든 손이 보임 | Mina가 나갔다 "Join again" → 바로 배지 보임 | Pass |
| 먼저 와 있던 사람(트랙 없음) | 입장 즉시 보임 | 마이크를 끈 Joon이 Mina에게 안 보이고 "대기 중" → 입장 직후 기존 참가자를 알리도록 수정 후 바로 보임 | Fail → 수정 |
| 로그 | 채팅 글·토큰 없음 | 두 기기 logcat에 글 0건, `eyJ` 0건 | Pass |
| 장시간 통화 | 끊기지 않음 | Joon(API 35) 1시간 29분 유지 | Pass |
| debug 빌드 과부하 | — | 에뮬레이터 두 대 + 호스트 load 16에서 API 36 debug 빌드가 첫 화면 구성에 프레임당 최대 4.5초 걸려 입력 ANR 1회. 메인 스레드는 막히지 않고 계속 진행 중이었음. release·단독 실행에서는 보지 못함 | 관찰 |
| 다른 SDK(LiveKit Meet 등)와 채팅 호환 | — | 이후 표준 텍스트 스트림(`lk.chat`)으로 바꿈. Voxera끼리(에뮬레이터 2대, 로컬 서버) "Stream chat via lk.chat" 전달·배지 확인. Meet 브라우저와 직접 주고받기는 안 해 봄 | NOT RUN |

## Fold7 실제 블루투스 — 2026-10-16

LiveKit Cloud(voxera-test) 음성 통화, Galaxy Buds2가 미리 연결된 상태로 시작했다.

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| 이어폰 연결된 채 입장 | 처음부터 이어폰으로 | 오디오 아이콘 블루투스, 시스템 통신 경로 bt_sco Galaxy Buds2 | Pass |
| 오디오 시트 | 연결된 장치만 | 블루투스(Galaxy Buds2), 휴대전화, 스피커. 워치는 안 나옴 | Pass |
| 내장 장치 이름 | 한국어 화면에 영어 이름이 없음 | "Earpiece", "Speakerphone"이 밑에 붙어 있었음 → 이어폰·USB만 기기 이름을 보이게 수정 | Fail → 수정 |
| 장시간 | 끊기지 않음 | 18분 47초 유지 | Pass |
| 통화 중 이어폰 넣기·꺼내기 (첫 시도) | 끊김 알림, 자동 전환 알림 | 사용자가 자리를 비워 버즈를 넣고 꺼내지 못함 | NOT RUN |
| 통화 중 이어폰 꺼내기 (접은 화면) | 자동 전환, 알림과 되돌리기 | 0:45에 연결 → 같은 순간 "소리와 마이크를 이 기기로 옮겼어요 · 되돌리기", 오디오 아이콘 블루투스, 몇 초 뒤 알림 사라짐. 삼성 시스템 버즈 배터리 팝업이 처음 1~2초 위에 겹침 | Pass |
| 통화 중 이어폰 넣기 | 끊김 알림, 휴대전화로 | 2:12에 "Galaxy Buds2 (3FE4) 연결이 끊겼어요 · 지금 출력: 휴대전화", 아이콘 휴대전화 | Pass |
| 로그 | 토큰 없음 | 앱 로그 `eyJ` 0건. 전체 logcat의 1건은 삼성 시스템 IMS 로그(DEBUG_IMS)의 base64 데이터에 우연히 든 글자로 토큰이 아님 | Pass |
| 오디오 알림 위치 (이후 변경) | 시스템 팝업과 겹치지 않음 | 위쪽 알림이 삼성 버즈 팝업에 가려져, 오디오 버튼 위 꼬리 달린 말풍선으로 옮기고 8초(끊김 6초) 보이게 함. 에뮬레이터 음성 데모: 말풍선 꼬리가 오디오 버튼을 가리킴. 영상 데모: 숨은 막대가 나오고 더보기 버튼을 가리킴, 8초 뒤 사라짐 | Pass |
| 말풍선으로 바꾼 뒤 Fold7 실기기 (접은 화면, Cloud 음성) | 버튼 위 말풍선, 시스템 팝업과 안 겹침 | 0:46 버즈 연결 → 오디오 버튼 위 "소리와 마이크를 이 기기로 옮겼어요 · 되돌리기", 같은 순간 삼성 버즈 팝업은 화면 위쪽이라 겹치지 않음. 1:06 넣음 → "연결이 끊겼어요 · 지금 출력: 휴대전화" 말풍선. 앱 로그 `eyJ` 0건 | Pass |

## 첫 실행 시간 — 2026-10-16, voxera_verify(API 36, 3GB) 한 대만 실행

`am start -W -S` 3회. 앞서 에뮬레이터 두 대를 함께 돌릴 때 본 debug 빌드 ANR이 앱 자체 문제인지 보려고 쟀다.

| 빌드 | TotalTime (ms) | Result |
| --- | --- | --- |
| debug | 1857, 1657, 1280 | 관찰 |
| release (R8) | 1213, 956, 980 | 관찰 |

## 메모리 장시간 측정 — 2026-10-16, voxera_verify(API 36), release 빌드

9인 그룹 데모에서 한 사람을 크게 본 채 20분 동안 두고 `dumpsys meminfo`를 1분마다 읽었다(반응·말하는 사람 바뀜·채팅·손들기가 계속 일어남).

| 항목 | 시작(1분) | 19분 | Result |
| --- | --- | --- | --- |
| TOTAL PSS | 37.7 MB | 40.1 MB (중간 37~40 MB 사이를 오감) | Pass |
| Java Heap | 4.1 MB | 4.1 MB | Pass |
| Native Heap | 14.6 MB | 15.0 MB | Pass |
| Views | 11 | 11 | Pass |
| 크래시 | — | 0건, 같은 프로세스 유지 | Pass |

## 통화 알림 아이콘 — 2026-10-16, voxera_verify(API 36), 로컬 livekit-server 음성 통화

알림 작은 아이콘을 Voxera 마크로 바꾸고 상황 배지를 붙인 뒤, 실제 통화 알림에서 아이콘 리소스와 상태 표시줄을 확인했다. 영상·화면 공유·보류는 영상으로 다시 입장해 확인했다(`emu gsm accept`로 받으면 시스템이 Voxera 통화를 보류하지 않아, 앞선 Telecom 확인처럼 통화 키로 받았다).

| 상황 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 입장 직후 | 배지 없는 마크, "통화 중" 문구 | `voxera_ic_notification`, 상태 표시줄에 마크 | Pass |
| 마이크 끔 | 사선 배지, "마이크 꺼짐" 문구 | `voxera_ic_notification_muted`, 본문 "Your microphone is off…" | Pass |
| 마이크 다시 켬 | 배지 없는 마크로 돌아감 | `voxera_ic_notification` | Pass |
| 서버를 멈춰 재연결 | 점 세 개 배지 | `voxera_ic_notification_reconnecting`. 에뮬레이터가 느려 바뀌기까지 25초 남짓 걸림 | Pass |
| 재연결 시간 초과 | 통화가 끝나면 알림도 사라짐 | "Call disconnected", 알림 없어짐 | Pass |
| 영상으로 입장 | 카메라 배지 | `voxera_ic_notification_video`, 상태 표시줄에 카메라 배지 | Pass |
| 화면 공유 시작 | 화면 공유 배지 | `voxera_ic_notification_sharing`. 공유 중에는 상태 표시줄에 시스템의 빨간 화면 공유 칩이 대신 보임 | Pass |
| 화면 공유 멈춤 | 카메라 배지로 돌아감 | `voxera_ic_notification_video` | Pass |
| `emu gsm call` 후 통화 키로 받음 | 보류 배지 | Voxera 통화 ON_HOLD, 오디오 모드 MODE_IN_CALL, `voxera_ic_notification_on_hold`, 상태 표시줄에 일시정지 배지 | Pass |
| 휴대전화 끊음 | 이어 가고 카메라 배지로 돌아감 | ACTIVE, `voxera_ic_notification_video` | Pass |
| 로그 | 토큰 없음 | logcat `eyJ` 0건 | Pass |

CallStyle 알림을 펼친 화면에서는 시스템이 작은 아이콘 대신 앱 아이콘을 사람 사진 옆에 붙여 보여 준다. 배지는 상태 표시줄과 잠금 화면 같은 작은 아이콘 자리에서 보인다.

## Kinetica UI 키트 적용 — 2026-10-16, voxera_verify(API 36), 브랜치 feat/kinetica-ui

Kinetica `com.naurylab:kinetica-*:1.0.0-alpha01`(정식 배포 전, 로컬 Maven. 처음 ed3bafc, 이후 94bcdc3 판)을 붙인 뒤 오프라인 데모와 로컬 livekit-server 실통화(`lk room join --publish-demo` 영상, TextureView)로 확인했다.

| 항목 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 의존성 | 충돌 없이 debug·release(R8) 빌드 | 전체 검사(test, lint, apiCheck, 두 빌드) 통과 | Pass |
| 외부 앱 | mavenLocal로 Voxera를 받는 `samples/consumer`가 Kinetica까지 받아 실행 | pom에 Kinetica가 runtime 의존성으로 들어가고, release 빌드가 통화 화면까지 크래시 없이 뜸 | Pass |
| 영상 위 유리 | 도크·상단 바 뒤 영상이 흐리게 비침 | TextureView 영상의 잔디 무늬가 뭉개지고 색만 비침 | Pass |
| 시트 | 뒤 통화 화면이 물러나며 흐려짐, 끌어 닫기, 뒤로 가기, 키보드 따라 올라감 | 모두 됨. 판 안 카드가 판을 뚫고 배경을 비추던 문제는 KSheet로 바꿔 해결 | Pass |
| 채팅 시트 + 키보드 | 메시지가 많아도 입력 칸이 키보드 위 | 처음에는 입력 칸이 키보드 뒤로 잘림 → 목록이 줄어들게 고친 뒤 통과 | Fail → 수정 |
| 아일랜드 알림 | 입퇴장·손들기·채팅이 차례로, 상단 버튼을 가리지 않음 | 처음에는 상단 줄 버튼을 가려 PiP 버튼이 안 눌림 → 상단 줄 아래로 내린 뒤 실통화 입장 알림으로 통과 | Fail → 수정 |
| 선택 묶음, 글꼴 1.5배 | 이름이 잘리지 않음 | ed3bafc 판은 "Dev toke…"로 잘림 → 피드백 후 94bcdc3 판에서 두 줄로 넘어감 | Fail → 수정 |
| 스위치·추이 그래프·말풍선·숫자 굴림·빛 테두리·색종이·연결 중 반짝임 | 라이트·다크에서 깨지지 않음 | 화면으로 확인. 빛 테두리는 처음에 타일 안쪽에 얼룩이 생겨 clip 바깥으로 옮김 | Pass |
| 상태 표시줄 아이콘 | 바탕에 맞는 밝기 | 어두운 테마를 쓰는 외부 앱에서 흰 아이콘이 밝은 바탕에 묻힘 → 화면마다 아이콘 밝기를 고르게 한 뒤 어두운 아이콘 | Fail → 수정 |
| PiP | 작은 창으로 줄어듦 | pinned, 아바타 표시 | Pass |
| 입력 오류 흔들림 | 오류가 생길 때 입력란이 흔들림 | 420ms로 짧아 화면 캡처로는 잡지 못함 | NOT RUN |
| Fold7 실기기 | 같은 화면 | 무선 연결이 끊겨 확인 못 함 | NOT RUN |

643df65 판(2차 피드백 반영)으로 다시 확인했다.

| 항목 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 채팅 입력(상태 기반) | 입력을 잃지 않고 Enter로 보냄, 한도 가까이에서만 글자 수 | "Hello" 입력 후 Enter로 보내짐, 칸이 비워짐 | Pass |
| 채팅 입력 칸 이름 | TalkBack이 칸 이름을 읽음 | 입력 칸 노드에 이름이 없음(Kinetica에 알림) | Fail |
| 아일랜드 얼굴 | 입퇴장·손들기·채팅 알림 앞에 그 사람 얼굴 | 얼굴과 손 표시가 붙음. 채팅 시트를 열면 채팅 미리보기가 닫힘 | Pass |
| 인원수 숫자 굴림 | 2→3→4로 빠르게 바뀌어도 마지막 숫자가 보임 | 숫자가 빈칸으로 남음(Kinetica에 알림) → 인원수는 일반 글자로 되돌림 | Fail → 우회 |
| 받은 말풍선 | 시트 위에서 그림자 없이 옅게 | 옅게 그려짐 | Pass |

179bf7f 판(3차 피드백 반영)으로 다시 확인했다.

| 항목 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 인원수 숫자 굴림 | 1→2→3→4로 빠르게 바뀐 뒤 4가 보임 | 데모 두 번 모두 4로 멈춰 보임. 인원수 굴림을 다시 켬 | Pass |
| 채팅 입력 칸 이름 | 입력 칸에 이름 | 입력 칸(EditText) 바로 아래 자식 노드에 "Message"가 붙음(Compose 입력 칸 구조상 자식으로 나감) | Pass |
| TalkBack이 실제로 읽는 문장 | "Message, 편집 상자"처럼 이름을 읽음 | 에뮬레이터에서 TalkBack 음성 출력을 꺼낼 방법이 없어(오류 로그만, root 불가) 확인 못 함. 사람이 직접 들어 봐야 함 | NOT RUN |

40ec180 판: 전체 검사 통과. 채팅 입력 칸의 안쪽 여백이 넓어져 커서와 글자가 캡슐 끝 곡선에 붙어 보이지 않음(에뮬레이터 화면으로 확인, Pass).

### Fold7 실기기 (펼친 안쪽 화면, One UI, 창 블러 꺼짐) — 2026-10-16, 40ec180 판

로컬 livekit-server에 adb reverse(7880/7881/3881)로 붙어 `--publish-demo` 봇과 영상 통화를 했고, 오프라인 4인 데모도 돌렸다. debug 빌드.

| 항목 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 영상 위 도크 | 뒤 영상이 흐리게 비침 | 잔디·하늘이 매끈하게 흐려져 비침(화면 안 블러는 창 블러 설정과 무관) | Pass |
| 참가자 시트 | 뒤 화면이 물러나며 흐려짐, 넓은 화면에서 가운데 | 640dp 폭으로 가운데, 뒤 영상과 내 카메라 화면이 흐려짐 | Pass |
| 아일랜드·빛 테두리 | 얼굴과 손 표시, 말하는 사람 테두리 | "Taylor 님이 손을 들었습니다"와 얼굴·손 표시, 테두리가 말하는 사람을 따라감 | Pass |
| 프레임 | 시트 열기·닫기·끌기 반복 864프레임 | 끊긴 프레임 3.0%, 중간 8ms, 90% 17ms, 99% 77ms(시트가 열리는 순간). release는 로컬 평문 서버에 붙을 수 없어 못 잼 | 관찰 |
| 로그 | 토큰 없음 | 앱 로그 `eyJ` 0건 | Pass |
| 접은 바깥 화면, TalkBack 음성 | 같은 화면, 입력 칸 이름을 읽음 | 기기를 접고 TalkBack을 직접 들어 봐야 해서 못 함 | NOT RUN |

0dff505 판(시트 성능 개선), Fold7 같은 방법 2회: 끊긴 프레임 4.2%·5.2%, 중간 11·13ms, 90% 19·20ms, 99% 40·53ms(앞 판 3.0%, 8ms, 17ms, 77ms). 가장 느린 프레임은 줄었지만 평균은 조금 늘어 관찰로 남긴다. 시트가 열린 동안 뒤 영상이 계속 움직이는지는 에뮬레이터 실통화로 따로 확인: 시트를 연 채 3초 간격으로 찍은 두 장에서 흐린 뒤 영상의 구름이 움직임(Pass).

449e653 판, Fold7 같은 날 같은 조건에서 Kinetica 적용 전 빌드(feat/VOX-001-foundation)와 나란히 쟀다. debug 빌드, 로컬 실통화 영상.

| 측정 | Kinetica 전 | 449e653 |
| --- | --- | --- |
| 시트 열기·끌어 닫기 3회(2번 잼) | 끊김 1.9%·2.1%, 중간 7ms, 90% 11ms, 99% 32·25ms | 끊김 3.8%·2.6%, 중간 11·13ms, 90% 19·20ms, 99% 32·34ms |
| 조작 막대만 보이는 가만히 있는 통화 | 중간 7ms, 90% 12ms | 중간 10·11ms, 90% 17·18ms |

시트를 여는 순간의 느린 프레임(99%)은 Kinetica 전과 비슷해졌다. 다만 영상 위 유리가 보이는 동안은 매 프레임 3~4ms가 더 들어, 중간값이 120Hz 한 프레임(8.3ms)을 넘는다. 조작 막대는 영상 통화에서 4초 뒤 숨으므로 통화 내내 드는 비용은 아니다. Kinetica에 알림.

블러 반경 실험(449e653 판, Fold7, 조작 막대가 보이는 정지 상태 통화, 3번씩): 도크 반경 12dp 중간 9~10ms·90% 14~15ms, 18dp 10~11ms·14~15ms, 24dp(지금 값) 10ms·13~14ms, 32dp 9~10ms·13~16ms. 반경과 상관없이 같아서, 남은 비용은 블러 크기가 아니라 프레임마다 블러를 다시 거치는 고정 비용으로 본다. 반경은 24dp 그대로 둔다.

c857437 판: 전체 검사 통과. 권장에 따라 말하는 사람 빛 테두리를 영상 무대(배경 기록) 밖 위층으로 옮겼고, 에뮬레이터에서 격자·크게 보기 아래 줄·시트가 열려 무대가 물러날 때 모두 제자리에 그려지는 것을 확인했다(Pass). 인원수 숫자 굴림은 이 판에서 4명·9명 데모 모두 빈칸이 다시 보여(Kinetica에 알림) 일반 글자로 되돌렸다(Fail → 우회). 에뮬레이터는 프레임 통계가 4950ms로만 나와 성능 비교에 쓸 수 없어, 위층 이동 효과는 Fold7이 다시 연결되면 잰다(NOT RUN).

1f1a76c 판: 전체 검사 통과. 인원수 숫자 굴림을 다시 켜서 9명 데모를 세 번 돌렸지만 세 번 모두 0:04에 숫자 자리가 비어 있었다(투명해진 것이 아니라 자리 폭이 사라짐, Fail). 같은 판에서 인원수를 일반 글자로 두면 4명·9명 모두 정상이라 숫자 굴림 컴포넌트에만 생기는 문제로 좁혀 Kinetica에 알렸다. 인원수는 일반 글자로 둔다.

01926ba 판: 인원수 빈칸의 원인은 숫자 굴림이 아니라 Voxera 상단 줄 레이아웃이었다. 휴대폰 폭에서 버튼 네 개와 데모 표시가 함께 있으면 시간 알약 자리가 90dp 남짓인데, 상태 점이 나타나면 내용이 넘쳐 마지막 칸인 인원수 폭이 0이 됐다. 알약 안 간격을 8dp → 4dp, 좌우 여백을 12dp → 10dp로 줄이고, 자리가 모자라면 데모 표시를 숨기게 했다. 에뮬레이터에서 4명·9명 데모 0:04(상태 점 표시 뒤)에 인원수가 알약 안에 보이고, 글꼴 1.3배·1.5배에서도 시간·인원이 온전하며 데모 표시만 사라진다(Pass). 인원수 숫자 굴림을 다시 켰다.

### Fold7 바깥 화면(접은 상태), 같은 시각 세 빌드 비교 — 2026-10-17

빌드: Kinetica 적용 전(feat/VOX-001-foundation), 빛 테두리를 무대 안에 그리던 판(f712a5e 직전), 위층으로 옮긴 판(01926ba 판 위 62e5070). debug. 조작 막대가 보이는 정지 상태를 3.2초씩, 빌드마다 4번.

| 화면 | 적용 전 | 테두리 무대 안 | 테두리 위층 |
| --- | --- | --- | --- |
| 오프라인 4인 데모(배경 정적, 테두리 계속 돎) | 중간 5~7ms, 90% 7~8ms | 6~7ms, 8~9ms | 6~7ms, 8~9ms |
| 로컬 실통화 1:1 영상(TextureView 30fps) | 중간 5~6ms, 90% 7~8ms | 8~10ms, 12~14ms | 6~9ms, 11~12ms |

배경이 정적이면 Kinetica 비용은 90% 기준 1ms 안쪽이다. 영상 위에서는 테두리를 위층으로 옮겨 90%가 1~2ms 줄었고, 적용 전보다 3~4ms 더 드는 것은 영상 프레임마다 블러를 다시 하는 몫으로 본다. 접은 화면 레이아웃도 깨지지 않았다(Pass). 앱 로그 `eyJ` 0건.

121b113 판: 전체 검사 통과(이름이 바뀐 KAvatar 데이터 클래스는 Voxera가 쓰지 않음). 쇼케이스 앱의 홈 → 연결 설정 → 입장 전을 PushNavigation(밀어 넣기)로, 통화 진입을 KTransitionContent(확대)로 바꿨다. 에뮬레이터에서 뒤로 가기 제스처를 하는 동안 연결 설정 화면이 손가락을 따라 밀려나고 아래 홈이 드러나며, 손을 떼면 홈으로 돌아오고 스크롤 위치도 그대로다(Pass). 통화를 끝내면 홈으로 돌아온다(Pass).

f74d015 판(블러를 프레임당 한 번만 하고 유리들이 잘라 씀): 전체 검사 통과. 에뮬레이터 실통화 영상 위 도크·상단 버튼·시트의 블러 모양이 이전 판과 같고 잘린 자리도 맞다(Pass). Fold7은 무선 연결이 끊겨 GPU 시간 비교는 못 함(NOT RUN).

Kinetica 일반 구성 요소 추가 적용(f74d015 판, 에뮬레이터): 오디오 출력 시트는 묶음 판 안 선택 행(KListSection + KRadioRow, 고른 행 앞 타일이 강조색), 참가자 시트는 묶음 목록 행(kListSection + KListItem, Voxera 얼굴과 상태 아이콘, 고정 버튼), 품질 시트 지표는 통계 카드(KStatCard, 숫자 굴림·흐름 그래프), 연결 설정의 테스트 전용 경고는 KAlert, 로컬 서버 허용은 KSwitchRow로 바꿨다. 라이트·다크에서 깨짐 없음, 행 전체를 눌러 토글·선택됨(Pass). 전체 검사 통과.

Kinetica 통화 부품 적용(c51dfff 판, 에뮬레이터): 통화 조작 막대를 KCallDock, 상단 바를 KCallTopBar로 바꿨다. 도크는 1:1 영상(버튼 9개)에서 마이크·카메라·전환·더보기(배지 1)·종료로 접히고, 더보기를 누르면 Voxera 더보기 시트에 접힌 조작(채팅·화면 공유·효과·반응·오디오)이 상태 글자와 함께 나온다(Pass). 오디오 버튼이 접혀 있으면 이어폰 말풍선은 더보기 버튼을 가리킨다(Pass). 상단 바는 연결 품질 막대·굴러가는 통화 시간·인원·데모 칩을 담고, 글꼴 1.5배·다크에서도 오른쪽 버튼이 밀리지 않으며 도크 이름표는 1.3배에서 멈춘다(Pass). 채팅 배지가 TalkBack에 두 번 읽히던 것은 Kinetica가 badgeDescription을 추가해 고쳤고, 단위 테스트로 상태 글자만 읽는 것을 확인했다. TalkBack 실제 음성은 NOT RUN. 전체 검사 통과.

Kinetica 참가자 타일·격자 적용(c51dfff 판, 에뮬레이터): 영상 무대를 KParticipantGrid, 타일을 KParticipantTile로 바꿨다. 같은 격자 상태로 화면 배경에는 영상 층, 그 위에는 장식 층(이름표·말하는 사람 테두리·누르기·TalkBack)을 그려, 직접 만든 위층 테두리(SpeakingGlows)는 지웠다. 공개 ParticipantTile은 시그니처를 그대로 두고 안쪽만 KParticipantTile로 바꿔 PiP 창도 같은 모양이다. 누구를 크게 볼지는 Voxera가 정하고(stageModel), 자리 계산은 Kinetica가 한다.
- Pass: 1:1 상대 전체 화면과 내 작은 창, 작은 창 끌기와 가까운 모서리 붙기, 작은 창 탭으로 서로 바꾸기, 9명 3×3, 격자 탭으로 고정(큰 화면과 아래 띠, 고정 표시), 띠 칸 탭으로 고정 바꾸기, 막대가 숨었을 때 탭하면 막대만 꺼내기, 화면 공유 크게 보기와 띠, 위층 글로우, 손 배지, 아바타 물결, 다크 모드, 글꼴 1.5배, PiP 창.
- Fail → Pass(089cba4 판): 고정 시 아래 띠를 손가락으로 넘겨도 움직이지 않던 것은 Kinetica 격자의 터치 상자 크기 문제였고, 고친 판에서 띠가 넘어가고 넘긴 뒤 접근성 좌표도 따라간다. 막대를 숨기면 무대가 세로로 늘어 4명이 1열 4단, 9명이 2열 5단으로 바뀌던 것은 Kinetica가 연 columns로 Voxera가 인원과 화면 방향만 보고 열 수를 정해(stageColumns), 막대를 숨겨도 4명 2×2·9명 3×3 그대로다.
- TalkBack 실제 음성은 NOT RUN. Fold7 성능 비교는 NOT RUN.

## TalkBack 실제 음성과 무대 성능 (에뮬레이터 API 36, Kinetica 818f8cc 판) — 2026-10-18

TalkBack 16에서 실제로 읽는 문장을 확인했다. 방법:
- 읽는 문장 받기: TalkBack 설정 > 고급 > 개발자 설정에서 로그 수준을 VERBOSE로 두면, logcat `talkback: SpeechControllerImpl: Speaking fragment text="..."`에 읽는 문장이 그대로 나온다.
- 항목 넘기기: adb input 키와 터치는 TalkBack이 받지 않는다. 대신 `adb shell hid -`로 uhid USB 키보드를 등록하고(셸이 uhid 그룹), TalkBack 키보드 단축키를 보낸다. Alt+→는 다음, Alt+←는 이전, Alt+Enter는 누르기다.
- 대상 AVD는 voxera_verify이고, 키보드 단축키를 쓰려고 hw.keyboard=yes로 켰다.

| 항목 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 4명 격자 읽는 순서 | 상단 → 타일 → 조작 막대 | "Excellent connection" → "0:14" → "4 people in call" → Speaker view·PiP·People·Quality → You·Alex·Taylor·Jamie(말하는 중·손 든 상태 포함) → "On, Mic"·"On, Camera"·Flip·"2 new, More"·End | Pass |
| 통화 시간 | "1:47"만 | "1:47" 다음에 "Colon"을 한 번 더 읽음. Kinetica RollingText가 ":" 글자를 묶음에 남기던 것을 6f9b6a5에서 고쳤고, 이제 "0:08"만 읽는다. 음성 통화 무대 시간도 같은 부품이다 | Fail → Pass |
| 격자 타일 안내 | 누르면 크게, 동작 메뉴 | "Alex, Speaking, Button, Press Alt + ENTER to Show large, … Actions available". Alt+Enter로 고정하면 "Alex, Pinned"를 알리고 큰 화면 → 띠(You·Taylor·Jamie) → 조작 막대 순서다 | Pass |
| 1:1 타일 | 상대는 막대 보이기·숨기기, 내 창은 바꾸기 | "Alex Morgan, Speaking … Show or hide controls", "You … Swap views". Alt+Enter로 서로 바뀐다(화면으로 확인) | Pass |
| 9명 격자 | 9명 모두, 상태 함께 | "Taylor, Hand raised", "Morgan, Mic off", "Sam, Mic off" 등 9명 모두 | Pass |
| 화면 공유 | 공유 화면 → 띠 | "Taylor (screen), Speaking"으로 공유 화면에 말하는 중이 붙었다. 공유 화면 타일은 말하는 중·마이크·손 상태를 빼고 카메라 타일에만 둔다. 이제 "Taylor (screen)" → You → "Taylor, Speaking" → "Alex, Speaking"으로 읽는다 | Fail → Pass |
| 채팅 미리보기 알림 | "Alex, Loud and clear 👍" | 앞에 "Button, capital A"를 붙여 읽었다. "capital A"는 Voxera 아바타 이니셜이 읽히던 것이라, 이니셜은 장식으로 TalkBack에서 뺐다. "Button"은 Kinetica KIsland의 역할 이름이라 818f8cc에서 고쳤다. 이제 알림은 "Alex, Loud and clear 👍"로 읽고, 초점을 옮기면 한 번만 멈춘다 | Fail → Pass |
| 알림에 초점을 둘 때 누르기 안내 | "두 번 탭하여 열기" 같은 안내 | 알림이 4초 만에 닫히고 반응 알림이 끼어들어 안내 문장까지 듣지 못함 | NOT RUN |
| 더보기 시트 | 시트 안에서만 초점이 돌고 상태 함께 | Close에서 시작해 "Chat, 2 unread messages"·"Share, Off"·"Effects, Off"·React·Audio를 읽고, 다시 Close로 돈다 | Pass |
| 음성 통화 | 이름·시간·조작 | Alex Morgan, 통화 시간, "Good connection", "On, Mic"·Chat·React·Audio·End | Pass |
| 이어폰 알림 말풍선 | 뜨면 알림 | 말풍선이 뜨면 초점이 말풍선으로 가서 "Galaxy Buds (demo) disconnected"와 Undo를 읽음 | Pass |
| 손가락 제스처(스와이프·두 번 탭) | 위와 같음 | 키보드로 같은 동작을 확인했다. 손가락 제스처 자체는 adb로 넣을 수 없음 | NOT RUN |

무대 성능은 타일·격자를 바꾸기 전 판(d675c03)과 지금 판을 같은 에뮬레이터에서 비교했다. 조건은 다음과 같다.
- release 빌드에 `cmd package compile -m speed`를 했고, 시나리오마다 15초씩 두 번 번갈아 쟀다.
- 에뮬레이터를 `-gpu host`로 띄웠다. 그래야 GPU 시간이 실제 값으로 나온다. 예전에 4950ms만 나온 것은 소프트웨어 GPU 때문이었다.

| 시나리오 | 이전 판 프레임 p50/p90, 끊김, GPU p50/p90 | 지금 판 |
| --- | --- | --- |
| 9명 3×3 | 26~28/30~31ms, 0.8%, 19~20/22ms | 24~25/29~30ms, 0.6~0.8%, 19/21~22ms |
| 4명 2×2 | 23/28~29ms, 0.3~0.6%, 18~19/21ms | 23~24/27~29ms, 0.6~0.9%, 19/21ms |
| 1:1 | 25~26/34~36ms, 1.0%, 20/22ms | 26/34ms, 0.8~1.0%, 20/22ms |

결과는 이렇다.
- 두 판 모두 15초에 약 900프레임으로, 거의 60fps다.
- 프레임 시간·끊김·GPU 시간 차이는 반복 측정 사이의 차이 안에 든다.
- 따라서 두 층 무대(영상 층 + 장식 층)로 바꾼 뒤에도 느려지지 않았다(Pass).

절대값은 Mac GPU 기준이라 Fold7 값은 아니다. Fold7 실측은 NOT RUN.

## 남은 NOT RUN 다시 확인 (에뮬레이터 API 36) — 2026-10-18

손가락 제스처는 `adb shell hid -`로 등록한 HID 터치스크린으로 보냈다.
- 장치 설정: Digitizer Touch Screen, Contact ID·Tip·X/Y 0..32767, feature report 2 = 최대 접촉 수.
- 안드로이드가 이 장치를 DIRECT TOUCH_SCREEN으로 잡기 때문에, TalkBack은 쓸기와 두 번 탭을 사람 손가락처럼 받는다. adb input으로 넣은 터치는 TalkBack 제스처가 되지 않는다.
- 읽는 문장은 앞 절처럼 VERBOSE 로그로 받았다.

| 항목 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 손가락으로 항목 넘기기·두 번 탭 | 상단 → 영상 → 조작 막대, 두 번 탭으로 동작 | 오른쪽 쓸기로 상단 → 타일 → 조작 막대 순서. 안내는 "Double-tap to Show large…". 마이크에서 두 번 탭하면 "Applying…" → "Off"로 꺼짐(화면에 초점 테두리와 꺼진 마이크 확인) | Pass |
| 고정 후 띠의 화면 밖 사람 | 쓸어 넘기면 띠가 따라 움직여 모두 닿음 | 처음에는 보이는 4명까지만 가고 조작 막대로 넘어갔다(화면 밖 타일이 트리에 없고 띠에 스크롤 정보 없음). Kinetica c93cad4 뒤로 오른쪽 쓸기는 Sam까지 닿는다. 왼쪽으로 돌아올 때는 띠는 되돌아오지만 초점이 큰 타일로 빠져 Morgan~Alex를 건너뛴다(Kinetica에 알림) | 오른쪽 Pass, 왼쪽 Fail |
| 알림 섬 누르기 안내 | 누를 수 있다는 안내 | "Alex, Loud and clear 👍" 다음 "Double-tap to Open. Actions available". 두 번 탭하면 채팅 시트가 열림 | Pass |
| 채팅 시트 | 메시지마다 한 번, 입력 칸 이름 | 메시지마다 "Jamie: …"와 말풍선 글자로 두 번 멈췄다. 고른 글자 노드를 지우고 한 칸으로 묶었으며, 길게 눌러 복사하는 대신 "Copy" 동작을 넣었다. 입력 칸은 "Edit box. Message. Message."로 이름을 두 번 읽었다(label과 placeholder가 같은 글). placeholder만 넘겨 "Edit box. Message"가 됐다 | Fail → Pass |
| TalkBack이 실제로 읽는 문장(채팅 입력 칸) | "Message, 편집 상자"처럼 이름 | 위 칸과 같이 "Edit box. Message" | Pass |
| 연결 설정 입력 칸 | 칸마다 한 번 | 칸마다 이름표 → 입력 칸 → "Required"로 세 번 멈췄다. 입력 칸이 이름과 오류를 함께 읽으므로 위아래 글자를 TalkBack에서 뺐다. 이제 "Edit box. Token server ID. Enter your own ID. Error: Required" 한 번 | Fail → Pass |
| 입력 오류 흔들림 | 오류가 생길 때 입력란이 흔들림 | 화면 녹화(60fps)에서 입력 칸 왼쪽 끝이 64 → 76 → 46 → 76 → 53 → 70 → 61 → 64px로 약 0.45초 좌우로 흔들린 뒤 제자리 | Pass |
| 반응 받기(실통화) | 원격 참가자 반응 표시 | 로컬 LiveKit 서버에 LiveKit Python SDK(1.1.21) 참가자를 넣어 `voxera.reaction`에 `1:heart`를 보냈다. Voxera 음성 통화에 하트와 "Py Bot" 이름표가 떴다 | Pass |
| 다른 SDK와 채팅 호환 | 표준 텍스트 스트림으로 주고받음 | Python SDK가 `lk.chat`으로 보낸 글이 Voxera 알림 섬과 채팅 배지로 왔고, Voxera가 보낸 글을 Python이 `lk.chat`에서 같은 글로 받았다. LiveKit Meet 브라우저와 직접은 안 해 봄 | Pass (Meet는 NOT RUN) |
| 음성 그룹 10명 이상 세로 넘기기 | 모두 표시 | 데모가 없어 단위 테스트로 확인. 20명이면 마지막 사람이 처음에는 화면 밖이고, 넘기면 보이며, 목록에 스크롤 동작이 있다 | Pass |
| 입장 전 마이크 레벨 | 말하면 막대가 참 | 에뮬레이터에 Mac 마이크를 연결했지만, Mac 출력 음량이 0이라 시험 음성을 내지 못했다. 소리를 크게 틀어야 해서 하지 않았다 | NOT RUN |
| 접은 바깥 화면 TalkBack | 입력 칸 이름을 읽음 | 휴대전화 크기 에뮬레이터에서 같은 화면 "Edit box. Message". 실제 Fold7 바깥 화면은 안 해 봄 | NOT RUN |

## 12인 그룹 쪽 넘기기와 Kinetica 띠·경고 상자 (에뮬레이터 API 36, Kinetica f8be4e6 판) — 2026-10-18

쇼케이스에 12인 그룹(DemoScenario.GROUP_TWELVE) 시나리오를 넣었다. 한 쪽에 9명까지 놓으므로 격자가 두 쪽으로 나뉜다. TalkBack 확인은 앞 절과 같이 HID 터치스크린 손가락 쓸기와 VERBOSE 로그로 했다.

| 항목 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 손가락으로 쪽 넘기기 | 옆으로 넘기면 다음 쪽 | 1쪽 3×3(9명)에서 넘기면 2쪽 Avery·Quinn·Drew, 아래 점이 2쪽을 가리킴 | Pass |
| TalkBack 쪽 넘기기 | 1쪽 끝에서 다음 쪽 사람에게, 거꾸로도 | 오른쪽 쓸기: You … Sam(1쪽) → Avery·Quinn·Drew(2쪽) → "Page 2 of 2" → Mic. 왼쪽 쓸기: Drew·Quinn·Avery → Sam … You(1쪽) → 상단 바 | Pass |
| 띠 왼쪽 쓸기(d8f9bd7) | 띠 처음까지 되돌아옴 | 오른쪽: You, Pinned → Alex … Sam → Mic(큰 화면이 끼지 않음). 왼쪽: Sam … Alex → You, Pinned → 상단 바 | Fail → Pass |
| 경고 상자(KAlert) | 한 번에 읽음 | "Warning, Test-only token source, 본문" 한 번 | Fail → Pass |
| 덜 찬 마지막 쪽 칸 크기 | 1쪽과 같은 크기 | 처음에는 2쪽의 3명이 2열로 커졌다(Kinetica가 쪽마다 인원으로 줄 수를 정함). Kinetica 77cbb1a부터 여러 쪽이면 꽉 찬 쪽 기준으로 짜서, 2쪽의 3명이 1쪽과 같은 크기 칸 한 줄로 화면 가운데에 놓인다 | Fail → Pass |

도중에 확인한 것: TalkBack을 꺼 둔 줄 알았는데 켜져 있었고, 그동안은 손가락 탭과 쓸기가 TalkBack 제스처로 바뀌어 동작하지 않는 것처럼 보였다. 시험 전에는 `settings get secure enabled_accessibility_services`로 꺼짐을 확인한다.

## 접은 바깥 화면과 LiveKit Meet 채팅 (에뮬레이터) — 2026-10-18

| 항목 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 통화 중 접고 펴기 | 통화가 이어지고 화면이 맞춰짐 | 전용 폴더블 AVD(voxera_fold, Pixel 9 Pro Fold, API 36.1)에서 4명 통화 중 `adb emu unfold`/`fold`를 했다. 안쪽 2076×2152에서는 2×2가 넓게, 바깥 1080×2424에서는 길게 바뀌고, 통화는 이어지며 앱이 죽지 않음 | Pass |
| 접은 바깥 화면, TalkBack 음성 | 같은 화면, 입력 칸 이름을 읽음 | 바깥 화면에서 채팅은 더보기가 아니라 조작 막대에 있다("2 unread messages, Chat"). 시트에서는 "Chat, Heading" → 메시지마다 한 번 → "Edit box, Message" → "Send, Button, disabled"(uhid 키보드 + VERBOSE 로그) | Pass (실제 Fold7은 NOT RUN) |
| LiveKit Meet와 채팅 | 서로 주고받음 | Meet가 쓰는 판과 같은 채팅 코드(@livekit/components-core 0.12.15 setupChat, livekit-client 2.22.4)를 headless Chrome에 띄워 로컬 서버에 넣었다. Meet 쪽이 보낸 글이 Voxera 알림 섬과 배지로 왔고, Voxera가 보낸 글을 Meet 쪽이 같은 글로 받았다. Meet 웹앱 화면 자체는 띄우지 않았다 | Pass |
| Kinetica a95752d(채운 면 흰 글자 대비) | 화면이 깨지지 않음 | Voxera 강조색(청록)은 이미 4.5:1을 넘어 버튼 색이 그대로다. 전체 검사 통과 | Pass |

## 참가자마다 다른 영상 비율 (에뮬레이터 API 36) — 2026-10-18

타일 자리마다 잘림 한도를 두고, 그보다 많이 잘리면 영상 전체를 보여 준다. 한도는 크게 보기·1:1·PiP 75%, 격자·아래 줄 50%이고, 내 작은 창은 늘 채우며, 화면 공유는 늘 전체다. 렌더러가 알려 주는 실제 프레임 크기(회전 반영)로 판단한다. 데모에서는 원격 참가자 셋 중 한 명(2·5·8번째)이 가로 노트북 영상을 보낸다.

| 항목 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 데모 4명 격자 | 세로 영상은 채우고, 가로 영상(Taylor)은 전체 | Taylor 칸만 위아래 어두운 띠와 함께 영상 전체, 나머지는 채움 | Pass |
| 가로 영상을 크게 보기 | 전체를 보여 줌 | Taylor를 고정하면 큰 화면에서 영상 전체가 보임 | Pass |
| 휴대전화 가로 화면 | 칸이 납작하지 않음 | 처음에는 4명이 2×2로 놓여 칸이 매우 납작했고 세로 영상이 가는 띠로 보였다. 낮은 창(높이 480dp 미만)에서는 4명까지 한 줄, 9명은 5+4 두 줄로 바꿨다. 1:1은 세로 상대 영상이 가운데에 전체로 보인다 | Fail → Pass |
| 실통화(로컬 LiveKit) 가로 640×360 참가자 | 전체 | Python SDK 참가자 영상의 빨강·노랑 모서리 표시가 모두 보임 | Pass |
| 실통화 세로 360×640 → 25초 뒤 가로로 돌림 | 처음엔 채우고, 돌리면 전체로 바뀜 | 세로일 때 칸을 채우다가(가장자리만 조금 잘림) 가로로 바뀌자 위아래 띠와 함께 전체로 바뀜 | Pass |
| 단위 테스트 | 판단 규칙과 크기 계산 | VideoFitTest 4개, 낮은 창 열 수 테스트 | Pass |

입장 전 마이크 레벨(다시 시도): 사용자 허락을 받아 Mac 음량 30%로 시험 문장을 틀었다. 에뮬레이터는 `-allow-host-audio`와 `hostmicon`으로 띄웠다. 그래도 막대는 0이었고 "Microphone is on. Say something to test it."가 그대로였다. 권한 창이 뜨지 않아, macOS가 에뮬레이터에 마이크를 열어 주지 않은 것으로 보인다. 음량은 원래대로(0, 음소거) 되돌렸다. NOT RUN, 실기기에서 확인할 것.

## 음성 그룹 20명 (에뮬레이터 API 36, 로컬 LiveKit 실통화) — 2026-10-18

Python LiveKit SDK로 트랙 없는 참가자 11명, 이어서 8명을 더 넣고 에뮬레이터가 음성 통화로 들어갔다(나 포함 20명).

| 항목 | 기대 | 실제 | Result |
| --- | --- | --- | --- |
| 12명 | 한 화면에 모두 | 3열 4줄로 다 보여 넘길 일이 없음 | Pass |
| 20명 세로 넘기기 | 마지막 줄까지 보고 조작 막대에 가리지 않음 | 위로 쓸면 마지막 줄(2명, 가운데 정렬)이 막대 위에 온전히 보이고, 다시 쓸면 맨 위 "You"로 돌아옴 | Pass |
| TalkBack으로 사람마다 넘기기 | 사람 하나에 한 번 멈춤 | 처음에는 사람마다 "Microphone off" 아이콘과 이름에서 두 번 멈췄다. 칸을 묶어 "Jordan, Microphone off"처럼 한 번에 읽게 고쳤다. 고친 뒤 19명을 차례로 한 번씩 읽고, 마지막 사람 다음은 조작 막대 "On, Mic, Button"으로 넘어가며, 화면도 초점을 따라 끝까지 내려감 | Fail → Pass |
| 단위 테스트 | 같은 동작 | 20명 테스트가 "Person 19, Microphone off" 한 노드(자식 없음)를 넘겨 찾음 | Pass |

Kinetica 402a375(입력란 이름을 안내 글로 내보냄): 전체 검사 통과. 채팅 입력란은 비었을 때 "Edit box. Message", 글을 쓴 뒤 "Editing. hello. Edit box. Message"로 읽음(Pass).

