# Security Policy

## 지원 버전

| 버전 | 지원 |
| --- | --- |
| 0.1.x (alpha) | 최신 alpha만 수정합니다 |

## 취약점 알리기

공개 이슈에 올리지 말고 GitHub의 **Security → Report a vulnerability**(비공개 보고)로 알려 주세요. 재현 방법, 영향 받는 버전·모듈, 가능하면 수정 제안을 함께 적어 주세요. 확인되면 수정 버전과 함께 공지합니다.

보고에 실제 API Secret, 액세스 토큰, 개인 LiveKit 주소를 넣지 마세요. 필요하면 가린 값이나 만료된 토큰을 쓰세요.

## SDK가 지키는 것

- API Secret·Signing Key 입력란이나 저장 기능이 없습니다. 토큰은 호스트가 발급하고 SDK는 메모리에만 둡니다.
- 토큰·Authorization·JWT·SDP·개인정보를 로그와 진단 리포트에 남기지 않습니다.
- 기본으로 `wss://`·`https://`만 허용하고, 로컬 개발 주소의 평문 연결은 호스트가 명시적으로 켤 때만 씁니다.
- 푸시 payload에 토큰을 넣지 않는 계약이며, 데이터 메시지(반응 등)는 정해진 형식만 받아들입니다.
- 앱 안에서만 쓰는 방송 수신기는 `RECEIVER_NOT_EXPORTED`, PendingIntent는 `FLAG_IMMUTABLE`입니다.
