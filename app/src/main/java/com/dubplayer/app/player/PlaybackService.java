package com.dubplayer.app.player;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/** Foreground playback so audio survives backgrounding / screen-off. */
public class PlaybackService extends MediaSessionService {
    private MediaSession session;

    @Override
    public void onCreate() {
        super.onCreate();
        ExoPlayer player = new ExoPlayer.Builder(this).build();
        session = new MediaSession.Builder(this, player).build();
    }
    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (session != null) {
            MediaItem mi = i != null ? i.getParcelableExtra("mi") : null;
            if (mi != null) {
                session.getPlayer().setMediaItem(mi);
                session.getPlayer().prepare();
                session.getPlayer().play();
            }
        }
        return super.onStartCommand(i, flags, startId);
    }
    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo info) { return session; }
    @Override
    public void onDestroy() {
        if (session != null) {
            session.getPlayer().release();
            session.release();
        }
        super.onDestroy();
    }
}
