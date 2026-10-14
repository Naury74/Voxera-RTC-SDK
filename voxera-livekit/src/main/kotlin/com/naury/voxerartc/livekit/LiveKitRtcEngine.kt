package com.naury.voxerartc.livekit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.CameraFacing
import com.naury.voxerartc.api.ConnectOptions
import com.naury.voxerartc.api.ConnectOutcome
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.MediaActionResult
import com.naury.voxerartc.api.MediaDevice
import com.naury.voxerartc.api.MediaPermission
import com.naury.voxerartc.api.MediaToggle
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcEngine
import com.naury.voxerartc.api.RtcEngineEvent
import com.naury.voxerartc.api.RtcEngineFactory
import com.naury.voxerartc.api.RtcEngineKind
import com.naury.voxerartc.api.VoxeraError
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioDeviceChangeListener
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.TrackPublication
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import com.naury.voxerartc.api.ScreenShareConsent
import com.naury.voxerartc.api.EffectsAvailability
import com.naury.voxerartc.api.VideoEffect
import com.naury.voxerartc.api.VideoTrackHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import livekit.org.webrtc.RTCStatsCollectorCallback
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean
import io.livekit.android.room.participant.Participant as LiveKitParticipant

/**
 * 실제 LiveKit 룸에 연결하는 [RtcEngineFactory]. 연결마다 새 `Room`을 만들고, 세션이 끝나면 `Room.release`로 해제한다.
 *
 * 마이크·카메라 권한은 호스트가 입장 전에 요청해야 한다. 권한이 없으면 해당 미디어 없이 입장하고, 조작하면
 * [VoxeraError.PermissionDenied]를 돌려준다.
 *
 * @param context Activity가 아닌 application context를 보관한다.
 */
public class LiveKitRtcEngineFactory(context: Context) : RtcEngineFactory {
    private val appContext = context.applicationContext

    /** 이 factory가 만든 엔진의 영상 트랙을 화면에 붙인다. UI 어댑터(voxera-livekit-compose)가 쓴다. */
    public val videoTracks: LiveKitVideoTracks = LiveKitVideoTracks()

    override val kind: RtcEngineKind = RtcEngineKind.LIVE

    override fun create(): RtcEngine = LiveKitRtcEngine(appContext, videoTracks)
}

/**
 * LiveKit `Room` 하나를 [RtcEngine]으로 감싼다. LiveKit 객체는 이 클래스 밖으로 나가지 않는다.
 *
 * 재연결은 LiveKit이 스스로 하며, 이 엔진은 `Reconnecting`/`Reconnected`/`Disconnected` 이벤트만 옮긴다.
 */
