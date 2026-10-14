package com.naury.voxerartc.livekit

import android.content.Intent
import com.naury.voxerartc.api.ScreenShareConsent

/**
 * Android 화면 공유 승인 결과. `MediaProjectionManager.createScreenCaptureIntent()`로 띄운 대화상자에서 사용자가 허락하면
 * 돌아오는 결과 Intent를 담는다. 한 번의 공유에만 쓰며 저장하지 않는다.
 */
public class MediaProjectionConsent(public val resultData: Intent) : ScreenShareConsent
