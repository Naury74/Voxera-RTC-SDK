package com.naury.voxerartc.livekit

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.AudioRouteType
import com.naury.voxerartc.api.VoxeraError
import io.livekit.android.events.DisconnectReason
import io.livekit.android.room.RoomException
import org.junit.Test
import java.io.IOException
import java.net.ProtocolException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class LiveKitMappingTest {

    @Test
    fun `토큰 검증 실패 문구는 인증 만료로 분류한다`() {
        assertThat(classifyConnectFailure(RoomException.ConnectException("could not validate token: token is expired (exp)")))
            .isEqualTo(VoxeraError.AuthenticationExpired)
        assertThat(classifyConnectFailure(RoomException.ConnectException("invalid authorization token")))
            .isEqualTo(VoxeraError.AuthenticationExpired)
    }

    @Test
    fun `DNS·TLS·연결 거부는 서버에 닿지 않음`() {
        assertThat(classifyConnectFailure(RoomException.ConnectException("x", UnknownHostException("nope.example"))))
            .isEqualTo(VoxeraError.ServerUnreachable)
        assertThat(classifyConnectFailure(SSLHandshakeException("bad cert"))).isEqualTo(VoxeraError.ServerUnreachable)
        assertThat(classifyConnectFailure(IOException("wrapped", java.net.ConnectException("refused"))))
            .isEqualTo(VoxeraError.ServerUnreachable)
    }

    @Test
    fun `WebSocket 업그레이드가 안 되면 호환되지 않는 서버`() {
        assertThat(classifyConnectFailure(RoomException.ConnectException("Expected HTTP 101 response but was '200 OK'", ProtocolException())))
            .isEqualTo(VoxeraError.ServerIncompatible)
    }

    @Test
    fun `알 수 없는 실패는 문구 없이 내부 코드만 남긴다`() {
        val error = classifyConnectFailure(IllegalStateException("weird secret-looking text"))
        assertThat(error).isEqualTo(VoxeraError.InternalFailure("LIVEKIT_CONNECT"))
    }

    @Test
    fun `종료 사유`() {
        assertThat(mapDisconnect(DisconnectReason.CLIENT_INITIATED)).isEqualTo(DisconnectMapping.Ignore)
        assertThat(mapDisconnect(DisconnectReason.DUPLICATE_IDENTITY)).isEqualTo(DisconnectMapping.Notify(VoxeraError.DuplicateIdentity))
        assertThat(mapDisconnect(DisconnectReason.ROOM_DELETED)).isEqualTo(DisconnectMapping.Notify(null))
        assertThat(mapDisconnect(DisconnectReason.PARTICIPANT_REMOVED)).isEqualTo(DisconnectMapping.Notify(null))
        assertThat(mapDisconnect(DisconnectReason.SIGNAL_CLOSE)).isEqualTo(DisconnectMapping.Notify(VoxeraError.NetworkUnavailable))
        // 모든 사유가 분류되는지(새 사유가 생기면 컴파일 단계에서 when이 깨진다).
        DisconnectReason.entries.forEach { mapDisconnect(it) }
    }

    @Test
    fun `오디오 경로 id는 종류와 이름으로 정해진다`() {
        assertThat(routeIdOf(AudioRouteType.BLUETOOTH, "Galaxy Buds")).isEqualTo("bluetooth:Galaxy Buds")
        assertThat(routeIdOf(AudioRouteType.SPEAKER, "Speakerphone")).isNotEqualTo(routeIdOf(AudioRouteType.EARPIECE, "Speakerphone"))
    }
}
