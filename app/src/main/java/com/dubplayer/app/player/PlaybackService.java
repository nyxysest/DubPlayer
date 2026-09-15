package com.dubplayer.app.player;

import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;

import com.dubplayer.app.data.YtSearch;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Single owner of the ExoPlayer instance. UI connects via MediaController.
 * Custom MediaSourceFactory handles:
 *  - merged videoOnly+audio (MergingMediaSource, same-period)
 *  - hls / dash / progressive chosen by StreamRef mime
 * Browser UA on every request (Piped proxy returns 403 for unknown agents).
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackService extends MediaSessionService {
    private MediaSession session;

    @Override
    public void onCreate() {
        super.onCreate();

        OkHttpClient ok = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        OkHttpDataSource.Factory okHttp = new OkHttpDataSource.Factory(ok)
                .setUserAgent(YtSearch.UA)
                .setDefaultRequestProperties(java.util.Collections.singletonMap("Referer", "https://piped.video/"));
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(YtSearch.UA)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true);

        DefaultMediaSourceFactory base = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(okHttp);

        ExoPlayer player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new MergeAwareFactory(base, okHttp, http))
                .build();
        session = new MediaSession.Builder(this, player).build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Player p = session.getPlayer();
        if (!p.getPlayWhenReady() || p.getPlaybackState() == Player.STATE_ENDED) stopSelf();
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    /**
     * MediaItem with playback-properties tag = int mode:
     *  0 = default (single url, auto-detect)
     *  1 = force HLS single
     *  2 = force DASH single
     *  3 = merged: uri=video, tag extra audio url in mediaMetadata.extras ("audioUrl")
     */
    static final class MergeAwareFactory implements androidx.media3.exoplayer.source.MediaSource.Factory {
        private final DefaultMediaSourceFactory base;
        private final OkHttpDataSource.Factory okHttp;
        private final DefaultHttpDataSource.Factory http;

        MergeAwareFactory(DefaultMediaSourceFactory b, OkHttpDataSource.Factory o,
                          DefaultHttpDataSource.Factory h) {
            base = b; okHttp = o; http = h;
        }

        @Override
        public MergeAwareFactory setDrmSessionManagerProvider(
                androidx.media3.exoplayer.drm.DrmSessionManagerProvider p) { base.setDrmSessionManagerProvider(p); return this; }
        @Override
        public MergeAwareFactory setLoadErrorHandlingPolicy(
                androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy p) { base.setLoadErrorHandlingPolicy(p); return this; }
        @Override
        public int[] getSupportedTypes() { return base.getSupportedTypes(); }

        @Override
        public MediaSource createMediaSource(MediaItem item) {
            Object tag = item.playbackProperties != null ? item.playbackProperties.tag : null;
            int mode = (tag instanceof Integer) ? (Integer) tag : 0;
            String audioUrl = null;
            if (item.playbackProperties != null && item.playbackProperties.tag instanceof Object[]) {
                Object[] arr = (Object[]) item.playbackProperties.tag;
                if (arr.length == 2 && arr[0] instanceof Integer && arr[1] instanceof String) {
                    mode = (Integer) arr[0];
                    audioUrl = (String) arr[1];
                }
            }
            if (mode == 3 && audioUrl != null) {
                MediaItem v = item.buildUpon().setUri(item.playbackProperties.uri).build();
                MediaItem a = new MediaItem.Builder()
                        .setUri(android.net.Uri.parse(audioUrl))
                        .setMimeType(MimeTypes.AUDIO_MP4)
                        .build();
                MediaSource vs = new ProgressiveMediaSource.Factory(okHttp).createMediaSource(v);
                MediaSource as = new ProgressiveMediaSource.Factory(okHttp).createMediaSource(a);
                return new MergingMediaSource(true, vs, as);
            }
            if (mode == 1) {
                return new HlsMediaSource.Factory(http).createMediaSource(item);
            }
            if (mode == 2) {
                return new DashMediaSource.Factory(http).createMediaSource(item);
            }
            return base.createMediaSource(item);
        }
    }
}
