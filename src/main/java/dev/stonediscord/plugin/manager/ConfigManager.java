package dev.stonediscord.plugin.manager;

import dev.stonediscord.plugin.StoneDiscord;
import dev.stonediscord.plugin.config.ConfigUpdater;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {

    private static final String RESOURCE_PATH = "config.yml";

    // Bugfix: load()/reload() laeuft seit StoneDiscord#reloadAsync auf einem
    // async Thread, waehrend gleichzeitig andere Spieler auf dem Main-Thread
    // /discord ausfuehren und dabei isDiscordEnabled()/getDiscordLink()/... lesen.
    // Vorher wurden dafuer mehrere einzelne, nicht-volatile Felder nacheinander
    // ueberschrieben - ein Leser konnte dabei einen Mix aus altem und neuem
    // Stand sehen (torn read), ohne jede Synchronisierung sogar dauerhaft einen
    // veralteten Wert. Jetzt wird der komplette neue Zustand abseits der Felder
    // aufgebaut und erst danach in einem einzigen atomaren Schritt ueber eine
    // volatile Referenz sichtbar gemacht.
    private record ConfigSnapshot(YamlConfiguration config, String language, boolean discordEnabled,
                                   String discordLink, String discordMessage, int discordCooldownSeconds) {
    }

    private final StoneDiscord plugin;
    private volatile ConfigSnapshot snapshot;

    public ConfigManager(StoneDiscord plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!configFile.exists()) {
            plugin.saveResource(RESOURCE_PATH, false);
        }

        try {
            ConfigUpdater.UpdateResult result = ConfigUpdater.update(plugin, RESOURCE_PATH, configFile);
            if (result.addedKeys() > 0) {
                plugin.getLogger().info("Added " + result.addedKeys() + " new option(s) to config.yml");
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to update config.yml: " + ex.getMessage());
        }

        YamlConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);
        snapshot = new ConfigSnapshot(
                newConfig,
                newConfig.getString("language", "en"),
                newConfig.getBoolean("discord.enabled", true),
                newConfig.getString("discord.link", "https://discord.gg/your-invite"),
                newConfig.getString("discord.message",
                        "<gradient:#5865F2:#7289DA><bold>Join our Discord!</bold></gradient> <gray>»</gray> <hover:show_text:'Click to open'><click:open_url:'{link}'><#5865F2>{link}</#5865F2></click></hover>"),
                newConfig.getInt("discord.cooldown-seconds", 5)
        );
    }

    public void reload() {
        load();
    }

    public String getString(String path, String def) {
        return snapshot.config().getString(path, def);
    }

    public int getInt(String path, int def) {
        return snapshot.config().getInt(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return snapshot.config().getBoolean(path, def);
    }

    public String getLanguage() {
        return snapshot.language();
    }

    public boolean isDiscordEnabled() {
        return snapshot.discordEnabled();
    }

    public String getDiscordLink() {
        return snapshot.discordLink();
    }

    public String getDiscordMessage() {
        return snapshot.discordMessage();
    }

    public int getDiscordCooldownSeconds() {
        return snapshot.discordCooldownSeconds();
    }
}
