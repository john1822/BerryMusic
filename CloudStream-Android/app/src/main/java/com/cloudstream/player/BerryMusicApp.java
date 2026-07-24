package com.cloudstream.player;

import android.app.Application;
import javax.net.ssl.HttpsURLConnection;
import java.security.Security;
import org.conscrypt.Conscrypt;

public class BerryMusicApp extends Application {
    public static android.content.Context ctx;
    @Override
    public void onCreate() {
        super.onCreate();
        ctx = this;
        try {
            // Install Conscrypt to provide modern TLS 1.2/1.3 and fix SSL_ERROR_ZERO_RETURN
            Security.insertProviderAt(Conscrypt.newProvider(), 1);

            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS", "Conscrypt");

            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    }
            };

            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                @Override
                public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                    return true;
                }
            });

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
