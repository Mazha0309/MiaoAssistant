package com.mazha0309.miaoassistant.privileged;

import android.util.Base64;

import androidx.annotation.Keep;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Entry point launched through app_process under the system or shell UID. */
@Keep
public final class ClipboardShell {
    private static final String RESULT_PREFIX = "MIAO_CLIP:";

    private ClipboardShell() {}

    @Keep
    public static void main(String[] arguments) {
        if (arguments.length != 2) System.exit(2);
        try {
            switch (arguments[0]) {
                case "swap":
                    String text = new String(
                            Base64.decode(arguments[1], Base64.NO_WRAP),
                            StandardCharsets.UTF_8
                    );
                    printResult(ShellClipboardBridge.swap(text));
                    break;
                case "restore":
                    ShellClipboardBridge.restore(arguments[1]);
                    printResult("OK");
                    break;
                case "roundtrip":
                    String probe = new String(
                            Base64.decode(arguments[1], Base64.NO_WRAP),
                            StandardCharsets.UTF_8
                    );
                    if (!ShellClipboardBridge.verifyRoundTrip(probe)) System.exit(1);
                    printResult("OK");
                    break;
                default:
                    System.exit(2);
            }
        } catch (Throwable error) {
            System.err.println("MiaoAssistant clipboard helper failed: " + error.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static void printResult(String value) {
        PrintStream output = new PrintStream(new FileOutputStream(FileDescriptor.out), true);
        output.println(RESULT_PREFIX + value);
        output.flush();
    }
}
