# Security Policy

## 지원 버전

| 버전 | 지원 |
| --- | --- |
| 0.1.x (alpha) | 최신 alpha만 수정합니다 |

## 취약점 알리기

공개 이슈 대신 GitHub의 **Security → Report a vulnerability**(비공개 보고)로 알려 주세요. 재현 방법과 영향받는 버전, 모듈을 적어 주시고, 고칠 방법이 떠오르면 같이 적어 주시면 좋습니다. 문제가 확인되면 수정 버전을 낼 때 함께 알립니다.

보고 내용에 실제 API Secret, 액세스 토큰, 개인 LiveKit 주소는 넣지 마세요. 꼭 필요하면 값을 가리거나 만료된 토큰을 쓰세요.

## SDK가 지키는 것

- API Secret이나 Signing Key를 입력하거나 저장하는 기능이 없습니다. 토큰은 호스트가 발급하고, SDK는 메모리에만 들고 있습니다.
- 토큰, Authorization 헤더, JWT, SDP, 개인정보는 로그와 진단 리포트에 남기지 않습니다.
- 기본은 `wss://`와 `https://`만 받습니다. 로컬 개발 주소로 평문 연결을 하려면 호스트가 직접 켜야 합니다.
- 푸시 payload에는 토큰을 넣지 않는 것이 계약입니다. 반응 같은 데이터 메시지는 정해진 형식만 받습니다.
- 앱 안에서만 쓰는 broadcast receiver는 `RECEIVER_NOT_EXPORTED`로, PendingIntent는 `FLAG_IMMUTABLE`로 만듭니다.
