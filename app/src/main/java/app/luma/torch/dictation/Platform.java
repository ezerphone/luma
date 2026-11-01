package app.luma.torch.dictation;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

/** Permission checks with the same self-UID semantics before and after runtime permissions. */
final class Platform {
    private Platform() {}
    static boolean hasPermission(Context context,String permission) {
        return context.checkPermission(permission,Process.myPid(),Process.myUid())==PackageManager.PERMISSION_GRANTED;
    }
}
