package com.dubplayer.app.data;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Search + stream resolve via public Piped / Invidious instances.
 * No API key needed. All network calls are blocking - call off main thread.
 */
public final class YtSearch {
    private YtSearch() {}

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private static final Pattern ID_PAT = Pattern.compile(
            "(?:v=|youtu\\.be/|/shorts/|/embed/|/live/|/v/|/watch\\?v=)([A-Za-z0-9_-]{11})");
    private static final Pattern RAW_PAT = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    /** Extract 11-char YouTube id from raw id, watch url, youtu.be, shorts, embed, live. */
    public static String extractId(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        if (RAW_PAT.matcher(s).matches()) return s;
        Matcher m = ID_PAT.matcher(s);
        if (m.find()) return m.group(1);
        // Piped returns "/watch?v=ID" (no host) - handle query manually
        try {
            Uri u = Uri.parse(s);
            String v = u.getQueryParameter("v");
            if (v != null && RAW_PAT.matcher(v).matches()) return v;
            List<String> segs = u.getPathSegments();
            if (segs != null) {
                for (int i = segs.size() - 1; i >= 0; i--) {
                    if (RAW_PAT.matcher(segs.get(i)).matches()) return segs.get(i);
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static final String[] PIPED = {
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://api.piped.private.coffee"
    };
    private static final String[] INVID = {
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yewtu.be"
    };

    private static String get(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "DubPlayer/1.0")
                .build();
        try (Response r = HTTP.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) throw new IOException("HTTP " + r.code());
            return r.body().string();
        }
    }

    /** Search videos. Throws IOException when all instances fail. */
    public static List<VideoItem> search(String q) throws IOException {
        String enc = URLEncoder.encode(q, "UTF-8");
        List<VideoItem> out = new ArrayList<>();
        IOException last = null;
        for (String host : PIPED) {
            try {
                JSONArray arr = new JSONObject(get(host + "/search?q=" + enc + "&filter=videos"))
                        .optJSONArray("items");
                if (arr == null) continue;
                for (int i = 0; i < arr.length() && out.size() < 30; i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    if (!"stream".equals(o.optString("type", "stream"))) {
                        // Piped marks videos as type stream; skip channels/playlists
                        if (o.has("type") && !"stream".equals(o.optString("type"))) continue;
                    }
                    String id = extractId(o.optString("url", ""));
                    if (id == null) continue;
                    String thumb = o.optString("thumbnail", null);
                    if (thumb != null && thumb.startsWith("/")) thumb = host + thumb;
                    out.add(new VideoItem(id,
                            o.optString("title", id),
                            o.optString("uploaderName", ""),
                            thumb,
                            o.optLong("duration", 0) * 1000,
                            false, false, null, null));
                }
                if (!out.isEmpty()) return out;
            } catch (IOException e) { last = e; } catch (Exception e) { last = new IOException(e.getMessage(), e); }
        }
        for (String host : INVID) {
            try {
                JSONArray arr = new JSONArray(get(host + "/api/v1/search?q=" + enc + "&type=video"));
                for (int i = 0; i < arr.length() && out.size() < 30; i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null || !"video".equals(o.optString("type", "video"))) continue;
                    String vid = o.optString("videoId", "");
                    if (!RAW_PAT.matcher(vid).matches()) continue;
                    String thumb = null;
                    JSONArray thumbs = o.optJSONArray("videoThumbnails");
                    if (thumbs != null && thumbs.length() > 0) {
                        // pick a mid-size thumbnail
                        JSONObject t = thumbs.optJSONObject(Math.min(2, thumbs.length() - 1));
                        if (t != null) thumb = t.optString("url", null);
                    }
                    out.add(new VideoItem(vid,
                            o.optString("title", vid),
                            o.optString("author", ""),
                            thumb,
                            o.optLong("lengthSeconds", 0) * 1000,
                            false, false, null, null));
                }
                if (!out.isEmpty()) return out;
            } catch (IOException e) { last = e; } catch (Exception e) { last = new IOException(e.getMessage(), e); }
        }
        throw last != null ? last : new IOException("Search failed on all servers");
    }

    /** Resolve a playable http/hls url for a YouTube id. Returns null if unresolvable. */
    public static String resolveStream(String ytId) {
        for (String host : PIPED) {
            try {
                JSONObject o = new JSONObject(get(host + "/streams/" + ytId));
                // 1) muxed (audio+video) mp4, highest quality last
                JSONArray vs = o.optJSONArray("videoStreams");
                String bestMuxed = null;
                if (vs != null) {
                    for (int i = 0; i < vs.length(); i++) {
                        JSONObject s = vs.optJSONObject(i);
                        if (s == null) continue;
                        if (!s.optBoolean("videoOnly", true)) {
                            String u = s.optString("url", null);
                            if (u != null) bestMuxed = u; // keep last = best
                        }
                    }
                }
                if (bestMuxed != null) return bestMuxed;
                String hls = o.optString("hls", null);
                if (hls != null && hls.startsWith("http")) return hls;
            } catch (Exception ignored) { }
        }
        for (String host : INVID) {
            try {
                JSONObject o = new JSONObject(get(host + "/api/v1/videos/" + ytId));
                JSONArray fs = o.optJSONArray("formatStreams");
                String best = null;
                if (fs != null) {
                    for (int i = 0; i < fs.length(); i++) {
                        String u = fs.optJSONObject(i).optString("url", null);
                        if (u != null) best = u;
                    }
                }
                if (best != null) return best;
                String hls = o.optString("hlsUrl", null);
                if (hls != null && hls.startsWith("http")) {
                    return hls.startsWith("/") ? host + hls : hls;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** Piped stream title/author (best effort, may return null). */
    public static String[] fetchMeta(String ytId) {
        for (String host : PIPED) {
            try {
                JSONObject o = new JSONObject(get(host + "/streams/" + ytId));
                return new String[]{o.optString("title", ytId), o.optString("uploader", "")};
            } catch (Exception ignored) { }
        }
        return new String[]{ytId, ""};
    }
}
