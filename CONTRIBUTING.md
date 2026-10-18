# Contributing

## 빌드

Gradle은 JDK 25에서 돕니다(`gradle/gradle-daemon-jvm.properties`). 컴퓨터에 없으면 처음 빌드할 때 Gradle이 내려받습니다. Gradle을 시작하는 JDK는 Android Studio에 들어 있는 JBR을 그대로 써도 됩니다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test lint apiCheck :app:assembleDebug :app:assembleRelease
```

PR을 올리기 전에 이 명령이 통과해야 합니다. CI(`.github/workflows/ci.yml`)도 같은 명령을 돌립니다. 배포한 artifact만 받는 바깥 앱이 release로 빌드되는지는 다음으로 확인합니다.

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew publishToMavenLocal && ./gradlew -p samples/consumer :app:assembleRelease
```

- Kotlin JVM 모듈(rtc-api, core, invitation, testkit)의 공개 API는 `<모듈>/api/<모듈>.api`에 고정되어 있습니다. 의도해서 바꾼 것이라면 `./gradlew apiDump`로 파일을 갱신해 함께 커밋하고 [CHANGELOG](CHANGELOG.md)에도 적습니다.
- SDK 모듈은 explicit API mode로 빌드합니다. 공개 선언에는 `public`과 반환 타입을 적습니다.
- API 문서는 `./gradlew :dokkaGenerate`로 `build/dokka/html`에 만들어집니다.

## 작업 흐름

1. `main`에서 작업마다 브랜치를 만듭니다. 예: `feat/chat-retry`, `fix/pip-hangup`
2. 커밋 하나에는 변경 의도 하나만 담습니다. [Conventional Commits](https://www.conventionalcommits.org/) 형식을 따르고, 제목은 한국어로, scope는 모듈 이름으로 씁니다.

```text
feat(ui): 음성 그룹 통화에서 사람이 많으면 위아래로 넘김
fix(livekit): 재연결 뒤 화면 공유 트랙이 사라지던 문제
test(core): 토큰 재요청이 한 번만 일어나는지 확인
build: LiveKit Android 2.29.1로 올림
```

| scope | 모듈 |
| --- | --- |
| `rtc-api`, `core`, `invitation`, `testkit` | Kotlin JVM 모듈 |
| `livekit`, `ui`, `android` | Android 모듈(`voxera-livekit`과 `voxera-livekit-compose`는 `livekit`) |
| `app` | 쇼케이스 앱 |
| `build`, `ci`, `docs` | 빌드 설정, CI, 문서 |

## 규칙

- `voxera-rtc-api`와 `voxera-core`에는 Android, Compose, LiveKit 타입을 넣지 않습니다. 상태 전이는 순수 JVM 테스트로 확인합니다. 모듈 관계는 [docs/architecture.md](docs/architecture.md)에 있습니다.
- 공개 API에 LiveKit `Room`, `Track` 같은 엔진 객체나 Kinetica 타입을 내보내지 않습니다.
- 상태는 `SessionReducer`를 거쳐서만 바꿉니다. 부수 효과는 전이가 받아들여진 뒤에만 실행합니다.
- 리소스 이름은 `voxera_`로 시작합니다. 화면 문구는 영어(`values`)와 한국어(`values-ko`)를 함께 넣습니다.
- 주석과 KDoc은 한국어로 씁니다. KDoc은 공개 API 계약에 달고 단위, 범위, 실패 조건을 적습니다. 그 밖의 주석에는 코드만 보고는 알 수 없는 이유만 적습니다.
- 토큰, Authorization 헤더, JWT, SDP, 참가자 이름, 채팅 내용은 로그와 오류 메시지, 진단 리포트에 넣지 않습니다.
- 네트워크에서 받은 값(채팅, 반응, 손들기, 푸시 payload)은 믿지 않습니다. 정해진 형식과 길이만 받고, 새 필드를 읽을 때는 적대적 입력 테스트를 더합니다.
- 제거 조건이 없는 `TODO`, `FIXME`는 남기지 않습니다. 눌러도 아무 일이 없는 버튼이나 성공한 척하는 stub도 두지 않습니다.
- 의존성 버전은 `gradle/libs.versions.toml`에서만 관리하고, 버전을 올리는 커밋은 기능 변경 커밋과 나눕니다.
- 기기에서 직접 확인하지 않은 동작을 확인했다고 적지 않습니다. 확인한 결과는 [docs/verification.md](docs/verification.md)에 남깁니다.

## 저장소에 넣지 않는 것

빌드 산출물, APK와 AAB, keystore와 서명 정보, LiveKit API Key와 Secret, 토큰, `local.properties` 같은 로컬 설정, 테스트와 lint 리포트, IDE 설정은 커밋하지 않습니다.
