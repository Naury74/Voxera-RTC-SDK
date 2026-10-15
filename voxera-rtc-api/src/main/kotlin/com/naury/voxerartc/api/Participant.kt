package com.naury.voxerartc.api

/** 영상 트랙 출처. 카메라와 화면 공유는 따로 다룬다. */
public enum class VideoSource { CAMERA, SCREEN_SHARE }

/**
 * 영상 트랙을 가리키는 불투명 참조. LiveKit Track 같은 엔진 객체는 드러내지 않으며, UI 어댑터는 이 값으로 렌더러를 찾는다.
 *
 * [trackId]는 publication 기준이다. 재연결이나 재게시 뒤에도 같은 값이라고 가정하지 않는다.
 */
public data class VideoTrackHandle(
    val participantId: String,
    val trackId: String,
    val source: VideoSource,
)

/** 엔진이 알려 주는 참가자 연결 품질. 모르면 [UNKNOWN]. */
public enum class ConnectionQuality { EXCELLENT, GOOD, POOR, LOST, UNKNOWN }

/**
 * 화면에 보여 줄 참가자 상태.
 *
 * @property id RTC provider의 identity에서 가져온 고정 ID. 표시 이름이 바뀌어도 유지된다.
 * @property audioLevel 0.0~1.0. 엔진이 주지 않으면 0이다.
 * @property camera 카메라가 켜져 있고 구독할 수 있을 때만 값이 있다. `null`이면 아바타를 보여 준다.
 * @property screenShare 화면 공유 중일 때만 값이 있다.
 * @property handRaisedAtMs 손을 든 시각(`VoxeraSession` 시계 기준). 손을 들지 않았으면 `null`. 먼저 든 사람부터 보여 줄 때 쓴다.
 */
public data class Participant(
    val id: String,
    val displayName: String,
    val isLocal: Boolean,
    val isMicrophoneEnabled: Boolean = false,
    val isSpeaking: Boolean = false,
    val audioLevel: Float = 0f,
    val camera: VideoTrackHandle? = null,
    val screenShare: VideoTrackHandle? = null,
    val connectionQuality: ConnectionQuality = ConnectionQuality.UNKNOWN,
    val handRaisedAtMs: Long? = null,
) {
    val isHandRaised: Boolean get() = handRaisedAtMs != null
}
