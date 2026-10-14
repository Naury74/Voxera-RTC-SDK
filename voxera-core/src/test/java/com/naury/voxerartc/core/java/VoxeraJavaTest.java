package com.naury.voxerartc.core.java;

import static com.google.common.truth.Truth.assertThat;

import com.naury.voxerartc.api.CallMode;
import com.naury.voxerartc.api.CredentialsUnavailableException;
import com.naury.voxerartc.api.MediaActionResult;
import com.naury.voxerartc.api.RtcCredentials;
import com.naury.voxerartc.api.SessionState;
import com.naury.voxerartc.api.VoxeraError;
import com.naury.voxerartc.testkit.FakeRtcEngine;
import com.naury.voxerartc.testkit.FakeRtcEngineFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;

/** Java 호스트가 실제로 쓰는 모양 그대로 facade를 호출한다. Kotlin 전용 타입이 필요하면 이 파일이 컴파일되지 않는다. */
public class VoxeraJavaTest {

    private final ExecutorService callbackThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "callback"));
    private final FakeRtcEngineFactory engines = new FakeRtcEngineFactory();

    @After
    public void tearDown() {
        callbackThread.shutdownNow();
    }

    private VoxeraClient client(JavaCredentialsProvider provider) {
        return VoxeraJava.create(engines, provider, callbackThread);
    }

    private static JavaCredentialsProvider okProvider() {
        return (request, callback) -> callback.onCredentials(new RtcCredentials("wss://t.livekit.cloud", "token"));
    }

    @Test
    public void joinRoomDeliversSuccessOnCallbackExecutor() throws Exception {
        VoxeraClient client = client(okProvider());
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> thread = new AtomicReference<>();
        AtomicReference<String> sessionId = new AtomicReference<>();

        client.joinRoom("support-42", CallMode.VOICE, new VoxeraCallback<String>() {
            @Override public void onSuccess(String value) {
                sessionId.set(value);
                thread.set(Thread.currentThread().getName());
                done.countDown();
            }

            @Override public void onError(VoxeraError error) {
                done.countDown();
            }
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sessionId.get()).isNotEmpty();
        assertThat(thread.get()).isEqualTo("callback");
        assertThat(client.getCurrentState()).isInstanceOf(SessionState.Connected.class);
        client.close();
    }

    @Test
    public void credentialFailureIsReportedAsError() throws Exception {
        VoxeraClient client = client((request, callback) ->
            callback.onError(new CredentialsUnavailableException(CredentialsUnavailableException.Kind.UNAUTHORIZED, null, null)));
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<VoxeraError> error = new AtomicReference<>();
        client.joinRoom("room", CallMode.VOICE, new VoxeraCallback<String>() {
            @Override public void onSuccess(String value) { done.countDown(); }
            @Override public void onError(VoxeraError e) { error.set(e); done.countDown(); }
        });
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isEqualTo(VoxeraError.HostUnauthorized.INSTANCE);
        client.close();
    }

    @Test
    public void cancellingPendingJoinSuppressesCallbackAndReleasesEngine() throws Exception {
        FakeRtcEngineFactory hanging = new FakeRtcEngineFactory(() -> {
            FakeRtcEngine engine = new FakeRtcEngine();
            engine.setHangOnConnect(true);
            return engine;
        });
        VoxeraClient client = VoxeraJava.create(hanging, okProvider(), callbackThread);
        List<String> calls = new ArrayList<>();
        VoxeraCancellable pending = client.joinRoom("room", CallMode.VOICE, new VoxeraCallback<String>() {
            @Override public void onSuccess(String value) { calls.add("success"); }
            @Override public void onError(VoxeraError error) { calls.add("error"); }
        });
        Thread.sleep(200);
        pending.cancel();
        pending.cancel();
        Thread.sleep(300);

        assertThat(calls).isEmpty();
        assertThat(hanging.getLast().isReleased()).isTrue();
        assertThat(client.getCurrentState()).isInstanceOf(SessionState.Ended.class);
        client.close();
    }

    @Test
    public void listenersAndMediaActionsWork() throws Exception {
        VoxeraClient client = client(okProvider());
        List<SessionState> states = new ArrayList<>();
        CountDownLatch connected = new CountDownLatch(1);
        VoxeraCancellable listener = client.addStateListener(state -> {
            synchronized (states) { states.add(state); }
            if (state instanceof SessionState.Connected) connected.countDown();
        });
        client.joinRoom("room", CallMode.VOICE, new VoxeraCallback<String>() {
            @Override public void onSuccess(String value) { }
            @Override public void onError(VoxeraError error) { }
        });
        assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

        CountDownLatch muted = new CountDownLatch(1);
        AtomicReference<MediaActionResult> result = new AtomicReference<>();
        client.setMicrophoneEnabled(false, r -> { result.set(r); muted.countDown(); });
        assertThat(muted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isEqualTo(MediaActionResult.Applied.INSTANCE);

        listener.cancel();
        CountDownLatch left = new CountDownLatch(1);
        client.leave(left::countDown);
        assertThat(left.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(client.getCurrentState()).isInstanceOf(SessionState.Ended.class);
        synchronized (states) { assertThat(states.get(0)).isEqualTo(SessionState.Idle.INSTANCE); }
        client.close();
    }
}
