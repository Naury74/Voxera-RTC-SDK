# 권한과 Manifest 계약

`voxera-livekit`을 추가하면 아래 항목이 호스트 앱 manifest에 합쳐집니다(LiveKit Android 2.29.0, audioswitch 기준).

| 항목 | 출처 | 런타임 요청 | 용도 |
| --- | --- | --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | LiveKit | 아니요 | 서버 연결 |
| `RECORD_AUDIO` | LiveKit | 예, 마이크를 켜기 직전 | 음성 게시 |
| `CAMERA` | LiveKit | 예, 카메라를 켜기 직전 | 영상 게시 |
| `MODIFY_AUDIO_SETTINGS` | audioswitch | 아니요 | 통화 오디오 모드·경로 |
| `BLUETOOTH` (maxSdk 30) | audioswitch | 아니요 | Android 11 이하 블루투스 경로 |
| `BLUETOOTH_CONNECT` | voxera-livekit | 예(Android 12+) | 블루투스 이어폰 경로 찾기·선택 |

제거하는 항목: LiveKit이 선언하는 `ScreenCaptureService`(mediaProjection foreground service), `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PROJECTION`. 화면 공유는 아직 제공하지 않으므로 쓰지 않는 서비스를 선언하지 않습니다.

`voxera-core`·`voxera-rtc-api`·`voxera-testkit`·`voxera-ui-compose`는 권한을 선언하지 않습니다. 오프라인 데모만 쓰는 앱은 권한이 필요 없습니다.

## 런타임 권한

SDK는 권한 대화상자를 직접 띄우지 않습니다. 호스트가 사용자 동작(입장 버튼 등) 직후에 요청하고, 거부되면 다음처럼 동작합니다.

- 마이크 거부: 마이크 없이 입장하고, 마이크를 켜면 `VoxeraError.PermissionDenied(MICROPHONE)`.
- 카메라 거부: 카메라 없이(음성으로) 입장하고, 카메라를 켜면 `VoxeraError.PermissionDenied(CAMERA)`.
- 카메라가 없는 기기: 카메라 켜기가 `MediaActionResult.Unsupported`.

## 로컬 개발 서버(`ws://`)

Android 9 이상은 평문 통신을 기본으로 막습니다. `ServerUrlPolicy(allowInsecureLocalhost = true)`로 `ws://10.0.2.2:7880` 같은
로컬 개발 서버를 허용해도, 호스트 앱의 network security config가 그 주소의 cleartext를 허용하지 않으면 연결은
`InvalidConfiguration(SERVER_URL_INSECURE)`로 실패합니다. SDK는 네트워크 보안 정책이나 TLS 검증을 우회하지 않습니다.
쇼케이스는 debug 빌드(`app/src/debug`)에서만 로컬 주소의 cleartext를 허용합니다.
