package com.cloudstream.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.conscrypt.Conscrypt;

public class SoundCloudAPI {
    private static final String[] CLIENT_IDS = {
            "Mxv2e5wxnWei6krLywjIXpztX7S0VCeK",
            "YUKOSUSETA4nCqJgq5bV5N6uP4O4J8r0",
            "a3e059563d7fd3372b49b37f00a00bcf",
            "t210k9m1N8h3r9i48G24o936Q205l75c",
            "b45b1aa10f1ac2941910a7f0d10f8e28",
            "d866a8ee6ce05f93539e76fa2e7a3399"
    };
    private static int currentClientIndex = 0;

    public static final String API_BASE = "https://api-v2.soundcloud.com";

    public static synchronized void rotateClientId() {
        currentClientIndex = (currentClientIndex + 1) % CLIENT_IDS.length;
    }

    private static OkHttpClient client;

    public static synchronized OkHttpClient getClient() {
        if (client == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .dns(new Dns() {
                        @Override
                        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                            try {
                                return Dns.SYSTEM.lookup(hostname);
                            } catch (UnknownHostException e) {
                                String ip = resolveDoH(hostname);
                                if (ip != null) {
                                    return java.util.Collections.singletonList(InetAddress.getByName(ip));
                                }
                                throw e;
                            }
                        }
                    });

            try {
                javax.net.ssl.X509TrustManager trustManager = new javax.net.ssl.X509TrustManager() {
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                };
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS", "Conscrypt");
                sslContext.init(null, new javax.net.ssl.TrustManager[] { trustManager }, null);
                builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
                builder.hostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, javax.net.ssl.SSLSession session) { return true; }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            client = builder.build();
        }
        return client;
    }

    public static String resolveDoH(String hostname) {
        try {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS);

            try {
                javax.net.ssl.X509TrustManager trustManager = new javax.net.ssl.X509TrustManager() {
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                };
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS", "Conscrypt");
                sslContext.init(null, new javax.net.ssl.TrustManager[] { trustManager }, null);
                builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
                builder.hostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, javax.net.ssl.SSLSession session) { return true; }
                });
            } catch (Exception e) {}

            OkHttpClient dohClient = builder.build();
            Request request = new Request.Builder()
                    .url("https://8.8.8.8/resolve?name=" + hostname + "&type=A")
                    .header("Host", "dns.google")
                    .header("Accept", "application/json")
                    .build();

            Response response = dohClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                JSONObject json = new JSONObject(response.body().string());
                JSONArray answer = json.optJSONArray("Answer");
                if (answer != null) {
                    for (int i = 0; i < answer.length(); i++) {
                        JSONObject record = answer.getJSONObject(i);
                        if (record.optInt("type") == 1) { // A record
                            return record.optString("data");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String httpGet(String urlStr, boolean isRedirectExpected) throws Exception {
        int maxRetries = CLIENT_IDS.length;
        for (int i = 0; i < maxRetries; i++) {
            String requestUrl = urlStr.replaceAll("client_id=[^&]+", "client_id=" + CLIENT_IDS[currentClientIndex]);

            Request request = new Request.Builder()
                    .url(requestUrl)
                    .header("User-Agent", "SoundCloud/3.2.0 (Linux; Android 4.3; BlackBerry Q10)")
                    .header("Accept", "application/json")
                    .header("x-request-id", UUID.randomUUID().toString())
                    .header("Referer", "https://soundcloud.com/")
                    .build();

            OkHttpClient c = getClient();
            if (isRedirectExpected) {
                c = c.newBuilder().followRedirects(false).followSslRedirects(false).build();
            }

            Response response = c.newCall(request).execute();
            int code = response.code();

            if (code == 401 || code == 403 || code == 404) {
                rotateClientId();
                if (i == maxRetries - 1) {
                    throw new Exception("Auth error: " + code);
                }
                continue; // retry
            } else if (code == 429) {
                Thread.sleep(2000);
                if (i == maxRetries - 1) {
                    throw new Exception("Rate limited: " + code);
                }
                continue; // retry after delay
            }

            if (isRedirectExpected && (code == 301 || code == 302 || code == 303 || code == 307)) {
                return response.header("Location");
            }

            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }

            if (i == maxRetries - 1) {
                throw new Exception("HTTP request failed: " + code);
            }
        }
        throw new Exception("HTTP request failed after retries");
    }

    public static void searchTracks(final String query, final Handler handler, final SearchCallback callback) {
        if (BerryMusicApp.ctx != null) {
            android.content.SharedPreferences p = BerryMusicApp.ctx.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE);
            if (p.getBoolean("Offline Mode", false)) {
                if(handler != null) handler.post(new Runnable() { public void run() { callback.onError("Offline Mode enabled"); } });
                return;
            }
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
                    String url = API_BASE + "/search/tracks?q=" + encodedQuery + "&limit=20&client_id=" + CLIENT_IDS[currentClientIndex];
                    String jsonStr = httpGet(url, false);
                    JSONObject root = new JSONObject(jsonStr);
                    JSONArray collection = root.getJSONArray("collection");
                    final ArrayList<MainActivity.TrackItem> results = new ArrayList<MainActivity.TrackItem>();

                    for (int i = 0; i < collection.length(); i++) {
                        JSONObject track = collection.getJSONObject(i);
                        MainActivity.TrackItem item = new MainActivity.TrackItem();
                        item.id = String.valueOf(track.optInt("id"));
                        item.title = track.optString("title");

                        JSONObject user = track.optJSONObject("user");
                        if (user != null) {
                            item.artist = user.optString("username");
                        } else {
                            item.artist = "Unknown";
                        }

                        item.artworkUrl = track.optString("artwork_url");
                        if (item.artworkUrl == null || item.artworkUrl.equals("null")) {
                            if (user != null) item.artworkUrl = user.optString("avatar_url");
                        }

                        item.duration = track.optInt("duration");
                        item.transcodingsUrl = null;

                        JSONObject media = track.optJSONObject("media");
                        if (media != null) {
                            JSONArray transcodings = media.optJSONArray("transcodings");
                            if (transcodings != null) {
                                for (int j = 0; j < transcodings.length(); j++) {
                                    JSONObject t = transcodings.getJSONObject(j);
                                    JSONObject format = t.optJSONObject("format");

                                    boolean isHQ = false;
                                    if (BerryMusicApp.ctx != null) {
                                        isHQ = BerryMusicApp.ctx.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE).getBoolean("High Quality Audio", false);
                                    }
                                    String targetFormat = isHQ ? "hls" : "progressive";
                                    if (format != null && targetFormat.equals(format.optString("protocol"))) {
                                        item.transcodingsUrl = t.optString("url");
                                    } else if (format != null && "progressive".equals(format.optString("protocol")) && item.transcodingsUrl == null) {
                                        item.transcodingsUrl = t.optString("url");
                                    }

                                }
                            }
                        }
                        results.add(item);
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(results);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    public static void getStreamUrl(final String transcodingsUrl, final Handler handler, final StreamUrlCallback callback) {
        if (BerryMusicApp.ctx != null) {
            android.content.SharedPreferences p = BerryMusicApp.ctx.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE);
            if (p.getBoolean("Offline Mode", false)) {
                if(handler != null) handler.post(new Runnable() { public void run() { callback.onError("Offline Mode enabled"); } });
                return;
            }
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = transcodingsUrl + (transcodingsUrl.contains("?") ? "&" : "?") + "client_id=" + CLIENT_IDS[currentClientIndex];
                    String jsonStr = httpGet(url, false);
                    JSONObject root = new JSONObject(jsonStr);
                    String streamUrl = root.optString("url");

                    final String res = streamUrl;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(res);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    public static void getTrackArtwork(final String urlStr, final Handler handler, final ArtworkCallback callback) {

        if (BerryMusicApp.ctx != null) {
            android.content.SharedPreferences p = BerryMusicApp.ctx.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE);
            if (p.getBoolean("Offline Mode", false) || p.getBoolean("Data Saver", false)) {
                if(handler != null) handler.post(new Runnable() { public void run() { callback.onSuccess(null); } });
                return;
            }
        }
        if (urlStr == null || urlStr.isEmpty() || urlStr.equals("null")) {
            handler.post(new Runnable() { public void run() { callback.onSuccess(null); } });
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Request request = new Request.Builder().url(urlStr).build();
                    Response response = getClient().newCall(request).execute();

                    if (response.isSuccessful() && response.body() != null) {
                        InputStream is = response.body().byteStream();
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2;
                        final Bitmap bmp = BitmapFactory.decodeStream(is, null, options);
                        is.close();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess(bmp);
                            }
                        });
                    } else {
                        throw new Exception("Failed to load artwork");
                    }
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    public interface SearchCallback {
        void onSuccess(ArrayList<MainActivity.TrackItem> results);
        void onError(String error);
    }

    public interface StreamUrlCallback {
        void onSuccess(String url);
        void onError(String error);
    }

    public interface ArtworkCallback {
        void onSuccess(Bitmap bitmap);
        void onError(String error);
    }
}
