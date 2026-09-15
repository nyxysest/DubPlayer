package com.dubplayer.app.data;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Local library filled by yt-dubber (via Termux) at /sdcard/DubPlayer/<id>/ :
 * dubbed.mp4 + subtitles.fa.srt (+ optional source.mp4).
 * NOTE: scan() does disk I/O - always call it off the main thread.
 */
public final class Library {
    private Library() {}

    public static File root() {
        File primary = new File(Environment.getExternalStorageDirectory(), "DubPlayer");
        if (primary.isDirectory()) return primary;
        File legacy = new File("/sdcard/DubPlayer");
        return legacy.isDirectory() ? legacy : primary;
    }

    public static File folderFor(String ytId) {
        return new File(root(), ytId.replaceAll("[^A-Za-z0-9_-]", "_"));
    }

    /** Newest folders first. */
    public static List<VideoItem> scan() {
        List<VideoItem> out = new ArrayList<>();
        File[] dirs = root().listFiles(File::isDirectory);
        if (dirs == null) return out;
        Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File d : dirs) {
            File dub = new File(d, "dubbed.mp4");
            File src = new File(d, "source.mp4");
            if (!src.exists()) src = firstVideo(d);
            File srt = persianSrt(d);
            File pick = dub.exists() ? dub : (src != null && src.exists() ? src : null);
            if (pick == null) continue;
            out.add(new VideoItem("local:" + pick.getAbsolutePath(), d.getName(),
                    dub.exists() ? "\u062F\u0648\u0628\u0644\u0647 \u0641\u0627\u0631\u0633\u06CC" : "\u0635\u062F\u0627\u06CC \u0627\u0635\u0644\u06CC",
                    null, 0, dub.exists(), srt != null,
                    dub.exists() ? dub.getAbsolutePath() : null,
                    srt != null ? srt.getAbsolutePath() : null));
        }
        return out;
    }

    private static File firstVideo(File dir) {
        File[] vids = dir.listFiles((d, n) -> n.toLowerCase(Locale.US).endsWith(".mp4"));
        return (vids != null && vids.length > 0) ? vids[0] : null;
    }

    static File persianSrt(File dir) {
        File exact = new File(dir, "subtitles.fa.srt");
        if (exact.exists()) return exact;
        File[] all = dir.listFiles((d, n) -> n.toLowerCase(Locale.US).endsWith(".srt"));
        if (all != null) {
            for (File f : all) {
                if (f.getName().toLowerCase(Locale.US).endsWith(".fa.srt")) return f;
            }
            if (all.length > 0) return all[0];
        }
        return exact;
    }
}
