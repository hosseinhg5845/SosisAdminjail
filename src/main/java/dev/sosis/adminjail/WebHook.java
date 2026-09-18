package dev.sosis.adminjail;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class WebHook {
    private final SosisAdminjailPlugin plugin;
    private final boolean enabled;
    private final String url;
    private final String apiKey;
    private final int timeout;
    private final int retryCount;

    public WebHook(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("website.enabled", false);
        this.url = plugin.getConfig().getString("website.url", "");
        this.apiKey = plugin.getConfig().getString("website.api-key", "");
        this.timeout = plugin.getConfig().getInt("website.timeout", 10) * 1000;
        this.retryCount = plugin.getConfig().getInt("website.retry-count", 3);
    }

    public void sendJailData(String playerName, String jailName, int blocks, String reason, String status) {
        if (!enabled || url.isEmpty()) return;
        String json = String.format("{%s%s%s%s%s%s%s%s}",
                "\"player\":\"" + escapeJson(playerName) + "\",",
                "\"jail\":\"" + escapeJson(jailName) + "\",",
                "\"blocks\":" + blocks + ",",
                "\"reason\":\"" + escapeJson(reason) + "\",",
                "\"status\":\"" + escapeJson(status) + "\",",
                "\"timestamp\":" + System.currentTimeMillis() + ",",
                "\"server\":\"" + escapeJson(plugin.getServer().getName()) + "\",",
                "\"api-key\":\"" + escapeJson(apiKey) + "\"");
        sendRequest(json);
    }

    public void sendProgressUpdate(String playerName, int current, int needed, String jailName) {
        if (!enabled || url.isEmpty()) return;
        String json = String.format("{%s%s%s%s%s%s}",
                "\"player\":\"" + escapeJson(playerName) + "\",",
                "\"current\":" + current + ",",
                "\"needed\":" + needed + ",",
                "\"jail\":\"" + escapeJson(jailName) + "\",",
                "\"type\":\"progress\",",
                "\"api-key\":\"" + escapeJson(apiKey) + "\"");
        sendRequest(json);
    }

    public void sendUnjailData(String playerName, String reason) {
        if (!enabled || url.isEmpty()) return;
        String json = String.format("{%s%s%s%s%s}",
                "\"player\":\"" + escapeJson(playerName) + "\",",
                "\"reason\":\"" + escapeJson(reason) + "\",",
                "\"status\":\"unjailed\",",
                "\"timestamp\":" + System.currentTimeMillis() + ",",
                "\"api-key\":\"" + escapeJson(apiKey) + "\"");
        sendRequest(json);
    }

    private void sendRequest(String json) {
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < retryCount; i++) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(timeout);
                    conn.setReadTimeout(timeout);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    int responseCode = conn.getResponseCode();
                    if (responseCode >= 200 && responseCode < 300) { return; }
                    conn.disconnect();
                } catch (Exception e) { plugin.getLogger().warning("WebHook error: " + e.getMessage()); }
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
            plugin.getLogger().warning("WebHook failed after " + retryCount + " attempts");
        });
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    public boolean isEnabled() { return enabled; }
}