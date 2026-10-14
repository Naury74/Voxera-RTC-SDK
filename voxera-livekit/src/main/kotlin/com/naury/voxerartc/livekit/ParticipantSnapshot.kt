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
 * [source] 트랙이 게시되어 있고 음소거가 아닌지. LiveKit의 `isMicrophoneEnabled`/`isCameraEnabled`는 다른 코루틴에서
 * 갱신되는 파생 값이라 게시 직후에는 아직 `false`일 수 있으므로 publication을 직접 본다.
 */
internal fun LiveKitParticipant.isPublishing(source: Track.Source): Boolean =
    getTrackPublication(source)?.let { !it.muted && it.track != null } == true

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
        isMicrophoneEnabled = isPublishing(Track.Source.MICROPHONE),
        isSpeaking = isSpeaking,
        audioLevel = audioLevel.coerceIn(0f, 1f),
        camera = handle(Track.Source.CAMERA, VideoSource.CAMERA),
        screenShare = handle(Track.Source.SCREEN_SHARE, VideoSource.SCREEN_SHARE),
        connectionQuality = connectionQuality.toVoxera(),
    )
}
