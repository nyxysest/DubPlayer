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
 * Search + stream resolve via public Piped instances (+ cobalt fallback).
 * No API key needed. All network calls are blocking - call off main thread.
 */
public final class YtSearch {
    private YtSearch() {}

    public static final String UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
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
            "https://api.piped.private.coffee",
            "https://pipedapi.reallyaweso.me",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.orangenet.cc",
            "https://pipedapi.ducks.party",
            "https://pipedapi.drgns.space",
            "https://api.piped.yt",
            "https://pipedapi.owo.si",
            "https://piped-api.codespace.cz",
            "https://pipedapi.darkness.services",
            "https://pipedapi-libre.kavin.rocks",
            "https://piped-api.privacy.com.de"
    };

    private static String get(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build();
        try (Response r = HTTP.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) throw new IOException("HTTP " + r.code());
            return r.body().string();
        }
    }

    private static JSONObject getJson(String url) throws IOException {
        try {
            return new JSONObject(get(url));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("bad json");
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
                    if (o.has("type") && !"stream".equals(o.optString("type"))) continue;
                    String id = extractId(o.optString("url", ""));
                    if (id == null) continue;
                    String thumb = o.optString("thumbnail", null);
                    if (thumb != null && thumb.startsWith("/")) thumb = host + thumb;
                    out.add(new VideoItem(id,
                            o.optString("title", id),
                            o.optString("uploaderName", ""),
                            thumb,
                            o.optLong("duration", -1) >= 0 ? o.optLong("duration", 0) * 1000 : 0,
                            false, false, null, null));
                }
                if (!out.isEmpty()) return out;
            } catch (IOException e) { last = e; } catch (Exception e) { last = new IOException(e.getMessage(), e); }
        }
        throw last != null ? last : new IOException("Search failed on all servers");
    }

    /**
     * Resolve playable streams. Priority:
     *  1. hls (most reliable, adaptive, single url)
     *  2. muxed mp4 (itag 18/22, direct single url)
     *  3. merged videoOnly+audio (720p mp4 + m4a) via MergingMediaSource
     * Single /streams call per host (title+streams together) - halves 500 risk.
     */
    public static StreamRef resolve(String ytId) {
        for (String host : PIPED) {
            JSONObject o;
            try {
                o = getJson(host + "/streams/" + ytId);
            } catch (Exception e) {
                continue;
            }
            try {
                String title = o.optString("title", ytId);
                String author = o.optString("uploader", "");

                String hls = o.optString("hls", null);
                if (hls != null && hls.startsWith("http")) {
                    return new StreamRef(hls, "hls", null, null, title, author);
                }
                JSONArray vs = o.optJSONArray("videoStreams");
                String bestMuxed = null;
                String bestV = null, bestA = null;
                int bestH = -1;
                String bestAudioUrl = pickAudio(o.optJSONArray("audioStreams"));
                if (vs != null) {
                    for (int i = 0; i < vs.length(); i++) {
                        JSONObject s = vs.optJSONObject(i);
                        if (s == null) continue;
                        String u = s.optString("url", null);
                        if (u == null || !u.startsWith("http")) continue;
                        String mime = s.optString("mimeType", "");
                        if (!s.optBoolean("videoOnly", true)) {
                            // muxed: prefer mp4 over webm/3gp, prefer higher res
                            int h = s.optInt("height", s.optInt("height", 0));
                            boolean mp4 = mime.contains("mp4");
                            if (bestMuxed == null || (mp4 && h >= bestH)) {
                                bestMuxed = u;
                                bestH = mp4 ? h : bestH;
                            }
                        } else {
                            // videoOnly candidate: mp4 + has audio track companion
                            if (mime.contains("mp4") && bestAudioUrl != null) {
                                int h = s.optInt("height", 0);
                                if (h <= 720 && h > bestH) {
                                    bestH = h;
                                    bestV = u;
                                }
                            }
                        }
                    }
                }
                if (bestMuxed != null) {
                    return new StreamRef(bestMuxed, null, null, null, title, author);
                }
                if (bestV != null && bestAudioUrl != null) {
                    return new StreamRef(null, null, bestV, bestAudioUrl, title, author);
                }
                String dash = o.optString("dash", null);
                if (dash != null && dash.startsWith("http")) {
                    return new StreamRef(dash, "dash", null, null, title, author);
                }
                // host answered but nothing usable -> try next host anyway
            } catch (Exception ignored) { }
        }
        // cobalt fallback: public api instances return a direct mp4/tunnel url
        StreamRef cb = resolveViaCobalt(ytId);
        if (cb != null) return cb;
        return null;
    }

    /** Highest-bitrate m4a/mp4 audio url, or null. */
    private static String pickAudio(JSONArray as) {
        if (as == null) return null;
        String best = null;
        long bestBr = -1;
        for (int i = 0; i < as.length(); i++) {
            JSONObject a = as.optJSONObject(i);
            if (a == null) continue;
            String u = a.optString("url", null);
            if (u == null || !u.startsWith("http")) continue;
            String mime = a.optString("mimeType", "");
            if (!(mime.contains("mp4") || mime.contains("m4a") || mime.contains("mp4a"))) continue;
            long br = a.optLong("bitrate", 0);
            if (br >= bestBr) { bestBr = br; best = u; }
        }
        return best;
    }

    private static final String[] COBALT = {
            "https://cobalt-api.kwiatekmiki.com",
            "https://co.wukko.xyz"
    };

    /** cobalt /api/json: {url, filename} direct download (usually 720p mp4 tunnel). */
    private static StreamRef resolveViaCobalt(String ytId) {
        String page = "https://www.youtube.com/watch?v=" + ytId;
        for (String api : COBALT) {
            try {
                String body = new JSONObject()
                        .put("url", page)
                        .put("videoQuality", "720")
                        .put("youtubeVideoCodec", "h264")
                        .toString();
                okhttp3.RequestBody rb = okhttp3.RequestBody.create(
                        body, okhttp3.MediaType.parse("application/json"));
                Request req = new Request.Builder()
                        .url(api + "/api/json")
                        .header("User-Agent", UA)
                        .header("Accept", "application/json")
                        .post(rb)
                        .build();
                try (Response r = HTTP.newCall(req).execute()) {
                    if (!r.isSuccessful() || r.body() == null) continue;
                    JSONObject o = new JSONObject(r.body().string());
                    String st = o.optString("status", "");
                    String u = o.optString("url", null);
                    if (("redirect".equals(st) || "tunnel".equals(st) || "stream".equals(st))
                            && u != null && u.startsWith("http")) {
                        String fn = o.optString("filename", ytId);
                        return new StreamRef(u, null, null, null, fn, "");
                    }
                }
            } catch (Exception ignored) { }
        }
        return null;
    }
}
