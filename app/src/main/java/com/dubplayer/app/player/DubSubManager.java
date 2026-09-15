package com.dubplayer.app.player;

import android.content.Context;

import com.dubplayer.app.data.Library;
import com.dubplayer.app.data.VideoItem;

import java.io.File;

/** Finds Persian dub audio and .fa.srt files for a given video. */
public final class DubSubManager {
    /** Where yt-dubber writes its output: /sdcard/DubPlayer/<id>/dubbed.mp4 */
    public static File folderFor(String ytId) { return Library.folderFor(ytId); }

    public static File dubFile(String ytId)  { return new File(folderFor(ytId), "dubbed.mp4"); }
    public static File srtFile(String ytId)  { return new File(folderFor(ytId), "subtitles.fa.srt"); }

    public static VideoItem itemFor(String ytId) {
        File dub = dubFile(ytId), srt = srtFile(ytId), src = new File(folderFor(ytId), "source.mp4");
        File pick = dub.exists() ? dub : (src.exists() ? src : null);
        if (pick == null) return null;
        return new VideoItem("local:" + pick.getAbsolutePath(), ytId,
                dub.exists() ? "دوبلهٔ فارسی" : "صدای اصلی", null,
                0, dub.exists(), srt.exists(),
                dub.exists() ? dub.getAbsolutePath() : null,
                srt.exists() ? srt.getAbsolutePath() : null);
    }
    public static File srcFile(String ytId) { return new File(folderFor(ytId), "source.mp4"); }
}
