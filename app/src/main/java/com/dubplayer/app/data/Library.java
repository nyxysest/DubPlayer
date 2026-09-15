package com.dubplayer.app.data;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Scans the app-private folder that yt-dubber fills over termux-setup-storage
 * or adb: /sdcard/DubPlayer/<video id>/{dubbed.mp4, subtitles.fa.srt, ...}
 * Everything found there becomes a playable row - dubbed audio and Persian
 * subtitles are picked up automatically when present.
 */
public final class Library {
    public static final File ROOT = new File("/sdcard/DubPlayer");

    public static List<VideoItem> scan(Context ctx) {
        List<VideoItem> out = new ArrayList<>();
        File[] dirs = ROOT.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File d : dirs) {
                File dub = new File(d, "dubbed.mp4");
                File src = new File(d, "source.mp4");
                File srt = new File(d, "subtitles.fa.srt");
                File pick = dub.exists() ? dub : (src.exists() ? src : null);
                if (pick == null) continue;
                out.add(new VideoItem(
                        "local:" + pick.getAbsolutePath(),
                        d.getName(),
                        dub.exists() ? "دوبلهٔ فارسی" : "صدای اصلی",
                        null,
                        0,
                        dub.exists(), srt.exists(),
                        dub.exists() ? dub.getAbsolutePath() : null,
                        srt.exists() ? srt.getAbsolutePath() : null));
            }
        }
        Collections.sort(out, (a, b) -> b.title.compareToIgnoreCase(a.title));
        return out;
    }

    /** Folder where yt-dubber should place output for this video id. */
    public static File folderFor(String ytId) {
        return new File(ROOT, ytId.replaceAll("[^A-Za-z0-9_-]", "_"));
    }

    public static boolean isPersianSrt(File f) {
        return f.getName().toLowerCase(Locale.US).endsWith(".fa.srt");
    }
}
