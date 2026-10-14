package com.naury.voxerartc.livekit

import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.VideoSource
import com.naury.voxerartc.api.VideoTrackHandle
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.track.Track
import io.livekit.android.room.participant.Participant as LiveKitParticipant

/** 참가자 id. identity가 정해지기 전(입장 직후 로컬)에는 sid를 쓴다. */
internal val LiveKitParticipant.voxeraId: String
    get() = identity?.value ?: sid.value

/**
 * LiveKit 참가자의 현재 상태를 [Participant]로 옮긴다. 영상 핸들은 트랙이 있고 음소거가 아닐 때만 만든다
 * (카메라를 끄면 마지막 프레임 대신 아바타가 보이도록).
 */
internal fun LiveKitParticipant.snapshot(): Participant {
    val id = voxeraId
    fun handle(source: Track.Source, videoSource: VideoSource): VideoTrackHandle? {
        val publication = getTrackPublication(source) ?: return null
        if (publication.muted || publication.track == null) return null
        return VideoTrackHandle(id, publication.sid, videoSource)
    }
    return Participant(
        id = id,
        displayName = name?.takeIf { it.isNotBlank() } ?: identity?.value ?: "",
        isLocal = this is LocalParticipant,
        isMicrophoneEnabled = isMicrophoneEnabled,
        isSpeaking = isSpeaking,
        audioLevel = audioLevel.coerceIn(0f, 1f),
        camera = handle(Track.Source.CAMERA, VideoSource.CAMERA),
        screenShare = handle(Track.Source.SCREEN_SHARE, VideoSource.SCREEN_SHARE),
        connectionQuality = connectionQuality.toVoxera(),
    )
}
