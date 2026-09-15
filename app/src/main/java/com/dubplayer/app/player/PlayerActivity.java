package com.dubplayer.app.player;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaItem.SubtitleConfiguration;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TrackSelectionDialogBuilder;

import com.dubplayer.app.R;
import com.google.android.material.chip.Chip;

import java.io.File;
import java.util.List;

/**
 * Full-featured player: Persian dub audio switching, Persian SRT subtitles,
 * quality/speed/dialog tracks, background audio via PlaybackService and PiP.
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_ID    = "id";       // yt id or local:<path>
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL   = "url";      // direct/hls url if remote
    public static final String EXTRA_DUB   = "dubPath";  // local dubbed.mp4
    public static final String EXTRA_SRT   = "srtPath";  // local subtitles.fa.srt

    private ExoPlayer player;
    private PlayerView playerView;
    private String videoId, title, url, dubPath, srtPath;
    private boolean usingDub = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        Intent in = getIntent();
        videoId = in.getStringExtra(EXTRA_ID);
        title   = in.getStringExtra(EXTRA_TITLE);
        url     = in.getStringExtra(EXTRA_URL);
        dubPath = in.getStringExtra(EXTRA_DUB);
        srtPath = in.getStringExtra(EXTRA_SRT);
        if (videoId != null && videoId.startsWith("local:")) {
            url = videoId.substring(6);
            videoId = null;
        }

        playerView = findViewById(R.id.playerView);
        TextView titleTv = findViewById(R.id.title);
        titleTv.setText(title == null ? "ویدیو" : title);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);

        player.setMediaItem(buildMediaItem(false));
        player.prepare();
        player.play();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        setupControls();
    }

    /** Builds the media item: remote stream or local file, + Persian SRT sidecar. */
    private MediaItem buildMediaItem(boolean withDub) {
        MediaItem.Builder b = new MediaItem.Builder()
                .setMediaId(videoId != null ? videoId : String.valueOf(url))
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build());

        if (url != null && url.startsWith("http")) {
            b.setUri(Uri.parse(url));
        } else if (url != null) {
            b.setUri(Uri.fromFile(new File(url)));
        }
        if (srtPath != null && new File(srtPath).exists()) {
            b.setSubtitleConfigurations(List.of(
                    SubtitleConfiguration.Builder(Uri.fromFile(new File(srtPath)))
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage("fa")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()));
        }
        return b.build();
    }

    /** Toggle main (source) audio vs Persian dub file, keeping position. */
    private void switchToDub(boolean dub) {
        if (usingDub == dub) return;
        long pos = player.getCurrentPosition();
        boolean wasPlaying = player.isPlaying();
        usingDub = dub;

        MediaItem item;
        if (dub && dubPath != null && new File(dubPath).exists()) {
            // Local dubbed file replaces the remote stream entirely (new audio + video muxed)
            item = MediaItem.fromUri(Uri.fromFile(new File(dubPath)));
        } else {
            item = buildMediaItem(false);
        }
        player.setMediaItem(item, pos);
        player.prepare();
        if (wasPlaying) player.play();
    }

    private void setupControls() {
        Chip chipDub  = findViewById(R.id.chipDub);
        Chip chipSub  = findViewById(R.id.chipSub);
        Chip chipQ    = findViewById(R.id.chipQuality);
        Chip chipSpd  = findViewById(R.id.chipSpeed);
        Chip chipPip  = findViewById(R.id.chipPip);

        boolean dubAvailable = dubPath != null && new File(dubPath).exists();
        chipDub.setVisibility(dubAvailable ? View.VISIBLE : View.GONE);
        chipDub.setOnClickListener(v -> {
            usingDub = !usingDub;
            switchToDub(usingDub);
            chipDub.setText(usingDub ? R.string.dub_track : R.string.orig_track);
        });

        chipSub.setOnClickListener(v -> {
            if (player == null) return;
            TrackSelectionDialogBuilder b = new TrackSelectionDialogBuilder(
                    this, getString(R.string.subtitle_track), player, C.TRACK_TYPE_TEXT);
            b.setShowDisableOption(true).build().show();
        });

        chipQ.setOnClickListener(v -> {
            if (player == null) return;
            TrackSelectionDialogBuilder b = new TrackSelectionDialogBuilder(
                    this, getString(R.string.quality), player, C.TRACK_TYPE_VIDEO);
            b.build().show();
        });

        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        chipSpd.setOnClickListener(v -> {
            if (player == null) return;
            float cur = player.getPlaybackParameters().speed;
            float next = speeds[0];
            for (float s : speeds) { if (s > cur + 0.01f) { next = s; break; } }
            player.setPlaybackParameters(new PlaybackParameters(next));
            chipSpd.setText("سرعت ×" + next);
        });

        chipPip.setOnClickListener(v -> enterPip());
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams p = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9)).build();
            try { enterPictureInPictureMode(p); } catch (Exception e) {
                Toast.makeText(this, "PiP در دسترس نیست", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player != null && player.isPlaying()) {
            enterPip();
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
