package dev.stonediscord.plugin.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.stonediscord.plugin.StoneDiscord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class UpdateChecker implements Listener {

    private static final String MODRINTH_PROJECT_SLUG = "stone-discord";
    private static final URI VERSIONS_URI = URI.create("https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version");

    private final StoneDiscord plugin;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private BukkitTask task;
    private volatile String latestKnownVersion = null;
    private volatile int versionsBehind = -1;

    public UpdateChecker(StoneDiscord plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfigManager().getBoolean("update-checker.enabled", true)) {
            plugin.getLogger().info("Update checker: disabled in config.yml (update-checker.enabled: false).");
            return;
        }
        long intervalMinutes = Math.max(5, plugin.getConfigManager().getInt("update-checker.check-interval-minutes", 60));
        long intervalTicks = intervalMinutes * 60L * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, 100L, intervalTicks);
    }

    public void checkNow() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::check);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(VERSIONS_URI)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "StonePlugins/StoneDiscord update-checker")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("Update checker: Modrinth responded with status " + response.statusCode() + " for project '" + MODRINTH_PROJECT_SLUG + "'.");
                return;
            }

            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            if (versions.isEmpty()) {
                plugin.getLogger().info("Update checker: project '" + MODRINTH_PROJECT_SLUG + "' found, but no version uploaded yet.");
                return;
            }

            String newest = versions.get(0).getAsJsonObject().get("version_number").getAsString();
            String current = plugin.getPluginMeta().getVersion();

            if (isNewer(newest, current)) {
                latestKnownVersion = newest;
                versionsBehind = countVersionsBehind(versions, current);
                logToConsole(newest, current);
                Bukkit.getScheduler().runTask(plugin, () -> notifyOnlineEligiblePlayers(newest, current));
            } else {
                latestKnownVersion = null;
                versionsBehind = -1;
                plugin.getLogger().info("No new version available (running " + current + ").");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Update checker: check failed (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
    }

    private void logToConsole(String newest, String current) {
        String behind = versionsBehind < 0 ? "an unknown number of versions" : versionsBehind + " version(s)";
        plugin.getLogger().info("A new version of Stone Discord is available: " + newest + " (you're on " + current + ", " + behind
                + " behind). Get it at https://modrinth.com/project/" + MODRINTH_PROJECT_SLUG);
    }

    private int countVersionsBehind(JsonArray versions, String current) {
        for (int i = 0; i < versions.size(); i++) {
            String versionNumber = versions.get(i).getAsJsonObject().get("version_number").getAsString();
            if (versionNumber.equals(current)) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String newest = latestKnownVersion;
        if (newest == null || !isEligible(player)) {
            return;
        }
        notifyPlayer(player, newest, plugin.getPluginMeta().getVersion());
    }

    private boolean isEligible(Player player) {
        return player.isOp() || player.hasPermission("stonediscord.admin");
    }

    private void notifyOnlineEligiblePlayers(String newest, String current) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isEligible(player)) {
                notifyPlayer(player, newest, current);
            }
        }
    }

    private void notifyPlayer(Player player, String newest, String current) {
        MessageManager mm = plugin.getMessageManager();
        // Vorher: 2x HashMap anlegen + einzeln befuellen (put/put). Da dieser Pfad
        // von PlayerJoinEvent ausgeloest wird - auf einem 250er-Server bei hoher
        // Fluktuation potenziell viele Male pro Minute - lohnt sich die unnoetige
        // Zwischen-Allokation zu vermeiden. Map.of() ist eine einzige unveraenderliche
        // Allokation ohne Resize/Rehash-Overhead, den eine HashMap beim Befuellen
        // potenziell hat.
        String behindText = versionsBehind < 0
                ? mm.getRaw("update.versions-behind-unknown")
                : mm.getFormattedRaw("update.versions-behind", Map.of("count", String.valueOf(versionsBehind)));

        player.sendMessage(mm.format(mm.getRaw("update.available"), Map.of(
                "version", newest,
                "current", current,
                "behind", behindText
        )));
    }

    private boolean isNewer(String remote, String current) {
        try {
            String[] remoteParts = remote.split("\\.");
            String[] currentParts = current.split("\\.");
            int length = Math.max(remoteParts.length, currentParts.length);
            for (int i = 0; i < length; i++) {
                int r = i < remoteParts.length ? parsePart(remoteParts[i]) : 0;
                int c = i < currentParts.length ? parsePart(currentParts[i]) : 0;
                if (r != c) {
                    return r > c;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private int parsePart(String part) {
        StringBuilder digits = new StringBuilder();
        for (char c : part.toCharArray()) {
            if (!Character.isDigit(c)) {
                break;
            }
            digits.append(c);
        }
        return digits.isEmpty() ? 0 : Integer.parseInt(digits.toString());
    }
}
