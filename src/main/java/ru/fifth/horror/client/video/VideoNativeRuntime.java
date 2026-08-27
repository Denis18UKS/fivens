package ru.fifth.horror.client.video;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.util.Locale;

/** Client-only probe for the bundled Windows x86_64 JavaCV/FFmpeg runtime. */
public final class VideoNativeRuntime {
    private static volatile boolean checked;
    private static volatile boolean available;
    private static volatile String failure = "";

    private VideoNativeRuntime() {}

    public static boolean available() {
        ensureChecked();
        return available;
    }

    public static String failureMessage() {
        ensureChecked();
        return failure;
    }

    private static synchronized void ensureChecked() {
        if (checked) return;
        checked = true;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win") || !(arch.contains("amd64") || arch.contains("x86_64"))) {
            failure = "FFmpeg runtime unavailable: поддерживается Windows x86_64.";
            return;
        }
        try {
            FFmpegFrameGrabber.tryLoad();
            available = true;
        } catch (Throwable error) {
            failure = "FFmpeg runtime unavailable: " + error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : " — " + error.getMessage());
        }
    }
}