internal class LiveKitRtcEngine(
    private val context: Context,
    private val videoTracks: LiveKitVideoTracks,
) : RtcEngine {

    override val kind: RtcEngineKind = RtcEngineKind.LIVE

    private val room: Room = LiveKit.create(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val released = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)

    // 이벤트를 버리지 않도록 수집 코루틴에서 suspend emit으로 보낸다. 세션은 connect 전에 구독한다.
    private val _events = MutableSharedFlow<RtcEngineEvent>(extraBufferCapacity = 64)
    override val events: Flow<RtcEngineEvent> = _events.asSharedFlow()

    private val audioDeviceListener: AudioDeviceChangeListener = { _: List<AudioDevice>, _: AudioDevice? ->
        scope.launch { _events.emit(RtcEngineEvent.LocalMediaChanged(localMedia())) }
    }

    /** 음성 모드면 원격 영상은 화면에 그리지 않으므로 받지 않는다(데이터·배터리 절약). */
    @Volatile
    private var receiveVideo = true

    private fun applyVideoSubscription(publication: TrackPublication) {
        if (publication.kind != Track.Kind.VIDEO) return
        (publication as? RemoteTrackPublication)?.setSubscribed(receiveVideo)
    }

    override suspend fun connect(credentials: RtcCredentials, options: ConnectOptions): ConnectOutcome {
        receiveVideo = options.mode == CallMode.VIDEO
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            room.events.events.collect { event -> onRoomEvent(event) }
        }
        try {
            room.connect(credentials.serverUrl, credentials.accessToken)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { room.disconnect() }
            throw cancelled
        } catch (failure: Exception) {
            return ConnectOutcome.Failed(classifyConnectFailure(failure))
        }
        connected.set(true)
        room.remoteParticipants.values.forEach { participant -> participant.trackPublications.values.forEach(::applyVideoSubscription) }
        videoTracks.register(this)
        room.audioSwitchHandler?.registerAudioDeviceChangeListener(audioDeviceListener)
        // 권한이 없거나 장치를 못 열어도 입장은 유지한다. 실제 게시 상태는 LocalMediaChanged로 알린다.
        if (options.startWithMicrophone) setMicrophoneEnabled(true)
        if (options.startWithCamera) setCameraEnabled(true)
        _events.emit(RtcEngineEvent.LocalMediaChanged(localMedia()))
        scope.launch { sampleQuality() }
        return ConnectOutcome.Connected(room.localParticipant.snapshot())
    }

    /** 연결되어 있는 동안 [QUALITY_INTERVAL_MS]마다 WebRTC 통계를 모아 품질 표본을 보낸다. 재연결 중에는 건너뛴다. */
    private suspend fun sampleQuality() {
        val sampler = QualitySampler()
        while (currentCoroutineContext().isActive) {
            delay(QUALITY_INTERVAL_MS)
            if (room.state != Room.State.CONNECTED) continue
            val publisher = withTimeoutOrNull(STATS_TIMEOUT_MS) { collectStats(room::getPublisherRTCStats) } ?: emptyList()
            val subscriber = withTimeoutOrNull(STATS_TIMEOUT_MS) { collectStats(room::getSubscriberRTCStats) } ?: emptyList()
            _events.emit(RtcEngineEvent.QualitySampled(sampler.sample(System.nanoTime() / 1_000_000, publisher, subscriber, effectProcessor.lastAverageMs)))
        }
    }

    private suspend fun collectStats(request: (RTCStatsCollectorCallback) -> Unit): List<StatEntry> =
        suspendCancellableCoroutine { continuation ->
            request(
                RTCStatsCollectorCallback { report ->
                    val entries = report.statsMap.values.map { StatEntry(it.id, it.type, it.members) }
                    if (continuation.isActive) continuation.resume(entries)
                },
            )
        }

    private suspend fun onRoomEvent(event: RoomEvent) {
        if (!connected.get() || released.get()) return
        val mapped: List<RtcEngineEvent> = when (event) {
            is RoomEvent.Reconnecting -> listOf(RtcEngineEvent.Reconnecting)
            is RoomEvent.Reconnected -> listOf(RtcEngineEvent.Reconnected) + allParticipants()
            is RoomEvent.Disconnected -> when (val mapping = mapDisconnect(event.reason)) {
                DisconnectMapping.Ignore -> emptyList()
                is DisconnectMapping.Notify -> listOf(RtcEngineEvent.Disconnected(mapping.error))
            }
            is RoomEvent.ParticipantConnected -> listOf(upsert(event.participant))
            is RoomEvent.ParticipantDisconnected -> listOf(RtcEngineEvent.ParticipantLeft(event.participant.voxeraId))
            is RoomEvent.ParticipantNameChanged -> listOf(upsert(event.participant))
            is RoomEvent.ActiveSpeakersChanged -> allParticipants()
            is RoomEvent.ConnectionQualityChanged -> listOf(upsert(event.participant))
            is RoomEvent.TrackSubscribed -> {
                // 입장 전부터 있던 트랙은 TrackPublished 없이 자동 구독되므로 여기서도 음성 모드 규칙을 적용한다.
                applyVideoSubscription(event.publication)
                listOf(upsert(event.participant))
            }
            is RoomEvent.TrackUnsubscribed -> listOf(upsert(event.participant))
            is RoomEvent.TrackPublished -> {
                applyVideoSubscription(event.publication)
                listOf(upsert(event.participant))
            }
            is RoomEvent.TrackUnpublished -> listOf(upsert(event.participant))
            is RoomEvent.TrackMuted -> listOf(upsert(event.participant))
            is RoomEvent.TrackUnmuted -> listOf(upsert(event.participant))
            else -> emptyList()
        }
        mapped.forEach { _events.emit(it) }
    }

    private fun upsert(participant: LiveKitParticipant): RtcEngineEvent =
        RtcEngineEvent.ParticipantUpserted(participant.snapshot())

    private fun allParticipants(): List<RtcEngineEvent> =
        (listOf(room.localParticipant) + room.remoteParticipants.values).map(::upsert)

    override suspend fun setMicrophoneEnabled(enabled: Boolean): MediaActionResult {
        if (enabled && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            return MediaActionResult.Failed(VoxeraError.PermissionDenied(MediaPermission.MICROPHONE))
        }
        return toggle(MediaDevice.MICROPHONE) { room.localParticipant.setMicrophoneEnabled(enabled) }
    }

    override suspend fun setCameraEnabled(enabled: Boolean): MediaActionResult {
        if (enabled && !hasPermission(Manifest.permission.CAMERA)) {
            return MediaActionResult.Failed(VoxeraError.PermissionDenied(MediaPermission.CAMERA))
        }
        if (enabled && cameraCount() == 0) return MediaActionResult.Unsupported
        val local = room.localParticipant
        // 처음 켤 때만 색 효과 처리기를 단 카메라 트랙을 직접 만들어 게시한다. 이후 켜고 끄기는 LiveKit 기본 경로(음소거)를 쓴다.
        if (enabled && local.getTrackPublication(Track.Source.CAMERA) == null) {
            return toggle(MediaDevice.CAMERA) {
                val track = local.createVideoTrack(name = "camera", videoProcessor = effectProcessor)
                track.startCapture()
                local.publishVideoTrack(track).also { published ->
                    if (!published) {
                        track.stopCapture()
                        track.dispose()
                    }
                }
            }
        }
        return toggle(MediaDevice.CAMERA) { local.setCameraEnabled(enabled) }
    }

    /** 성능 예산을 넘겨 효과가 꺼지면 true. 다시 켜려면 사용자가 효과를 새로 고른다. */
    @Volatile
    private var effectsThrottled = false

    private val effectProcessor = ColorEffectProcessor(onThrottled = {
        effectsThrottled = true
        scope.launch { emitLocalState() }
    })

    override suspend fun setVideoEffect(effect: VideoEffect): MediaActionResult {
        effectsThrottled = false
        effectProcessor.effect = effect
        emitLocalState()
        return MediaActionResult.Applied
    }

    private suspend fun toggle(device: MediaDevice, action: suspend () -> Boolean): MediaActionResult {
        val applied = try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 다른 앱이 카메라를 쓰는 중이거나 장치를 열지 못함. 통화는 유지한다.
            false
        } catch (_: AssertionError) {
            // WebRTC 오디오 모듈은 AudioRecord를 열지 못하면 AssertionError를 던진다(API 26 에뮬레이터에서 확인).
            // 앱을 죽이지 않고 장치 없음으로 처리한다.
            false
        }
        emitLocalState()
        return if (applied) MediaActionResult.Applied else MediaActionResult.Failed(VoxeraError.MediaDeviceUnavailable(device))
    }

    override suspend fun switchCamera(): MediaActionResult {
        val track = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
            ?: return MediaActionResult.Unsupported
        if (cameraCount() < 2) return MediaActionResult.Unsupported
        val next = if (track.options.position == CameraPosition.FRONT) CameraPosition.BACK else CameraPosition.FRONT
        track.switchCamera(position = next)
        _events.emit(RtcEngineEvent.LocalMediaChanged(localMedia()))
        return MediaActionResult.Applied
    }

    override suspend fun startScreenShare(consent: ScreenShareConsent): MediaActionResult {
        val data = (consent as? MediaProjectionConsent)?.resultData ?: return MediaActionResult.Unsupported
        val params = ScreenCaptureParams(
            mediaProjectionPermissionResultData = data,
            // 사용자가 시스템 알림에서 멈추거나 화면이 잠겨 공유가 끝나면 상태를 되돌린다.
            onStop = { scope.launch { emitLocalState() } },
        )
        return toggle(MediaDevice.SCREEN) { room.localParticipant.setScreenShareEnabled(true, params) }
    }

    override suspend fun stopScreenShare(): MediaActionResult =
        toggle(MediaDevice.SCREEN) { room.localParticipant.setScreenShareEnabled(false) }

    private suspend fun emitLocalState() {
        if (released.get()) return
        _events.emit(RtcEngineEvent.ParticipantUpserted(room.localParticipant.snapshot()))
        _events.emit(RtcEngineEvent.LocalMediaChanged(localMedia()))
    }

    override suspend fun selectAudioRoute(routeId: String): MediaActionResult {
        val handler = room.audioSwitchHandler ?: return MediaActionResult.Unsupported
        val device = handler.availableAudioDevices.firstOrNull { it.routeId() == routeId } ?: return MediaActionResult.Unsupported
        handler.selectDevice(device)
        return MediaActionResult.Applied
    }

    private fun localMedia(): LocalMediaState {
        val local = room.localParticipant
        val camera = local.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
        val handler = room.audioSwitchHandler
        val microphoneOn = local.isPublishing(Track.Source.MICROPHONE)
        val cameraOn = local.isPublishing(Track.Source.CAMERA)
        return LocalMediaState(
            microphone = MediaToggle(microphoneOn, microphoneOn),
            camera = MediaToggle(cameraOn, cameraOn),
            screenShare = local.isPublishing(Track.Source.SCREEN_SHARE).let { MediaToggle(it, it) },
            cameraFacing = when (camera?.options?.position) {
                CameraPosition.BACK -> CameraFacing.BACK
                else -> CameraFacing.FRONT
            },
            canSwitchCamera = cameraCount() > 1,
            audioRoutes = handler?.availableAudioDevices.orEmpty().map { it.toRoute() },
            selectedAudioRouteId = handler?.selectedAudioDevice?.routeId(),
            videoEffect = effectProcessor.effect,
            effectsAvailability = if (effectsThrottled) EffectsAvailability.DISABLED_FOR_PERFORMANCE else EffectsAvailability.AVAILABLE,
        )
    }

    /** 핸들이 가리키는 영상 트랙. 참가자가 나갔거나 트랙이 바뀌었으면 `null`. */
    internal fun findVideoTrack(handle: VideoTrackHandle): Pair<Room, VideoTrack>? {
        if (released.get()) return null
        val participant = (listOf(room.localParticipant) + room.remoteParticipants.values)
            .firstOrNull { it.voxeraId == handle.participantId } ?: return null
        val track = participant.trackPublications[handle.trackId]?.track as? VideoTrack ?: return null
        return room to track
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun cameraCount(): Int =
        (context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager)?.cameraIdList?.size ?: 0

    private companion object {
        const val QUALITY_INTERVAL_MS = 2_000L
        const val STATS_TIMEOUT_MS = 1_000L
    }

    override suspend fun release() {
        if (!released.compareAndSet(false, true)) return
        videoTracks.unregister(this)
        room.audioSwitchHandler?.unregisterAudioDeviceChangeListener(audioDeviceListener)
        scope.cancel()
        room.release()
    }
}
