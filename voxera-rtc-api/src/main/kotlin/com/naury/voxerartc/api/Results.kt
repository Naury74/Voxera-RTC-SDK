package com.naury.voxerartc.api

/** `join` 결과. */
public sealed interface JoinResult {
    /** 미디어 연결 완료. [sessionId]는 이번 연결(generation)을 구분하는 SDK 내부 식별자다. */
    public data class Connected(val sessionId: String) : JoinResult

    /** 입장하지 못함. */
    public data class Rejected(val error: VoxeraError) : JoinResult
}

/** 마이크·카메라·장치 조작 결과. 엔진이 실제로 반영(ack)한 뒤에 돌아온다. */
public sealed interface MediaActionResult {
    public data object Applied : MediaActionResult

    /** 실패. 현재 미디어 상태는 바뀌지 않는다. */
    public data class Failed(val error: VoxeraError) : MediaActionResult

    /** 이 엔진·기기·상태에서 지원하지 않음(예: 전면 카메라만 있는 기기에서 전환). */
    public data object Unsupported : MediaActionResult
}

/** 사용자가 아닌 쪽에서 나가는 이유도 기록할 수 있도록 나누어 둔다. */
public enum class LeaveReason {
    /** 사용자가 종료 버튼을 누름. */
    USER,

    /** 호스트 앱 정책(로그아웃, 다른 통화 등)으로 종료. */
    HOST,
}
