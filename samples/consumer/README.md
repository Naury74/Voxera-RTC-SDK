# sample-consumer

배포 전에 SDK를 "남이 쓰는 것처럼" 설치해 보는 빈 소비자 앱입니다. 이 저장소의 모듈을 직접 참조하지 않고
로컬 Maven(`~/.m2`)에 게시된 `com.naurylab:voxera-*`만 받고, Kinetica 같은 나머지 의존성은 바깥 앱처럼 Maven Central에서 받습니다.

- Kotlin 2.2.21로 빌드해 SDK의 Kotlin 2.2 소비자 호환을 확인합니다.
- Java 소스(`JavaHost.java`)에서 Java facade를 호출해 Java 호스트 사용성을 확인합니다.
- release는 R8로 줄여 SDK 소비자 규칙을 확인합니다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew publishToMavenLocal
./gradlew -p samples/consumer :app:assembleRelease
```

LiveKit Android는 audioswitch를 JitPack에서 받으므로 소비자 앱도 `settings.gradle.kts`처럼 그 그룹만 JitPack을 허용해야 합니다.
