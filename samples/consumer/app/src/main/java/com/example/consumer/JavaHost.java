package com.example.consumer;

import android.content.Context;
import androidx.core.content.ContextCompat;
import com.naury.voxerartc.api.CallMode;
import com.naury.voxerartc.api.RtcCredentials;
import com.naury.voxerartc.api.VoxeraError;
import com.naury.voxerartc.core.java.VoxeraCallback;
import com.naury.voxerartc.core.java.VoxeraClient;
import com.naury.voxerartc.core.java.VoxeraJava;
import com.naury.voxerartc.livekit.LiveKitRtcEngineFactory;

/** Java 호스트가 문서의 facade 예제를 그대로 쓸 수 있는지 컴파일로 확인한다. */
final class JavaHost {
    private JavaHost() { }

    static VoxeraClient create(Context context) {
        VoxeraClient client = VoxeraJava.create(
            new LiveKitRtcEngineFactory(context),
            (request, callback) -> callback.onCredentials(new RtcCredentials("wss://example.invalid", "token")),
            ContextCompat.getMainExecutor(context));
        client.joinRoom("support-42", CallMode.VOICE, new VoxeraCallback<String>() {
            @Override public void onSuccess(String sessionId) { }
            @Override public void onError(VoxeraError error) { }
        }).cancel();
        return client;
    }
}
