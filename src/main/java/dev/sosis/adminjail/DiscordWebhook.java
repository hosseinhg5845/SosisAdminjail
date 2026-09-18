package dev.sosis.adminjail;

import org.bukkit.Bukkit;
import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DiscordWebhook {
    private final SosisAdminjailPlugin plugin;
    private final DiscordConfig discordConfig;

    public DiscordWebhook(SosisAdminjailPlugin plugin, DiscordConfig discordConfig) {
        this.plugin = plugin;
        this.discordConfig = discordConfig;
    }

    public void sendJail(String player, String jail, int blocks, String reason) {
        if (!discordConfig.isEnabled()) return;
        String json = "{" +
                "\"username\":\"" + escape(discordConfig.getUsername()) + "\"," +
                "\"avatar_url\":\"" + escape(discordConfig.getAvatarUrl()) + "\"," +
                "\"embeds\":[{" +
                "\"title\":\"" + escape(discordConfig.getJailTitle()) + "\"," +
                "\"description\":\"" + escape(discordConfig.getJailDesc()) + "\"," +
                "\"color\":" + discordConfig.getJailColor() + "," +
                "\"fields\":[" +
                "{\"name\":\"" + escape(discordConfig.getJailFieldPlayer()) + "\",\"value\":\"" + escape(player) + "\",\"inline\":true}," +
                "{\"name\":\"" + escape(discordConfig.getJailFieldJail()) + "\",\"value\":\"" + escape(jail) + "\",\"inline\":true}," +
                "{\"name\":\"" + escape(discordConfig.getJailFieldBlocks()) + "\",\"value\":\"" + blocks + "\",\"inline\":true}," +
                "{\"name\":\"" + escape(discordConfig.getJailFieldReason()) + "\",\"value\":\"" + escape(reason) + "\",\"inline\":false}" +
                "]," +
                "\"timestamp\":\"" + getTimestamp() + "\"" +
                "}]}";
        send(json);
    }

    public void sendUnjail(String player, String reason) {
        if (!discordConfig.isEnabled()) return;
        String json = "{" +
                "\"username\":\"" + escape(discordConfig.getUsername()) + "\"," +
                "\"avatar_url\":\"" + escape(discordConfig.getAvatarUrl()) + "\"," +
                "\"embeds\":[{" +
                "\"title\":\"" + escape(discordConfig.getUnjailTitle()) + "\"," +
                "\"description\":\"" + escape(discordConfig.getUnjailDesc()) + "\"," +
                "\"color\":" + discordConfig.getUnjailColor() + "," +
                "\"fields\":[" +
                "{\"name\":\"" + escape(discordConfig.getUnjailFieldPlayer()) + "\",\"value\":\"" + escape(player) + "\",\"inline\":true}," +
                "{\"name\":\"" + escape(discordConfig.getUnjailFieldReason()) + "\",\"value\":\"" + escape(reason) + "\",\"inline\":true}" +
                "]," +
                "\"timestamp\":\"" + getTimestamp() + "\"" +
                "}]}";
        send(json);
    }

    public void sendProgress(String player, int current, int needed, String jail) {
        if (!discordConfig.isEnabled()) return;
        int percent = (int) (((double) current / needed) * 100);
        String json = "{" +
                "\"username\":\"" + escape(discordConfig.getUsername()) + "\"," +
                "\"avatar_url\":\"" + escape(discordConfig.getAvatarUrl()) + "\"," +
                "\"embeds\":[{" +
                "\"title\":\"" + escape(discordConfig.getProgressTitle()) + "\"," +
                "\"description\":\"" + escape(discordConfig.getProgressDesc()) + "\"," +
                "\"color\":" + discordConfig.getProgressColor() + "," +
                "\"fields\":[" +
                "{\"name\":\"" + escape(discordConfig.getProgressFieldPlayer()) + "\",\"value\":\"" + escape(player) + "\",\"inline\":true}," +
                "{\"name\":\"" + escape(discordConfig.getProgressFieldJail()) + "\",\"value\":\"" + escape(jail) + "\",\"inline\":true}," +
                "{\"name\":\"" + escape(discordConfig.getProgressFieldProgress()) + "\",\"value\":\"" + current + "/" + needed + " (" + percent + "%)\",\"inline\":true}" +
                "]" +
                "}]}";
        send(json);
    }

    private void send(String json) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpsURLConnection conn = (HttpsURLConnection) new URL(discordConfig.getWebhookUrl()).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    plugin.getLogger().fine("Discord sent");
                } else {
                    plugin.getLogger().warning("Discord failed: HTTP " + code);
                }
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Discord error: " + e.getMessage());
            }
        });
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}