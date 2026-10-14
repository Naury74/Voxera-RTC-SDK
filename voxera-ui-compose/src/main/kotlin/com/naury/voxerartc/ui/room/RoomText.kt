package com.naury.voxerartc.ui.room

import androidx.annotation.StringRes
import com.naury.voxerartc.api.AudioRouteType
import com.naury.voxerartc.api.ConfigurationProblem
import com.naury.voxerartc.api.EndReason
import com.naury.voxerartc.api.MediaPermission
import com.naury.voxerartc.api.VoxeraError
import com.naury.voxerartc.ui.R
import java.util.Locale

/** 사용자가 할 수 있는 다음 행동을 담은 오류 문구. 서버 원문이나 토큰은 쓰지 않는다. */
@StringRes
internal fun VoxeraError.messageRes(): Int = when (this) {
    is VoxeraError.InvalidConfiguration -> when (problem) {
        ConfigurationProblem.SERVER_URL_INSECURE -> R.string.voxera_error_config_insecure
        ConfigurationProblem.TOKEN_SOURCE_REJECTED -> R.string.voxera_error_config_token_source
        else -> R.string.voxera_error_config
    }
    is VoxeraError.PermissionDenied -> when (permission) {
        MediaPermission.MICROPHONE -> R.string.voxera_error_permission_mic
        MediaPermission.CAMERA -> R.string.voxera_error_permission_camera
    }
    VoxeraError.AuthenticationExpired -> R.string.voxera_error_auth_expired
    VoxeraError.HostUnauthorized -> R.string.voxera_error_host_unauthorized
    VoxeraError.ServerUnreachable -> R.string.voxera_error_unreachable
    VoxeraError.ServerIncompatible -> R.string.voxera_error_incompatible
    VoxeraError.NetworkUnavailable -> R.string.voxera_error_network
    is VoxeraError.MediaDeviceUnavailable -> R.string.voxera_error_device
    VoxeraError.ConcurrentSession -> R.string.voxera_error_concurrent
    VoxeraError.NotConnected -> R.string.voxera_error_not_connected
    VoxeraError.UserCancelled -> R.string.voxera_error_cancelled
    VoxeraError.RemoteEnded -> R.string.voxera_error_remote_ended
    VoxeraError.DuplicateIdentity -> R.string.voxera_error_duplicate_identity
    is VoxeraError.InternalFailure -> R.string.voxera_error_internal
}

/** 다시 시도해도 같은 결과일 오류. 이 경우 재시도 버튼 대신 호스트가 처리하도록 닫기만 보여 준다. */
internal val VoxeraError.isRetryable: Boolean
    get() = when (this) {
        VoxeraError.ServerUnreachable, VoxeraError.NetworkUnavailable, VoxeraError.AuthenticationExpired,
        is VoxeraError.MediaDeviceUnavailable, VoxeraError.RemoteEnded, VoxeraError.UserCancelled -> true
        else -> false
    }

@StringRes
internal fun EndReason.messageRes(): Int = when (this) {
    EndReason.USER_LEFT -> R.string.voxera_end_reason_user
    EndReason.HOST_LEFT -> R.string.voxera_end_reason_host
    EndReason.CANCELLED -> R.string.voxera_end_reason_cancelled
    EndReason.REMOTE_ENDED -> R.string.voxera_end_reason_remote
    EndReason.AFTER_FAILURE -> R.string.voxera_end_reason_failure
}

@StringRes
internal fun AudioRouteType.labelRes(): Int = when (this) {
    AudioRouteType.EARPIECE -> R.string.voxera_route_earpiece
    AudioRouteType.SPEAKER -> R.string.voxera_route_speaker
    AudioRouteType.WIRED_HEADSET -> R.string.voxera_route_wired
    AudioRouteType.BLUETOOTH -> R.string.voxera_route_bluetooth
    AudioRouteType.USB -> R.string.voxera_route_usb
    AudioRouteType.UNKNOWN -> R.string.voxera_route_unknown
}

/** 통화 시간 `m:ss` 또는 `h:mm:ss`. 음수는 0으로 본다. */
internal fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** 비트레이트를 kbps/Mbps로 쓴다. */
internal fun formatBitrate(bitsPerSecond: Double): String = when {
    bitsPerSecond >= 1_000_000 -> String.format(Locale.ROOT, "%.1f Mbps", bitsPerSecond / 1_000_000)
    else -> String.format(Locale.ROOT, "%.0f kbps", bitsPerSecond / 1_000)
}
