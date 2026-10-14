package com.naury.voxerartc.core

import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.ServerUrlPolicy

/**
 * 입장할 때 켤 미디어. 사전 대기 화면에서 고른 값은 `join`의 `media`로 덮어쓸 수 있다.
 *
 * 영상 모드라도 [startWithCamera]가 `false`면 카메라를 켜지 않고 들어간다.
 */
public data class MediaPreferences(
    val startWithMicrophone: Boolean = true,
    val startWithCamera: Boolean = true,
)

/**
 * 연결·복구 시간 제한.
 *
 * @property connectTimeoutMs 자격 증명을 받은 뒤 서버 연결을 기다리는 최대 시간(ms, 1 이상).
 * @property reconnectDeadlineMs 연결이 끊긴 뒤 엔진 복구를 기다리는 최대 시간(ms, 1 이상). 넘기면 실패로 끝낸다.
 * @property credentialAttempts 일시적 오류(5xx, 시간 초과)에서 자격 증명을 요청하는 최대 횟수(1 이상). 인증 오류는 다시 시도하지 않는다.
 * @property credentialRetryDelayMs 첫 재시도 전 대기(ms). 재시도마다 두 배로 늘어난다.
 */
public data class SessionPolicy(
    val connectTimeoutMs: Long = 20_000,
    val reconnectDeadlineMs: Long = 30_000,
    val credentialAttempts: Int = 3,
    val credentialRetryDelayMs: Long = 500,
) {
    init {
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be positive" }
        require(reconnectDeadlineMs > 0) { "reconnectDeadlineMs must be positive" }
        require(credentialAttempts >= 1) { "credentialAttempts must be at least 1" }
        require(credentialRetryDelayMs >= 0) { "credentialRetryDelayMs must not be negative" }
    }
}

/**
 * 세션 설정.
 *
 * @property credentials 호스트가 구현하는 자격 증명 공급자. SDK는 받은 토큰을 저장하지 않는다.
 * @property serverUrlPolicy 공급자가 돌려준 서버 주소를 검사하는 규칙. 기본은 `wss://`만 허용한다.
 */
public data class VoxeraSessionConfig(
    val credentials: RtcCredentialsProvider,
    val media: MediaPreferences = MediaPreferences(),
    val policy: SessionPolicy = SessionPolicy(),
    val serverUrlPolicy: ServerUrlPolicy = ServerUrlPolicy(),
)
