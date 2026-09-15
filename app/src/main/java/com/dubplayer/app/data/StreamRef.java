package com.dubplayer.app.data;

import androidx.annotation.Nullable;

/**
 * Resolved remote playback target. One of:
 *  single: one url (muxed mp4 / hls / dash) + its mime
 *  merged: videoUrl + audioUrl (DASH-like adaptive pair, non- muxed)
 */
public final class StreamRef {
    @Nullable public final String single;
    @Nullable public final String singleMime; // "hls" | "dash" | null(=mp4)
    @Nullable public final String videoUrl;
    @Nullable public final String audioUrl;
    public final String title;
    public final String author;

    public StreamRef(String single, String singleMime, String videoUrl, String audioUrl,
                     String title, String author) {
        this.single = single;
        this.singleMime = singleMime;
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.title = title != null ? title : "";
        this.author = author != null ? author : "";
    }

    public boolean isMerged() { return single == null && videoUrl != null && audioUrl != null; }
    public boolean isSingle() { return single != null; }
    public boolean playable() { return isSingle() || isMerged(); }
}
