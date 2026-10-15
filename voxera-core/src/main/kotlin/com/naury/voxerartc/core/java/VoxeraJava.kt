package com.naury.voxerartc.core.java

import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.CredentialRequest
import com.naury.voxerartc.api.CredentialsUnavailableException
import com.naury.voxerartc.api.JoinResult
import com.naury.voxerartc.api.LeaveReason
import com.naury.voxerartc.api.MediaActionResult
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.RtcEngineFactory
import com.naury.voxerartc.api.ScreenShareConsent
import com.naury.voxerartc.api.Reaction
import com.naury.voxerartc.api.ReactionEvent
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VoxeraError
import com.naury.voxerartc.core.MediaPreferences
import com.naury.voxerartc.core.SessionPolicy
import com.naury.voxerartc.core.Voxera
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.core.VoxeraSessionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 성공/실패 콜백. 요청 하나에 둘 중 하나가 딱 한 번 불린다. */
public interface VoxeraCallback<T> {
    public fun onSuccess(value: T)

    public fun onError(error: VoxeraError)
}

/** 마이크나 카메라를 조작한 결과를 받는 콜백. */
public fun interface VoxeraActionCallback {
    public fun onComplete(result: MediaActionResult)
}

/** 상태 변화 리스너. 등록하면 현재 값을 먼저 한 번 받는다. */
public fun interface VoxeraListener<T> {
    public fun onChanged(value: T)
}

/** 진행 중인 요청이나 리스너를 멈춘다. 여러 번 불러도 안전하다. */
public fun interface VoxeraCancellable {
    public fun cancel()
}

/** Java 호스트용 자격 증명 결과 콜백. */
public interface CredentialsCallback {
    public fun onCredentials(credentials: RtcCredentials)

    public fun onError(error: CredentialsUnavailableException)
}

/** Java 호스트가 구현하는 자격 증명 공급자. 어느 스레드에서 콜백해도 된다. */
public fun interface JavaCredentialsProvider {
    public fun getCredentials(request: CredentialRequest, callback: CredentialsCallback)
}

/**
 * Java나 View 기반 호스트를 위한 진입점. suspend 함수와 Flow 대신 콜백과 [VoxeraCancellable]을 쓴다.
 *
 * ```java
 * VoxeraClient client = VoxeraJava.create(new LiveKitRtcEngineFactory(context), provider, ContextCompat.getMainExecutor(context));
 * VoxeraCancellable pending = client.joinRoom("support-42", CallMode.VOICE, callback);
 * client.close();
 * ```
 */
public object VoxeraJava {
    /**
     * @param callbackExecutor 모든 콜백과 리스너를 실행할 executor. Android에서는 main executor를 넘긴다.
     */
    @JvmStatic
    @JvmOverloads
    public fun create(
        engineFactory: RtcEngineFactory,
        credentials: JavaCredentialsProvider,
        callbackExecutor: Executor,
        media: MediaPreferences = MediaPreferences(),
        policy: SessionPolicy = SessionPolicy(),
    ): VoxeraClient {
        val provider = RtcCredentialsProvider { request -> credentials.await(request) }
        val session = Voxera.create(engineFactory).createSession(VoxeraSessionConfig(credentials = provider, media = media, policy = policy))
        return VoxeraClient(session, callbackExecutor)
    }
}

private suspend fun JavaCredentialsProvider.await(request: CredentialRequest): RtcCredentials =
    suspendCancellableCoroutine { continuation ->
        val done = AtomicBoolean(false)
        getCredentials(
            request,
            object : CredentialsCallback {
                override fun onCredentials(credentials: RtcCredentials) {
                    if (done.compareAndSet(false, true)) continuation.resume(credentials)
                }

                override fun onError(error: CredentialsUnavailableException) {
                    if (done.compareAndSet(false, true)) continuation.resumeWithException(error)
                }
            },
        )
    }

/**
 * [VoxeraSession]을 콜백으로 감싼다. 모든 메서드는 바로 반환하므로 메인 스레드를 막지 않는다.
 * 취소한 뒤에는 콜백이 불리지 않는다.
 */
