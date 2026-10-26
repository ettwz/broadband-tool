package dev.broadband.toolbox.privileged;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import dev.broadband.toolbox.privileged.IRootEvents;
interface IRootService {
    void destroy() = 16777114;
    String connect(IBinder client) = 1;
    String heartbeat() = 2;
    String start(String request, in ParcelFileDescriptor output, IRootEvents callback) = 3;
    void cancel(String requestId) = 4;
    void stopAll() = 5;
}
