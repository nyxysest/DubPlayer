package com.dubplayer.app.data;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Search a YouTube clone API (Piped first, Invidious fallback).
 * Users can plug their own API key / instance in Settings.
 */
public final class YtSearch {
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    /** Extract 11-char YouTube id from most URL shapes or a raw id. */
    public static String extractId(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.matches("[A-Za-z0-9_-]{11}")) return s;
        try {
            Uri u = Uri.parse(s);
            String h = u.getHost() == null ? "" : u.getHost().toLowerCase();
            if (h.contains("youtu.be")) {
                String p = u.getPath();
                return p != null && p.length() >= 2 ? p.substring(1) : null;
            }
            if (h.contains("youtube.com")) {
                String v = u.getQueryParameter("v");
                if (v != null && v.matches("[A-Za-z0-9_-]{11}")) return v;
                // /shorts/<id>, /embed/<id>, /live/<id>
                for (String seg : new String[]{"shorts", "embed", "live"}) {
                    List<String> pp = u.getPathSegments();
                    for (int i = 0; i < pp.size() - 1; i++) {
                        if (seg.equals(pp.get(i)) && pp.get(i + 1).matches("[A-Za-z0-9_-]{11}"))
                            return pp.get(i + 1);
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Returns list of results, or throws IOException with the last error. */
    public static List<VideoItem> search(Context ctx, String q) throws IOException {
        List<VideoItem> out = new ArrayList<>();
        String enc = URLEncoder.encode(q, "UTF-8");
        Exception last = null;
        String[] pipedHosts = {
                "https://pipedapi.kavin.rocks",
                "https://pipedapi.adminforge.de",
                "https://api.piped.private.coffee"
        };
        for (String host : pipedHosts) {
            try {
                Request req = new Request.Builder().url(host + "/search?q=" + enc + "&filter=videos").build();
                try (Response r = HTTP.newCall(req).execute()) {
                    if (!r.isSuccessful() || r.body() == null) continue;
                    JSONArray arr = new JSONObject(r.body().string()).getJSONArray("items");
                    for (int i = 0; i < arr.length() && out.size() < 30; i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String url = o.optString("url", "");
                        String id = extractId(url);
                        if (id == null) continue;
                        out.add(new VideoItem(id,
                                o.optString("title", ""),
                                o.optString("uploaderName", ""),
                                o.optString("thumbnail", null),
                                (long) (o.optDouble("duration", 0) * 1000),
                                false, false, null, null));
                    }
                    if (!out.isEmpty()) return out;
                }
            } catch (Exception e) { last = e; }
        }
        // Invidious fallback
        String[] invHosts = {
                "https://inv.nadeko.net",
                "https://invidious.nerdvpn.de",
                "https://yewtu.be"
        };
        for (String host : invHosts) {
            try {
                Request req = new Request.Builder().url(host + "/api/v1/search?q=" + enc + "&type=video").build();
                try (Response r = HTTP.newCall(req).execute()) {
                    if (!r.isSuccessful() || r.body() == null) continue;
                    JSONArray arr = new JSONArray(r.body().string());
                    for (int i = 0; i < arr.length() && out.size() < 30; i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String vid = o.optString("videoId", "");
                        if (!vid.matches("[A-Za-z0-9_-]{11}")) continue;
                        out.add(new VideoItem(vid,
                                o.optString("title", ""),
                                o.optString("author", ""),
                                o.optString("videoThumbnails", null) != null
                                        && o.getJSONArray("videoThumbnails").length() > 0
                                        ? o.getJSONArray("videoThumbnails").getJSONObject(0).optString("url", null)
                                        : null,
                                o.optLong("lengthSeconds", 0) * 1000,
                                false, false, null, null));
                    }
                    if (!out.isEmpty()) return out;
                }
            } catch (Exception e) { last = e; }
        }
        throw new IOException(last != null ? last.getMessage() : "همه سرورها ناموفق");
    }
}
