package com.naury.voxerartc.api

import kotlinx.coroutines.flow.Flow

/** 엔진이 실제 통화인지 데모 시뮬레이션인지. UI는 [SIMULATED]일 때 항상 DEMO를 표시한다. */
public enum class RtcEngineKind { LIVE, SIMULATED }

/**
 * 화면 공유 승인 결과를 가리키는 불투명 값. Android `MediaProjection` 결과 Intent 같은 플랫폼 값은 provider 모듈의 구현이 담는다.
 * 한 번 승인한 값은 한 번의 공유에만 쓴다(Android 14+는 공유할 때마다 새 승인이 필요하다).
 */
public interface ScreenShareConsent

/** 연결 옵션. */
public data class ConnectOptions(
    val roomId: String,
    val mode: CallMode,
    val startWithMicrophone: Boolean,
    val startWithCamera: Boolean,
)

/** [RtcEngine.connect] 결과. */
public sealed interface ConnectOutcome {
    /** 연결됨. [local]은 로컬 참가자 첫 상태다. */
    public data class Connected(val local: Participant) : ConnectOutcome

    public data class Failed(val error: VoxeraError) : ConnectOutcome
}

/** 엔진이 연결된 뒤 보내는 이벤트. 순서는 보장하지만 중복이나 늦은 도착이 있을 수 있다. */
public sealed interface RtcEngineEvent {
    /** 참가자가 들어오거나 상태가 바뀜. 같은 [Participant.id]면 덮어쓴다. */
    public data class ParticipantUpserted(val participant: Participant) : RtcEngineEvent

    public data class ParticipantLeft(val participantId: String) : RtcEngineEvent

    /** 로컬 미디어가 엔진 쪽에서 바뀜(시스템이 카메라를 끊은 경우 등). */
    public data class LocalMediaChanged(val state: LocalMediaState) : RtcEngineEvent

    /** 연결이 끊겨 엔진이 스스로 복구를 시작함. SDK는 따로 재연결 루프를 돌리지 않는다. */
    public data object Reconnecting : RtcEngineEvent

    public data object Reconnected : RtcEngineEvent

    /** 엔진 연결이 끝남. [error]가 `null`이면 서버·상대가 정상 종료한 것이다. */
    public data class Disconnected(val error: VoxeraError?) : RtcEngineEvent

    public data class QualitySampled(val snapshot: QualitySnapshot) : RtcEngineEvent
}

/**
 * RTC provider 포트. Voxera 세션이 이 인터페이스만 사용하므로 LiveKit 타입은 공개 API에 드러나지 않는다.
 *
 * 하나의 인스턴스는 연결 한 번에만 쓰인다. 세션은 [release] 뒤 같은 인스턴스를 다시 쓰지 않는다.
 * 모든 suspend 함수는 main-safe여야 하고, 취소되면 진행 중인 연결을 정리해야 한다.
 */
public interface RtcEngine {
    public val kind: RtcEngineKind

    /** [connect]가 끝나기 전부터 구독된다. [release] 뒤에는 완료되거나 더 이상 값을 보내지 않아야 한다. */
    public val events: Flow<RtcEngineEvent>

    public suspend fun connect(credentials: RtcCredentials, options: ConnectOptions): ConnectOutcome

    public suspend fun setMicrophoneEnabled(enabled: Boolean): MediaActionResult

    public suspend fun setCameraEnabled(enabled: Boolean): MediaActionResult

    public suspend fun switchCamera(): MediaActionResult

    public suspend fun selectAudioRoute(routeId: String): MediaActionResult

    /**
     * 화면 공유를 시작한다. [consent]는 사용자가 시스템 화면 공유 승인 대화상자에서 허락한 결과다(provider 모듈이 정의).
     * 시스템이 공유를 멈추면(사용자가 알림에서 중지, 화면 잠금 등) [RtcEngineEvent.LocalMediaChanged]로 알린다.
     */
    public suspend fun startScreenShare(consent: ScreenShareConsent): MediaActionResult = MediaActionResult.Unsupported

    public suspend fun stopScreenShare(): MediaActionResult = MediaActionResult.Unsupported

    /** 연결·트랙·리스너를 모두 해제한다. 여러 번 불러도 안전해야 한다. */
    public suspend fun release()
}

/** 연결마다 새 [RtcEngine]을 만든다. [kind]는 만들어지는 엔진의 [RtcEngine.kind]와 같아야 한다. */
public interface RtcEngineFactory {
    public val kind: RtcEngineKind

    public fun create(): RtcEngine
}
