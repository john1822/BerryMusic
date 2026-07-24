package com.cloudstream.player;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

public class LocalProxyServer extends Thread {
    private ServerSocket serverSocket;
    private int port;
    private boolean isRunning = true;

    public LocalProxyServer() {
        try {
            serverSocket = new ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"));
            port = serverSocket.getLocalPort();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getPort() {
        return port;
    }

    public void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) {}
    }

    @Override
    public void run() {
        if (serverSocket == null) return;
        while (isRunning) {
            try {
                final Socket client = serverSocket.accept();
                new Thread(new Runnable() {
                    public void run() {
                        handleClient(client);
                    }
                }).start();
            } catch (Exception e) {
                if (!isRunning) break;
            }
        }
    }

    private void handleClient(Socket client) {
        try {
            InputStream in = client.getInputStream();
            StringBuilder request = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                request.append((char) c);
                if (request.toString().endsWith("\r\n\r\n")) break;
            }
            String[] lines = request.toString().split("\r\n");
            if (lines.length == 0) {
                client.close();
                return;
            }
            String firstLine = lines[0];
            String[] parts = firstLine.split(" ");
            if (parts.length < 2) {
                client.close();
                return;
            }
            String path = parts[1];
            if (!path.startsWith("/?url=")) {
                client.close();
                return;
            }
            String rangeHeader = null;
            for (String line : lines) {
                if (line.toLowerCase().startsWith("range:")) {
                    rangeHeader = line.substring(6).trim();
                }
            }
            String targetUrl = path.substring(6);
            targetUrl = java.net.URLDecoder.decode(targetUrl, "UTF-8");



            okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder().url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            if (rangeHeader != null) {
                reqBuilder.header("Range", rangeHeader);
            }
            okhttp3.OkHttpClient streamClient = SoundCloudAPI.getClient().newBuilder()
                    .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            okhttp3.Response response = streamClient.newCall(reqBuilder.build()).execute();
            int responseCode = response.code();

            OutputStream out = client.getOutputStream();
            out.write(("HTTP/1.1 " + (responseCode == 206 ? "206 Partial Content" : "200 OK") + "\r\n").getBytes());
            String ct = response.header("Content-Type");
            if (ct == null) ct = "audio/mpeg";
            out.write(("Content-Type: " + ct + "\r\n").getBytes());
            String cl = response.header("Content-Length");
            if (cl != null) out.write(("Content-Length: " + cl + "\r\n").getBytes());
            String cr = response.header("Content-Range");
            if (cr != null) out.write(("Content-Range: " + cr + "\r\n").getBytes());
            out.write(("Accept-Ranges: bytes\r\n").getBytes());
            out.write(("Connection: close\r\n\r\n").getBytes());

            if (responseCode >= 400) {
                out.close();
                client.close();
                return;
            }

            InputStream targetIn = response.body().byteStream();


            if (targetIn != null) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = targetIn.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
                targetIn.close();
            }
            out.close();
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
            try { client.close(); } catch (Exception ex) {}
        }
    }}
