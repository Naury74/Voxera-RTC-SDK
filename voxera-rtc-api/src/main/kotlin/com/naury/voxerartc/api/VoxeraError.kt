package com.naury.voxerartc.api

/** 설정·입력값 문제. 사용자가 고칠 수 있는 항목을 가리킨다. */
public enum class ConfigurationProblem {
    SERVER_URL_BLANK,
    SERVER_URL_MALFORMED,

    /** `wss`/`ws`가 아닌 scheme(예: `https`). */
    SERVER_URL_SCHEME,

    /** 허용되지 않은 평문 `ws://`. */
    SERVER_URL_INSECURE,

    /** [ServerUrlPolicy.allowedHosts]에 없는 호스트. */
    SERVER_URL_NOT_ALLOWED,
    ROOM_NAME_BLANK,
    ROOM_NAME_INVALID,
    DISPLAY_NAME_BLANK,
    DISPLAY_NAME_INVALID,
    TOKEN_BLANK,

    /** JWT 모양이 아님. API Secret을 붙여 넣었을 가능성이 크다. */
    TOKEN_NOT_JWT,
    TOKEN_EXPIRED,

    /** 토큰 서버가 요청을 거절함. 토큰 서버 주소·ID를 확인해야 한다. */
    TOKEN_SOURCE_REJECTED,
}

/** 권한이 필요한 미디어 기능. */
public enum class MediaPermission { MICROPHONE, CAMERA }

/** 사용할 수 없는 장치 종류. */
public enum class MediaDevice { MICROPHONE, CAMERA, AUDIO_ROUTE }

/**
 * Voxera가 돌려주는 오류 분류. 서버의 원시 오류 문자열과 토큰은 담지 않는다.
 *
 * [code]는 문서·진단에서 쓰는 고정된 기술 코드이며 버전이 바뀌어도 유지한다.
 */
public sealed class VoxeraError(public val code: String) {
    /** 사용자가 고칠 수 있는 입력값 문제. */
    public data class InvalidConfiguration(val problem: ConfigurationProblem) : VoxeraError("VOX-CONFIG")

    /** 런타임 권한이 거부됨. 영구 거부 여부는 Android 쪽에서 판단한다. */
    public data class PermissionDenied(val permission: MediaPermission) : VoxeraError("VOX-PERMISSION")

    /** 서버가 토큰을 만료·거부함. 새 토큰을 한 번 요청한 뒤에도 실패한 경우다. */
    public data object AuthenticationExpired : VoxeraError("VOX-AUTH-EXPIRED")

    /** 호스트 자격 증명 공급자가 로그인·권한 문제로 실패함. 호스트 인증 갱신이 필요하다. */
    public data object HostUnauthorized : VoxeraError("VOX-HOST-UNAUTHORIZED")

    /** 서버에 닿지 않음(DNS, TLS, 방화벽, 시간 초과). */
    public data object ServerUnreachable : VoxeraError("VOX-SERVER-UNREACHABLE")

    /** 접속은 되었지만 LiveKit 호환 서버가 아님. */
    public data object ServerIncompatible : VoxeraError("VOX-SERVER-INCOMPATIBLE")

    /** 기기에 네트워크가 없음, 또는 재연결 제한 시간을 넘김. */
    public data object NetworkUnavailable : VoxeraError("VOX-NETWORK")

    /** 장치가 없거나 다른 앱이 사용 중. */
    public data class MediaDeviceUnavailable(val device: MediaDevice) : VoxeraError("VOX-DEVICE")

    /** 같은 SDK에서 이미 다른 통화가 진행 중. */
    public data object ConcurrentSession : VoxeraError("VOX-CONCURRENT")

    /** 연결되지 않은 상태에서 미디어를 조작함. */
    public data object NotConnected : VoxeraError("VOX-NOT-CONNECTED")

    /** 사용자가 연결 중에 나감. */
    public data object UserCancelled : VoxeraError("VOX-CANCELLED")

    /** 서버나 다른 참가자가 통화를 끝냄(강퇴·룸 삭제 포함). */
    public data object RemoteEnded : VoxeraError("VOX-REMOTE-ENDED")

    /** 같은 identity가 다른 곳에서 입장해 이 연결이 밀려남. */
    public data object DuplicateIdentity : VoxeraError("VOX-DUPLICATE-IDENTITY")

    /** 분류할 수 없는 내부 실패. [technicalCode]는 비밀값을 제거한 진단용 코드다. */
    public data class InternalFailure(val technicalCode: String) : VoxeraError("VOX-INTERNAL")
}
