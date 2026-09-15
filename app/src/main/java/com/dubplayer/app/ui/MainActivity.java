package com.dubplayer.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.dubplayer.app.R;
import com.dubplayer.app.data.Library;
import com.dubplayer.app.data.VideoItem;
import com.dubplayer.app.data.YtSearch;
import com.dubplayer.app.player.PlayerActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * YouTube-like home: search bar on top, video grid below.
 * Tabs: Home (search + results), Library (local dubbed videos), Settings.
 */
public class MainActivity extends AppCompatActivity {
    private final List<VideoItem> items = new ArrayList<>();
    private VideoAdapter adapter;
    private boolean showingLocal = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VideoAdapter(items);
        rv.setAdapter(adapter);

        EditText search = findViewById(R.id.searchBox);
        search.setOnEditorActionListener((v, actionId, ev) -> { doSearch(); return true; });
        findViewById(R.id.btnSearch).setOnClickListener(v -> doSearch());

        ExtendedFloatingActionButton fab = findViewById(R.id.fabLocal);
        fab.setOnClickListener(v -> showLocal());

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home)   { doSearch(); return true; }
            if (id == R.id.nav_library){ showLocal(); return true; }
            return true;
        });

        // Handle shared / opened YouTube links
        Intent in = getIntent();
        String shared = in.getStringExtra(Intent.EXTRA_TEXT);
        String action = in.getAction();
        Uri data = in.getData();
        if (shared != null) {
            String id = YtSearch.extractId(shared);
            if (id != null) { search.setText(id); doSearch(); }
        } else if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String id = YtSearch.extractId(data.toString());
            if (id != null) { search.setText(id); doSearch(); }
        }

        if (savedInstanceState == null) showLocal();  // start with local library (offline-first)
    }

    private void doSearch() {
        EditText search = findViewById(R.id.searchBox);
        String q = search.getText().toString().trim();
        if (q.isEmpty()) { showLocal(); return; }
        String ytId = YtSearch.extractId(q);
        if (ytId != null) {
            // Direct link: open player immediately (yt-dubber provides dub/srt files)
            com.dubplayer.app.player.DubSubManager mgr = new com.dubplayer.app.player.DubSubManager();
            VideoItem local = com.dubplayer.app.player.DubSubManager.itemFor(ytId);
            openVideo(local != null ? local : new VideoItem(ytId, q, "YouTube", null, 0, false, false, null, null));
            return;
        }
        Toast.makeText(this, "در حال جستجو…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<VideoItem> res = YtSearch.search(this, q);
                runOnUiThread(() -> { items.clear(); items.addAll(res); showingLocal = false; adapter.notifyDataSetChanged(); });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "خطا: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showLocal() {
        List<VideoItem> local = Library.scan(this);
        items.clear();
        items.addAll(local);
        showingLocal = true;
        adapter.notifyDataSetChanged();
        if (local.isEmpty()) {
            Toast.makeText(this, R.string.empty_library, Toast.LENGTH_LONG).show();
        }
    }

    private void openVideo(VideoItem v) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_ID, v.id);
        i.putExtra(PlayerActivity.EXTRA_TITLE, v.title);
        i.putExtra(PlayerActivity.EXTRA_DUB, v.dubPath);
        i.putExtra(PlayerActivity.EXTRA_SRT, v.srtPath);
        startActivity(i);
    }

    static class VideoAdapter extends RecyclerView.Adapter<VH> {
        private final List<VideoItem> data;
        VideoAdapter(List<VideoItem> d) { data = d; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            VideoItem it = data.get(pos);
            h.title.setText(it.title);
            h.channel.setText(it.channel);
            if (it.thumbUrl != null) {
                Glide.with(h.thumb).load(it.thumbUrl).into(h.thumb);
            } else {
                h.thumb.setImageResource(android.R.drawable.ic_media_play);
            }
            h.badgeDub.setVisibility(it.hasDub ? View.VISIBLE : View.GONE);
            h.badgeSub.setVisibility(it.hasSubs ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(v.getContext(), PlayerActivity.class);
                i.putExtra(PlayerActivity.EXTRA_ID, it.id);
                i.putExtra(PlayerActivity.EXTRA_TITLE, it.title);
                i.putExtra(PlayerActivity.EXTRA_URL, it.thumbUrl == null && it.id.startsWith("local:") ? it.id.substring(6) : null);
                i.putExtra(PlayerActivity.EXTRA_DUB, it.dubPath);
                i.putExtra(PlayerActivity.EXTRA_SRT, it.srtPath);
                v.getContext().startActivity(i);
            });
        }
        @Override public int getItemCount() { return data.size(); }
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb; final TextView title, channel; final View badgeDub, badgeSub;
        VH(View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.vTitle);
            channel = v.findViewById(R.id.vChannel);
            badgeDub = v.findViewById(R.id.badgeDub);
            badgeSub = v.findViewById(R.id.badgeSub);
        }
    }
}
