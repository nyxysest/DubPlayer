package com.dubplayer.app.player;

import com.dubplayer.app.data.Library;
import com.dubplayer.app.data.VideoItem;

import java.io.File;
import java.util.Locale;

/** Finds local Persian dub audio and .srt files produced by yt-dubber. */
public final class DubSubManager {
    private DubSubManager() {}

    public static File folderFor(String ytId) { return Library.folderFor(ytId); }
    public static File dubFile(String ytId) { return new File(folderFor(ytId), "dubbed.mp4"); }
    public static File srcFile(String ytId) { return new File(folderFor(ytId), "source.mp4"); }

    /** Local srt if present (prefers subtitles.fa.srt). Never null, check exists(). */
    public static File srtFile(String ytId) {
        File d = folderFor(ytId);
        File exact = new File(d, "subtitles.fa.srt");
        if (exact.exists()) return exact;
        File[] all = d.listFiles((dir, n) -> n.toLowerCase(Locale.US).endsWith(".srt"));
        if (all != null) {
            for (File f : all) {
                if (f.getName().toLowerCase(Locale.US).endsWith(".fa.srt")) return f;
            }
            if (all.length > 0) return all[0];
        }
        return exact;
    }

    /** Local VideoItem if a dub or source file exists, else null. */
    public static VideoItem itemFor(String ytId) {
        File dub = dubFile(ytId), src = srcFile(ytId), srt = srtFile(ytId);
        File pick = dub.exists() ? dub : (src.exists() ? src : null);
        if (pick == null) return null;
        boolean hasSrt = srt.exists();
        return new VideoItem("local:" + pick.getAbsolutePath(), ytId,
                dub.exists() ? "\u062F\u0648\u0628\u0644\u0647 \u0641\u0627\u0631\u0633\u06CC" : "\u0635\u062F\u0627\u06CC \u0627\u0635\u0644\u06CC",
                null, 0, dub.exists(), hasSrt,
                dub.exists() ? dub.getAbsolutePath() : null,
                hasSrt ? srt.getAbsolutePath() : null);
    }
}
