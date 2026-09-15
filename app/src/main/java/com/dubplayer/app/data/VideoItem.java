package com.dubplayer.app.data;

import androidx.annotation.Nullable;

/** One row in the home/library lists. */
public class VideoItem {
    public final String id;          // YouTube id or "local:<path>"
    public final String title;
    public final String channel;
    public final String thumbUrl;    // may be null for local files
    public final long durationMs;
    public final boolean hasDub;     // Persian dub file exists
    public final boolean hasSubs;    // Persian .srt exists
    public final String dubPath;     // absolute path of dubbed.mp4 (may be null)
    public final String srtPath;     // absolute path of subtitles .srt (may be null)

    public VideoItem(String id, String title, String channel, String thumbUrl,
                     long durationMs, boolean hasDub, boolean hasSubs,
                     String dubPath, String srtPath) {
        this.id = id;
        this.title = title;
        this.channel = channel;
        this.thumbUrl = thumbUrl;
        this.durationMs = durationMs;
        this.hasDub = hasDub;
        this.hasSubs = hasSubs;
        this.dubPath = dubPath;
        this.srtPath = srtPath;
    }

    @Nullable
    public String ytId() {
        return id.startsWith("local:") ? null : id;
    }
}
