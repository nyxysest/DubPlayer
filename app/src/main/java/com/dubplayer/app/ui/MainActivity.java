package com.dubplayer.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.dubplayer.app.R;
import com.dubplayer.app.data.Library;
import com.dubplayer.app.data.VideoItem;
import com.dubplayer.app.data.YtSearch;
import com.dubplayer.app.player.PlayerActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * MEMORY v2 - YouTube-style home:
 * - top search bar (query OR YouTube link)
 * - grid of results from Piped/Invidious
 * - Library tab = local /sdcard/DubPlayer dubbed files
 * - Downloads tab reserved for future manager
 */
public class MainActivity extends AppCompatActivity {
    private final List<VideoItem> items = new ArrayList<>();
    private VideoAdapter adapter;
    private EditText searchBox;
    private ProgressBar progress;
    private SwipeRefreshLayout swipe;
    private TextView emptyView;
    private final Executor bg = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile long querySeq = 0;
    private String mode = "search"; // search | library

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchBox = findViewById(R.id.searchBox);
        progress = findViewById(R.id.progress);
        swipe = findViewById(R.id.swipe);
        emptyView = findViewById(R.id.emptyView);

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setHasFixedSize(true);
        adapter = new VideoAdapter(items, this::openPlayer);
        rv.setAdapter(adapter);

