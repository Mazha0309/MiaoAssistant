package com.mazha0309.miaoassistant.privileged;

import android.content.ClipData;
import android.content.ClipDescription;
import android.os.PersistableBundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.util.Base64;

import java.lang.reflect.Method;

/** Clipboard binder access used only from a process running with a privileged Android UID. */
final class ShellClipboardBridge {
    static final String EMPTY_SNAPSHOT = "~";
    private static final int SYSTEM_UID = 1000;
    private static final int SHELL_UID = 2000;
    private static final String SYSTEM_PACKAGE = "android";
    private static final String SHELL_PACKAGE = "com.android.shell";
    private static final int MAX_SNAPSHOT_BYTES = 32 * 1024;

    private ShellClipboardBridge() {}

    static String swap(String text) throws Exception {
        Object service = getClipboardService();
        ClipDescription description = (ClipDescription) invoke(service, "getPrimaryClipDescription", null);
        if (Process.myUid() != SYSTEM_UID && !isSafelyRestorable(description)) {
            throw new IllegalStateException("The existing clipboard is not plain text");
        }
        ClipData previous = description == null ? null : (ClipData) invoke(service, "getPrimaryClip", null);
        String snapshot = encodeSnapshot(previous);
        invoke(service, "setPrimaryClip", createSensitiveClip(text));
        return snapshot;
    }

    private static boolean isSafelyRestorable(ClipDescription description) {
        if (description == null) return true;
        if (description.getMimeTypeCount() == 0) return false;
        for (int index = 0; index < description.getMimeTypeCount(); index++) {
            String type = description.getMimeType(index);
            if (!ClipDescription.MIMETYPE_TEXT_PLAIN.equals(type)
                    && !ClipDescription.MIMETYPE_TEXT_HTML.equals(type)) {
                return false;
            }
        }
        return true;
    }

    private static ClipData createSensitiveClip(String text) {
        ClipData clip = ClipData.newPlainText("", text);
        PersistableBundle extras = new PersistableBundle();
        // Literal used for API 24 compatibility; this is ClipDescription.EXTRA_IS_SENSITIVE.
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        clip.getDescription().setExtras(extras);
        return clip;
    }

    static void restore(String snapshot) throws Exception {
        Object service = getClipboardService();
        if (EMPTY_SNAPSHOT.equals(snapshot)) {
            invoke(service, "clearPrimaryClip", null);
        } else {
            invoke(service, "setPrimaryClip", decodeSnapshot(snapshot));
        }
    }

    static boolean verifyRoundTrip(String text) throws Exception {
        String snapshot = swap(text);
        try {
            ClipData current = (ClipData) invoke(getClipboardService(), "getPrimaryClip", null);
            return current != null
                    && current.getItemCount() > 0
                    && text.contentEquals(current.getItemAt(0).getText());
        } finally {
            restore(snapshot);
        }
    }

    private static Object getClipboardService() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getDeclaredMethod("getService", String.class);
        IBinder binder = (IBinder) getService.invoke(null, "clipboard");
        if (binder == null) throw new IllegalStateException("Clipboard service is unavailable");

        Class<?> stub = Class.forName("android.content.IClipboard$Stub");
        Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
        Object service = asInterface.invoke(null, binder);
        if (service == null) throw new IllegalStateException("Clipboard binder is unavailable");
        return service;
    }

    private static Object invoke(Object service, String name, ClipData clip) throws Exception {
        Method target = null;
        for (Method method : service.getClass().getMethods()) {
            if (method.getName().equals(name)) {
                target = method;
                break;
            }
        }
        if (target == null) throw new NoSuchMethodException(name);

        Class<?>[] types = target.getParameterTypes();
        Object[] arguments = new Object[types.length];
        boolean packageAssigned = false;
        for (int index = 0; index < types.length; index++) {
            Class<?> type = types[index];
            if (ClipData.class.isAssignableFrom(type)) {
                arguments[index] = clip;
            } else if (type == String.class) {
                arguments[index] = packageAssigned ? null : callingPackage();
                packageAssigned = true;
            } else if (type == int.class || type == Integer.class) {
                arguments[index] = 0;
            } else if (type == boolean.class || type == Boolean.class) {
                arguments[index] = false;
            } else {
                arguments[index] = null;
            }
        }
        target.setAccessible(true);
        return target.invoke(service, arguments);
    }

    private static String callingPackage() {
        int uid = Process.myUid();
        if (uid == SYSTEM_UID) return SYSTEM_PACKAGE;
        if (uid == SHELL_UID) return SHELL_PACKAGE;
        throw new SecurityException("Clipboard bridge requires the system or shell UID");
    }

    private static String encodeSnapshot(ClipData clip) {
        if (clip == null) return EMPTY_SNAPSHOT;
        Parcel parcel = Parcel.obtain();
        try {
            clip.writeToParcel(parcel, 0);
            byte[] bytes = parcel.marshall();
            if (bytes.length > MAX_SNAPSHOT_BYTES) {
                throw new IllegalStateException("Clipboard snapshot is too large to preserve safely");
            }
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } finally {
            parcel.recycle();
        }
    }

    private static ClipData decodeSnapshot(String snapshot) {
        byte[] bytes = Base64.decode(snapshot, Base64.NO_WRAP);
        Parcel parcel = Parcel.obtain();
        try {
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);
            return ClipData.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
