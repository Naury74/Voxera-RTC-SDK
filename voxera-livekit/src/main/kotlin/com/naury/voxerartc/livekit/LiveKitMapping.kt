package com.naury.voxerartc.livekit

import com.naury.voxerartc.api.AudioRoute
import com.naury.voxerartc.api.AudioRouteType
import com.naury.voxerartc.api.ConnectionQuality
import com.naury.voxerartc.api.VoxeraError
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.events.DisconnectReason
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import io.livekit.android.room.participant.ConnectionQuality as LiveKitQuality

/**
 * LiveKit 연결 실패를 [VoxeraError]로 분류한다. 서버가 돌려준 문구는 분류에만 쓰고 밖으로 내보내지 않는다.
 *
 * LiveKit은 WebSocket 연결이 실패하면 `/rtc/validate`의 응답 본문을 예외 메시지에 담는다. 토큰 문제면 "token"·"expired"·
 * "unauthorized" 같은 낱말이 들어 있으므로 인증 오류로 보고, 세션이 새 토큰으로 한 번 다시 시도하게 한다.
 */
internal fun classifyConnectFailure(error: Throwable): VoxeraError {
    val chain = generateSequence(error) { it.cause }.take(8).toList()
    val text = chain.mapNotNull { it.message?.lowercase() }.joinToString(" ")
    val authWords = listOf("expired", "unauthorized", "invalid token", "invalid authorization", "token is", "401", "403", "permission denied")
    return when {
        authWords.any { it in text } -> VoxeraError.AuthenticationExpired
        chain.any { it is UnknownHostException || it is SocketTimeoutException || it is NoRouteToHostException || it is SSLException || it is java.net.ConnectException } ->
            VoxeraError.ServerUnreachable
        chain.any { it is ProtocolException } || "expected http 101" in text || "404" in text -> VoxeraError.ServerIncompatible
        else -> VoxeraError.InternalFailure("LIVEKIT_CONNECT")
    }
}

/** 엔진 종료 이벤트를 세션에 알릴지와 그 오류. */
internal sealed interface DisconnectMapping {
    /** 우리가 끊은 것이라 알리지 않는다. */
    data object Ignore : DisconnectMapping

    /** [error]가 `null`이면 상대·서버가 정상으로 끝낸 것이다. */
    data class Notify(val error: VoxeraError?) : DisconnectMapping
}

internal fun mapDisconnect(reason: DisconnectReason): DisconnectMapping = when (reason) {
    DisconnectReason.CLIENT_INITIATED -> DisconnectMapping.Ignore
    DisconnectReason.DUPLICATE_IDENTITY -> DisconnectMapping.Notify(VoxeraError.DuplicateIdentity)
    DisconnectReason.PARTICIPANT_REMOVED, DisconnectReason.ROOM_DELETED, DisconnectReason.ROOM_CLOSED,
    DisconnectReason.USER_REJECTED, DisconnectReason.USER_UNAVAILABLE -> DisconnectMapping.Notify(null)
    DisconnectReason.SERVER_SHUTDOWN -> DisconnectMapping.Notify(VoxeraError.ServerUnreachable)
    DisconnectReason.MEDIA_FAILURE -> DisconnectMapping.Notify(VoxeraError.InternalFailure("LIVEKIT_MEDIA_FAILURE"))
    DisconnectReason.UNKNOWN_REASON, DisconnectReason.STATE_MISMATCH, DisconnectReason.JOIN_FAILURE, DisconnectReason.MIGRATION,
    DisconnectReason.SIGNAL_CLOSE, DisconnectReason.SIP_TRUNK_FAILURE, DisconnectReason.CONNECTION_TIMEOUT ->
        DisconnectMapping.Notify(VoxeraError.NetworkUnavailable)
}

internal fun LiveKitQuality.toVoxera(): ConnectionQuality = when (this) {
    LiveKitQuality.EXCELLENT -> ConnectionQuality.EXCELLENT
    LiveKitQuality.GOOD -> ConnectionQuality.GOOD
    LiveKitQuality.POOR -> ConnectionQuality.POOR
    LiveKitQuality.LOST -> ConnectionQuality.LOST
    LiveKitQuality.UNKNOWN -> ConnectionQuality.UNKNOWN
}

/** 오디오 경로의 고정 id. 같은 종류·이름이면 장치가 끊겼다 다시 연결되어도 같은 id다. */
internal fun routeIdOf(type: AudioRouteType, name: String): String = "${type.name.lowercase()}:$name"

internal fun AudioDevice.routeId(): String = routeIdOf(routeType(), name)

internal fun AudioDevice.routeType(): AudioRouteType = when (this) {
    is AudioDevice.Earpiece -> AudioRouteType.EARPIECE
    is AudioDevice.Speakerphone -> AudioRouteType.SPEAKER
    is AudioDevice.WiredHeadset -> AudioRouteType.WIRED_HEADSET
    is AudioDevice.BluetoothHeadset -> AudioRouteType.BLUETOOTH
}

internal fun AudioDevice.toRoute(): AudioRoute = AudioRoute(id = routeId(), label = name, type = routeType())