        ImageButton btnSearch = findViewById(R.id.btnSearch);
        btnSearch.setOnClickListener(v -> runQuery());
        searchBox.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runQuery(); return true; }
            return false;
        });

        swipe.setOnRefreshListener(this::reloadCurrent);

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) { showSearch(); return true; }
            if (id == R.id.nav_library) { showLibrary(); return true; }
            if (id == R.id.nav_downloads) { showDownloads(); return true; }
            return true;
        });

        handleIncomingLink(getIntent());
        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_home);
            runQueryDefault();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingLink(intent);
    }

    private void handleIncomingLink(Intent in) {
        if (in == null) return;
        String text = in.getStringExtra(Intent.EXTRA_TEXT);
        Uri data = in.getData();
        String cand = text != null ? text : (data != null ? data.toString() : null);
        if (cand == null) return;
        String id = YtSearch.extractId(cand);
        if (id != null) {
            searchBox.setText("https://www.youtube.com/watch?v=" + id);
            openById(id, null);
        } else {
            searchBox.setText(cand);
            runQuery();
        }
    }

    private void runQueryDefault() {
        searchBox.setText("lofi hip hop");
        runQuery();
    }

    private void showSearch() {
        mode = "search";
        emptyView.setVisibility(View.GONE);
        runQuery();
    }

    private void showLibrary() {
        mode = "library";
        swipe.setRefreshing(true);
        bg.execute(() -> {
            List<VideoItem> local = Library.scan();
            main.post(() -> {
                swipe.setRefreshing(false);
                items.clear();
                items.addAll(local);
                adapter.notifyDataSetChanged();
                emptyView.setVisibility(local.isEmpty() ? View.VISIBLE : View.GONE);
                if (local.isEmpty()) emptyView.setText(R.string.empty_library);
            });
        });
    }

    private void showDownloads() {
        mode = "downloads";
        items.clear();
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(R.string.empty_downloads);
    }

    private void reloadCurrent() {
        if ("library".equals(mode)) showLibrary();
        else if ("search".equals(mode)) runQuery();
        else swipe.setRefreshing(false);
    }

    private void runQuery() {
        String q = searchBox.getText().toString().trim();
        if (q.isEmpty()) { runQueryDefault(); return; }
        String id = YtSearch.extractId(q);
        if (id != null) { openById(id, null); return; }
        mode = "search";
        final long seq = ++querySeq;
        progress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        final String query = q;
        bg.execute(() -> {
            try {
                List<VideoItem> res = YtSearch.search(query);
                main.post(() -> {
                    if (seq != querySeq) return;
                    progress.setVisibility(View.GONE);
                    swipe.setRefreshing(false);
                    items.clear();
                    items.addAll(res);
                    adapter.notifyDataSetChanged();
                    emptyView.setVisibility(res.isEmpty() ? View.VISIBLE : View.GONE);
                    if (res.isEmpty()) emptyView.setText(R.string.empty_search);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (seq != querySeq) return;
                    progress.setVisibility(View.GONE);
                    swipe.setRefreshing(false);
                    Toast.makeText(MainActivity.this, getString(R.string.error_search, String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
                    if (items.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        emptyView.setText(R.string.empty_search);
                    }
                });
            }
        });
    }

    /** Direct open: local dub first, else resolve stream, then PlayerActivity. */
    private void openById(final String ytId, final VideoItem seed) {
        progress.setVisibility(View.VISIBLE);
        bg.execute(() -> {
            VideoItem local = com.dubplayer.app.player.DubSubManager.itemFor(ytId);
            String title = seed != null && seed.title != null ? seed.title : ytId;
            String channel = seed != null && seed.channel != null ? seed.channel : "";
            String thumb = seed != null ? seed.thumbUrl : null;
            if (local != null) {
                VideoItem merged = new VideoItem(local.id, title, channel, thumb,
                        seed != null ? seed.durationMs : 0,
                        true, local.hasSubs, local.dubPath, local.srtPath, null);
                main.post(() -> { progress.setVisibility(View.GONE); openPlayer(merged); });
                return;
            }
            String[] meta = YtSearch.fetchMeta(ytId);
            String stream = YtSearch.resolveStream(ytId);
            final String fTitle = meta[0] != null ? meta[0] : title;
            final String fChannel = meta[1] != null ? meta[1] : channel;
            if (stream == null) {
                main.post(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, R.string.error_stream, Toast.LENGTH_LONG).show();
                });
                return;
            }
            VideoItem remote = new VideoItem(ytId, fTitle, fChannel, thumb, 0, false, false, null, null, stream);
            main.post(() -> { progress.setVisibility(View.GONE); openPlayer(remote); });
        });
    }

    private void openPlayer(VideoItem v) {
        // resolve stream lazily on click if needed
        if (!v.isLocal() && v.streamUrl == null && v.ytId() != null) {
            openById(v.ytId(), v);
            return;
        }
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_ID, v.id);
        i.putExtra(PlayerActivity.EXTRA_TITLE, v.title);
        i.putExtra(PlayerActivity.EXTRA_URL, v.streamUrl);
        i.putExtra(PlayerActivity.EXTRA_DUB, v.dubPath);
        i.putExtra(PlayerActivity.EXTRA_SRT, v.srtPath);
        i.putExtra(PlayerActivity.EXTRA_THUMB, v.thumbUrl);
        startActivity(i);
    }

    // ---------------- adapter ----------------
    interface OnOpen { void open(VideoItem v); }

    static class VideoAdapter extends RecyclerView.Adapter<VH> {
        private final List<VideoItem> data;
        private final OnOpen onOpen;
        VideoAdapter(List<VideoItem> d, OnOpen o) { data = d; onOpen = o; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            VideoItem it = data.get(pos);
            h.title.setText(it.title != null ? it.title : "");
            h.channel.setText(it.channel != null ? it.channel : "");
            h.duration.setVisibility(View.GONE);
            if (!TextUtils.isEmpty(it.thumbUrl)) {
                h.thumb.setImageDrawable(null);
                Glide.with(h.thumb).load(it.thumbUrl).centerCrop().into(h.thumb);
            } else {
                Glide.with(h.thumb).clear(h.thumb);
                h.thumb.setImageResource(android.R.drawable.ic_media_play);
            }
            h.badgeDub.setVisibility(it.hasDub ? View.VISIBLE : View.GONE);
            h.badgeSub.setVisibility(it.hasSubs ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> onOpen.open(it));
        }
        @Override public int getItemCount() { return data.size(); }
        @Override public long getItemId(int p) { return data.get(p).id.hashCode(); }
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title, channel, duration;
        final View badgeDub, badgeSub;
        VH(View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.vTitle);
            channel = v.findViewById(R.id.vChannel);
            duration = v.findViewById(R.id.vDuration);
            badgeDub = v.findViewById(R.id.badgeDub);
            badgeSub = v.findViewById(R.id.badgeSub);
        }
    }
}
