package com.naury.voxerartc.api

/**
 * 사용자의 의도([desired])와 엔진이 실제로 반영한 상태([actual])를 함께 둔다. 둘이 다르면 반영 중이다.
 * UI는 [actual]을 확정 상태로 보여 주고, [isPending]이면 진행 표시를 한다.
 */
public data class MediaToggle(
    val desired: Boolean,
    val actual: Boolean,
    val lastError: VoxeraError? = null,
) {
    val isPending: Boolean get() = desired != actual

    public companion object {
        public val Off: MediaToggle = MediaToggle(desired = false, actual = false)
    }
}

/** 오디오 출력 경로 종류. */
public enum class AudioRouteType { EARPIECE, SPEAKER, WIRED_HEADSET, BLUETOOTH, USB, UNKNOWN }

/** 선택할 수 있는 오디오 출력 경로. [isSelectable]이 `false`면 연결은 되어 있지만 지금 고를 수 없다. */
public data class AudioRoute(
    val id: String,
    val label: String,
    val type: AudioRouteType,
    val isSelectable: Boolean = true,
)

/** 카메라 방향. 기기에 해당 카메라가 없으면 전환이 [MediaActionResult.Unsupported]가 된다. */
public enum class CameraFacing { FRONT, BACK, EXTERNAL }

/** 로컬 미디어 상태. */
public data class LocalMediaState(
    val microphone: MediaToggle = MediaToggle.Off,
    val camera: MediaToggle = MediaToggle.Off,
    /** 내 화면 공유. 끄는 것은 언제든 가능하지만 켜려면 매번 새 [ScreenShareConsent]가 필요하다. */
    val screenShare: MediaToggle = MediaToggle.Off,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val canSwitchCamera: Boolean = false,
    val audioRoutes: List<AudioRoute> = emptyList(),
    val selectedAudioRouteId: String? = null,
    /** 지금 적용 중인 카메라 효과. */
    val videoEffect: VideoEffect = VideoEffect.None,
    val effectsAvailability: EffectsAvailability = EffectsAvailability.UNSUPPORTED,
    /**
     * 다른 통화(휴대전화 통화·다른 앱의 통화)가 오디오를 가져가 내 소리가 상대에게 가지 않는 중. 그 통화가 끝나면 `false`로 돌아온다.
     * 엔진이 알 수 없으면 늘 `false`다(LiveKit 엔진은 Android 12+에서 알려 준다).
     */
    val audioInterrupted: Boolean = false,
)
