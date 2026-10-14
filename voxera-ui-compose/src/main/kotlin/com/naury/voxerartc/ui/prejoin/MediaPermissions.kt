package com.naury.voxerartc.ui.prejoin

/** 런타임 권한 상태. */
public enum class PermissionStatus {
    GRANTED,

    /** 아직 묻지 않았거나 다시 물어볼 수 있음. */
    REQUESTABLE,

    /** 거부했지만 이유를 보여 주고 다시 물을 수 있음. */
    DENIED,

    /** "다시 묻지 않음" 또는 정책으로 막힘. 시스템 대화상자가 뜨지 않으므로 설정 화면으로만 안내한다. */
    BLOCKED,
}

/**
 * Android는 권한이 영구 거부되었는지 직접 알려 주지 않는다. 한 번 요청한 뒤 거부 상태이고 이유 표시(rationale)도 하지 않으면
 * 시스템 대화상자가 더 뜨지 않는 상태로 본다. 요청 전에는 rationale이 false여도 요청할 수 있다.
 */
internal fun resolvePermission(granted: Boolean, requestedBefore: Boolean, shouldShowRationale: Boolean): PermissionStatus = when {
    granted -> PermissionStatus.GRANTED
    !requestedBefore -> PermissionStatus.REQUESTABLE
    shouldShowRationale -> PermissionStatus.DENIED
    else -> PermissionStatus.BLOCKED
}