public class VoxeraClient internal constructor(
    /** 감싸고 있는 원래 세션. Kotlin이나 Compose 코드(예: `VoxeraRoomScreen`)와 함께 쓸 때 넘긴다. */
    public val session: VoxeraSession,
    private val executor: Executor,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    public val currentState: SessionState get() = session.state.value

    /** 룸에 들어간다. 성공하면 세션 id, 실패하면 [VoxeraError]. */
    public fun joinRoom(roomId: String, mode: CallMode, callback: VoxeraCallback<String>): VoxeraCancellable =
        launchCancellable { isCancelled ->
            when (val result = session.join(roomId, mode)) {
                is JoinResult.Connected -> deliver(isCancelled) { callback.onSuccess(result.sessionId) }
                is JoinResult.Rejected -> deliver(isCancelled) { callback.onError(result.error) }
            }
        }

    public fun setMicrophoneEnabled(enabled: Boolean, callback: VoxeraActionCallback?): VoxeraCancellable =
        action(callback) { session.setMicrophoneEnabled(enabled) }

    public fun setCameraEnabled(enabled: Boolean, callback: VoxeraActionCallback?): VoxeraCancellable =
        action(callback) { session.setCameraEnabled(enabled) }

    public fun switchCamera(callback: VoxeraActionCallback?): VoxeraCancellable = action(callback) { session.switchCamera() }

    public fun startScreenShare(consent: ScreenShareConsent, callback: VoxeraActionCallback?): VoxeraCancellable =
        action(callback) { session.startScreenShare(consent) }

    public fun stopScreenShare(callback: VoxeraActionCallback?): VoxeraCancellable = action(callback) { session.stopScreenShare() }

    public fun selectAudioRoute(routeId: String, callback: VoxeraActionCallback?): VoxeraCancellable =
        action(callback) { session.selectAudioRoute(routeId) }

    public fun sendReaction(reaction: Reaction, callback: VoxeraActionCallback?): VoxeraCancellable =
        action(callback) { session.sendReaction(reaction) }

    /** 통화를 끝낸다. 끝나면 [onDone]을 부른다. [onDone]은 `null`이어도 된다. */
    public fun leave(onDone: Runnable?) {
        scope.launch {
            session.leave(LeaveReason.USER)
            if (onDone != null) executor.execute(onDone)
        }
    }

    /** 등록하는 순간의 상태를 먼저 받고, 이후 바뀔 때마다 받는다. 값이 연달아 빠르게 바뀌면 중간 값은 건너뛸 수 있다. */
    public fun addStateListener(listener: VoxeraListener<SessionState>): VoxeraCancellable =
        launchCancellable(CoroutineStart.UNDISPATCHED) { isCancelled ->
            session.state.collect { value -> deliver(isCancelled) { listener.onChanged(value) } }
        }

    public fun addParticipantsListener(listener: VoxeraListener<List<Participant>>): VoxeraCancellable =
        launchCancellable(CoroutineStart.UNDISPATCHED) { isCancelled ->
            session.participants.collect { value -> deliver(isCancelled) { listener.onChanged(value) } }
        }

    /** 등록한 뒤에 오는 반응만 받는다. 지난 반응은 다시 오지 않는다. */
    public fun addReactionListener(listener: VoxeraListener<ReactionEvent>): VoxeraCancellable =
        launchCancellable(CoroutineStart.UNDISPATCHED) { isCancelled ->
            session.reactions.collect { value -> deliver(isCancelled) { listener.onChanged(value) } }
        }

    private fun action(callback: VoxeraActionCallback?, block: suspend () -> MediaActionResult): VoxeraCancellable =
        launchCancellable { isCancelled ->
            val result = block()
            if (callback != null) deliver(isCancelled) { callback.onComplete(result) }
        }

    private fun launchCancellable(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend (isCancelled: () -> Boolean) -> Unit,
    ): VoxeraCancellable {
        val cancelled = AtomicBoolean(false)
        val job = scope.launch(start = start) { block { cancelled.get() } }
        return VoxeraCancellable {
            cancelled.set(true)
            job.cancel()
        }
    }

    private fun deliver(isCancelled: () -> Boolean, call: () -> Unit) {
        executor.execute { if (!isCancelled()) call() }
    }

    /** 통화를 끝내고 리스너를 모두 멈춘다. */
    override fun close() {
        session.close()
        scope.cancel()
    }
}
