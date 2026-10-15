package com.naury.voxerartc.api

import kotlinx.coroutines.flow.Flow

/** 엔진이 실제 통화인지 데모 시뮬레이션인지. UI는 [SIMULATED]일 때 항상 DEMO를 표시한다. */
public enum class RtcEngineKind { LIVE, SIMULATED }

/**
 * 화면 공유 승인 결과를 가리키는 불투명 값. Android `MediaProjection` 결과 Intent 같은 플랫폼 값은 provider 모듈의 구현체가 담는다.
 * 승인 값 하나는 공유 한 번에만 쓴다(Android 14+는 공유할 때마다 새 승인이 필요하다).
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

/** 엔진이 연결된 뒤 보내는 이벤트. 순서는 보장하지만 중복되거나 늦게 도착할 수 있다. */
public sealed interface RtcEngineEvent {
    /** 참가자가 들어왔거나 상태가 바뀌었다. 같은 [Participant.id]면 덮어쓴다. */
    public data class ParticipantUpserted(val participant: Participant) : RtcEngineEvent

    public data class ParticipantLeft(val participantId: String) : RtcEngineEvent

    /** 시스템이 카메라를 끊는 등 엔진 쪽에서 로컬 미디어가 바뀌었다. */
    public data class LocalMediaChanged(val state: LocalMediaState) : RtcEngineEvent

    /** 연결이 끊겨 엔진이 스스로 복구를 시작했다. SDK는 별도의 재연결 루프를 돌리지 않는다. */
    public data object Reconnecting : RtcEngineEvent

    public data object Reconnected : RtcEngineEvent

    /** 엔진 연결이 끝났다. [error]가 `null`이면 서버나 상대가 정상적으로 끝낸 것이다. */
    public data class Disconnected(val error: VoxeraError?) : RtcEngineEvent

    public data class QualitySampled(val snapshot: QualitySnapshot) : RtcEngineEvent

    /** 원격 참가자가 반응을 보냈다. 엔진은 모르는 반응을 이 이벤트로 바꾸지 않는다. */
    public data class ReactionReceived(val participantId: String, val reaction: Reaction) : RtcEngineEvent

    /** 원격 참가자가 채팅을 보냈다. [text]는 다듬지 않은 값이며 세션이 [ChatMessage.sanitize]로 거른다. */
    public data class ChatReceived(val participantId: String, val messageId: String, val text: String) : RtcEngineEvent

    /** 원격 참가자가 손을 들거나 내렸다. 늦게 들어온 참가자에게도 엔진이 현재 상태를 다시 알려야 한다. */
    public data class HandRaisedChanged(val participantId: String, val raised: Boolean) : RtcEngineEvent
}

/**
 * RTC provider 포트. Voxera 세션은 이 인터페이스만 쓰므로 LiveKit 타입이 공개 API에 드러나지 않는다.
 *
 * 인스턴스 하나는 연결 한 번에만 쓴다. 세션은 [release] 뒤 같은 인스턴스를 다시 쓰지 않는다.
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
     * 화면 공유를 시작한다. [consent]는 사용자가 시스템 화면 공유 승인 대화상자에서 허락한 결과이며, 구체 타입은 provider 모듈이 정의한다.
     * 사용자가 알림에서 멈추거나 화면이 잠기는 등 시스템이 공유를 멈추면 [RtcEngineEvent.LocalMediaChanged]로 알린다.
     */
    public suspend fun startScreenShare(consent: ScreenShareConsent): MediaActionResult = MediaActionResult.Unsupported

    public suspend fun stopScreenShare(): MediaActionResult = MediaActionResult.Unsupported

    /**
     * 카메라 색 효과를 바꾼다. 효과 처리에 실패하거나 너무 느리면 엔진이 원본으로 되돌리고
     * [LocalMediaState.effectsAvailability]로 알린다. 통화를 끊지 않는다.
     */
    public suspend fun setVideoEffect(effect: VideoEffect): MediaActionResult = MediaActionResult.Unsupported

    /** 룸의 다른 참가자에게 반응을 보낸다. 지원하지 않는 엔진은 Unsupported. */
    public suspend fun sendReaction(reaction: Reaction): MediaActionResult = MediaActionResult.Unsupported

    /** 룸의 다른 참가자에게 채팅을 보낸다. [text]는 세션이 이미 다듬은 값이다. 지원하지 않는 엔진은 Unsupported. */
    public suspend fun sendChatMessage(messageId: String, text: String): MediaActionResult = MediaActionResult.Unsupported

    /**
     * 손들기 상태를 알린다. 나중에 들어온 참가자도 알 수 있게, 손을 든 동안 새 참가자가 오면 엔진이 다시 알려야 한다.
     * 지원하지 않는 엔진은 Unsupported.
     */
    public suspend fun setHandRaised(raised: Boolean): MediaActionResult = MediaActionResult.Unsupported

    /** 연결, 트랙, 리스너를 모두 해제한다. 여러 번 불러도 안전해야 한다. */
    public suspend fun release()
}

/** 연결마다 새 [RtcEngine]을 만든다. [kind]는 만들어지는 엔진의 [RtcEngine.kind]와 같아야 한다. */
public interface RtcEngineFactory {
    public val kind: RtcEngineKind

    public fun create(): RtcEngine
}
