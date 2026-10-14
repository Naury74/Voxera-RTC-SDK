package com.naury.voxerartc.api

/**
 * 내 카메라 영상의 색 효과(v1, 저비용). 상대에게 보내는 영상과 내 미리보기에 함께 적용된다.
 *
 * @property brightness -0.5~0.5. 0이면 그대로.
 * @property contrast 0.5~1.5. 1이면 그대로.
 * @property saturation 0~2. 1이면 그대로, 0이면 흑백.
 */
public data class VideoEffect(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
) {
    init {
        require(brightness in -0.5f..0.5f) { "brightness must be in -0.5..0.5" }
        require(contrast in 0.5f..1.5f) { "contrast must be in 0.5..1.5" }
        require(saturation in 0f..2f) { "saturation must be in 0..2" }
    }

    val isNone: Boolean get() = brightness == 0f && contrast == 1f && saturation == 1f

    public companion object {
        public val None: VideoEffect = VideoEffect()
    }
}

/** 효과를 쓸 수 있는지. 쓸 수 없으면 UI는 효과를 비활성화하고 이유를 보여 준다. */
public enum class EffectsAvailability {
    AVAILABLE,

    /** 이 엔진·기기에서 지원하지 않음. */
    UNSUPPORTED,

    /** 처리 시간이 예산을 넘어 원본 영상으로 되돌림. 통화는 계속된다. */
    DISABLED_FOR_PERFORMANCE,
}
