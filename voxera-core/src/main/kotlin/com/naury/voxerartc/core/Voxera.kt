package com.naury.voxerartc.core

import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.RtcEngineFactory
import com.naury.voxerartc.api.RtcEngineKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicReference

/** 세션을 만드는 SDK 인스턴스. 같은 인스턴스에서 만든 세션들은 동시에 하나만 통화할 수 있다. */
public interface VoxeraSdk {
    /** 이 SDK가 쓰는 엔진 종류. [RtcEngineKind.SIMULATED]면 실제 통화가 아니다. */
    public val engineKind: RtcEngineKind

    public fun createSession(config: VoxeraSessionConfig): VoxeraSession
}

/** Voxera 진입점. 전역 상태를 두지 않으므로 호스트가 [VoxeraSdk] 인스턴스의 수명을 정한다. */
public object Voxera {
    /** [engineFactory]로 연결하는 SDK를 만든다. 실제 통화는 `voxera-livekit`의 factory를 넘긴다. */
    public fun create(engineFactory: RtcEngineFactory): VoxeraSdk =
        create(engineFactory, MonotonicClock.System, Dispatchers.Default)

    internal fun create(engineFactory: RtcEngineFactory, clock: MonotonicClock, dispatcher: CoroutineDispatcher): VoxeraSdk =
        DefaultVoxeraSdk(engineFactory, clock, dispatcher)
}

private class DefaultVoxeraSdk(
    private val engineFactory: RtcEngineFactory,
    private val clock: MonotonicClock,
    private val dispatcher: CoroutineDispatcher,
) : VoxeraSdk {
    private val callLock = ActiveCallLock()

    override val engineKind: RtcEngineKind get() = engineFactory.kind

    override fun createSession(config: VoxeraSessionConfig): VoxeraSession =
        DefaultVoxeraSession(config, engineFactory, callLock, clock, dispatcher)
}

/** 한 SDK 안에서 미디어를 쓰는 세션을 하나로 제한한다. 같은 소유자는 다시 얻어도 성공한다. */
internal class ActiveCallLock {
    private val owner = AtomicReference<Any?>(null)

    fun tryAcquire(candidate: Any): Boolean = owner.compareAndSet(null, candidate) || owner.get() === candidate

    fun release(candidate: Any) {
        owner.compareAndSet(candidate, null)
    }
}
