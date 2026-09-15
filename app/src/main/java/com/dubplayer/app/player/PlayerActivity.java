package com.dubplayer.app.player;

import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TrackSelectionDialogBuilder;

import com.bumptech.glide.Glide;
import com.dubplayer.app.R;
import com.google.android.material.chip.Chip;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * MEMORY v2 player (MediaController based):
 * - owns NO ExoPlayer: binds to PlaybackService via MediaController
 * - background audio + notification + PiP actually work
 * - dub/original toggle preserves position + subtitles + speed
 * - subtitle/quality dialogs from real Tracks (TrackSelectionDialogBuilder)
 * - keeps rotation state (position, dub, speed) via onSaveInstanceState
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_DUB = "dubPath";
    public static final String EXTRA_SRT = "srtPath";
    public static final String EXTRA_THUMB = "thumb";

    private static final String[] SPEEDS = {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
    private static final float[] SPEED_V = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    private PlayerView playerView;
    private TextView titleTv, stateTv;
    private Chip chipDub, chipSub, chipQ, chipSpd, chipPip;
    private ImageButton btnBack;

    private ListenableFuture<MediaController> future;
    private MediaController controller;

    private String videoId, title, url, dubPath, srtPath, thumb;
    private boolean usingDub;
    private int speedIdx = 2;
    private boolean controllerReady = false;
    private long pendingPos = C.TIME_UNSET;
    private boolean pendingAutoPlay = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        Intent in = getIntent();
        videoId = in.getStringExtra(EXTRA_ID);
        title = in.getStringExtra(EXTRA_TITLE);
        url = in.getStringExtra(EXTRA_URL);
        dubPath = in.getStringExtra(EXTRA_DUB);
        srtPath = in.getStringExtra(EXTRA_SRT);
        thumb = in.getStringExtra(EXTRA_THUMB);
        if (videoId != null && videoId.startsWith("local:")) {
            url = videoId.substring(6);
            videoId = null;
        }
        usingDub = false;
        if (savedInstanceState != null) {
            usingDub = savedInstanceState.getBoolean("dub", false);
            speedIdx = savedInstanceState.getInt("spd", 2);
            pendingPos = savedInstanceState.getLong("pos", C.TIME_UNSET);
            pendingAutoPlay = savedInstanceState.getBoolean("play", true);
        }
        if (savedInstanceState == null && dubPath != null && new File(dubPath).exists()) {
            // autoplay the Persian dub when it exists
            usingDub = true;
        }

        playerView = findViewById(R.id.playerView);
        titleTv = findViewById(R.id.title);
        stateTv = findViewById(R.id.state);
        chipDub = findViewById(R.id.chipDub);
        chipSub = findViewById(R.id.chipSub);
        chipQ = findViewById(R.id.chipQuality);
        chipSpd = findViewById(R.id.chipSpeed);
        chipPip = findViewById(R.id.chipPip);
        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        titleTv.setText(title != null ? title : "DubPlayer");
        refreshDubChip();
        refreshSpeedChip();

        chipDub.setOnClickListener(v -> {
            File d = dubPath != null ? new File(dubPath) : null;
            if (d == null || !d.exists()) {
                Toast.makeText(this, R.string.no_dub, Toast.LENGTH_SHORT).show();
                return;
            }
            usingDub = !usingDub;
            refreshDubChip();
            swapSource(true);
        });
        chipSub.setOnClickListener(v -> showTrackDialog(C.TRACK_TYPE_TEXT, true));
        chipQ.setOnClickListener(v -> showTrackDialog(C.TRACK_TYPE_VIDEO, false));
        chipSpd.setOnClickListener(v -> {
            speedIdx = (speedIdx + 1) % SPEED_V.length;
            refreshSpeedChip();
            applySpeed();
        });
        chipPip.setOnClickListener(v -> enterPip());
        refreshDubVisibility();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        future = new MediaController.Builder(this, token).buildAsync();
        future.addListener(() -> {
            try {
                controller = future.get();
                controllerReady = true;
                playerView.setPlayer(controller);
                controller.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                                .build(), true);
                controller.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int s) {
                        if (s == Player.STATE_BUFFERING) stateTv.setText(R.string.buffering);
                        else stateTv.setText("");
                    }
                    @Override
                    public void onPlayerError(PlaybackException e) {
                        stateTv.setText("");
                        Toast.makeText(PlayerActivity.this,
                                getString(R.string.error_player, String.valueOf(e.getMessage())),
                                Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onTracksChanged(Tracks tracks) {
                        refreshDubVisibility();
                    }
                });
                applySpeed();
                swapSource(false);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, getString(R.string.error_player, "service"), Toast.LENGTH_LONG).show();
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onStop() {
        if (controller != null) {
            pendingPos = controller.getCurrentPosition();
            pendingAutoPlay = controller.isPlaying();
        }
        playerView.setPlayer(null);
        if (future != null) {
            MediaController.releaseFuture(future);
            future = null;
        }
        controller = null;
        controllerReady = false;
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        long pos = pendingPos;
        boolean play = pendingAutoPlay;
        if (controller != null) {
            try { pos = controller.getCurrentPosition(); play = controller.isPlaying(); } catch (Exception ignored) { }
        }
        out.putBoolean("dub", usingDub);
        out.putInt("spd", speedIdx);
        out.putLong("pos", pos);
        out.putBoolean("play", play);
    }

    // ---------------- source building ----------------
    private MediaItem buildItem() {
        String playUri;
        if (usingDub && dubPath != null && new File(dubPath).exists()) playUri = dubPath;
        else if (url != null) playUri = url;
        else if (dubPath != null) playUri = dubPath;
        else playUri = "";

        MediaItem.Builder b = new MediaItem.Builder()
                .setMediaId(videoId != null ? videoId : playUri)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(title != null ? title : "DubPlayer")
                        .setArtworkUri(thumb != null ? Uri.parse(thumb) : null)
                        .build());
        if (playUri.startsWith("http")) b.setUri(Uri.parse(playUri));
        else if (!playUri.isEmpty()) b.setUri(Uri.fromFile(new File(playUri)));

        // Persian srt sidecar (NOT when playing the muxed dub - it has no external subs need
        // unless user provided srt next to it; we keep subs in both cases when the file exists).
        if (srtPath != null && new File(srtPath).exists()) {
            List<MediaItem.SubtitleConfiguration> subs = new ArrayList<>();
            subs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(new File(srtPath)))
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage("fa")
                    .setLabel(getString(R.string.sub_track))
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build());
            b.setSubtitleConfigurations(subs);
        }
        return b.build();
    }

    private void swapSource(boolean keepPos) {
        if (!controllerReady || controller == null) return;
        long pos = keepPos ? controller.getCurrentPosition() : pendingPos;
        boolean play = keepPos ? controller.isPlaying() : pendingAutoPlay;
        controller.setMediaItem(buildItem(), pos == C.TIME_UNSET ? 0 : pos);
        controller.prepare();
        controller.setPlayWhenReady(play);
        pendingPos = C.TIME_UNSET;
    }

    private void applySpeed() {
        if (controller != null && speedIdx >= 0 && speedIdx < SPEED_V.length) {
            controller.setPlaybackParameters(new PlaybackParameters(SPEED_V[speedIdx]));
        }
    }

    private void refreshSpeedChip() { chipSpd.setText(getString(R.string.speed_fmt, SPEEDS[speedIdx])); }

    private void refreshDubChip() {
        chipDub.setText(usingDub ? R.string.dub_track : R.string.orig_track);
        chipDub.setChecked(usingDub);
    }

    private void refreshDubVisibility() {
        File d = dubPath != null ? new File(dubPath) : null;
        chipDub.setVisibility(d != null && d.exists() ? View.VISIBLE : View.GONE);
    }

    private void showTrackDialog(int trackType, boolean allowOff) {
        if (controller == null) return;
        String dlgTitle = trackType == C.TRACK_TYPE_TEXT
                ? getString(R.string.subtitle_track) : getString(R.string.quality);
        TrackSelectionDialogBuilder b =
                new TrackSelectionDialogBuilder(this, dlgTitle, controller, trackType);
        b.setShowDisableOption(allowOff);
        b.build().show();
    }

    // ---------------- PiP ----------------
    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PictureInPictureParams p = new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9)).build();
                enterPictureInPictureMode(p);
            } catch (Exception e) {
                Toast.makeText(this, R.string.no_pip, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.no_pip, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && controller != null && controller.isPlaying()) {
            enterPip();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip,
            android.content.res.Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        findViewById(R.id.belowPlayer).setVisibility(inPip ? View.GONE : View.VISIBLE);
        if (inPip) playerView.setUseController(false);
        else playerView.setUseController(true);
    }
}
