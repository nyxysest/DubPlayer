package com.dubplayer.app.data;

import androidx.annotation.Nullable;

/** One row in home/library lists. Local videos use id "local:<abs path>". */
public class VideoItem {
    public final String id;
    public final String title;
    public final String channel;
    public final String thumbUrl;
    public final long durationMs;
    public final boolean hasDub;
    public final boolean hasSubs;
    public final String dubPath;
    public final String srtPath;
    // remote playback: either single (muxed mp4 / hls / dash) or video+audio pair
    public final String streamUrl;
    public final String videoUrl;
    public final String audioUrl;
    public final String streamMime; // M3U8 / MPD for single, else null

    public VideoItem(String id, String title, String channel, String thumbUrl,
                     long durationMs, boolean hasDub, boolean hasSubs,
                     String dubPath, String srtPath,
                     String streamUrl, String videoUrl, String audioUrl, String streamMime) {
        this.id = id;
        this.title = title;
        this.channel = channel;
        this.thumbUrl = thumbUrl;
        this.durationMs = durationMs;
        this.hasDub = hasDub;
        this.hasSubs = hasSubs;
        this.dubPath = dubPath;
        this.srtPath = srtPath;
        this.streamUrl = streamUrl;
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.streamMime = streamMime;
    }

    public VideoItem(String id, String title, String channel, String thumbUrl,
                     long durationMs, boolean hasDub, boolean hasSubs,
                     String dubPath, String srtPath) {
        this(id, title, channel, thumbUrl, durationMs, hasDub, hasSubs,
                dubPath, srtPath, null, null, null, null);
    }

    public boolean isLocal() { return id != null && id.startsWith("local:"); }

    @Nullable
    public String localPath() { return isLocal() ? id.substring(6) : null; }

    @Nullable
    public String ytId() { return isLocal() ? null : id; }

    public boolean hasPlayable() {
        return isLocal() || streamUrl != null || (videoUrl != null && audioUrl != null);
    }
}
