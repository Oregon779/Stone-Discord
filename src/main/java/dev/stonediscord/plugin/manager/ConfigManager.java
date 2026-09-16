package dev.stonediscord.plugin.manager;

import dev.stonediscord.plugin.StoneDiscord;
import dev.stonediscord.plugin.config.ConfigUpdater;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {

    private static final String RESOURCE_PATH = "config.yml";

    private final StoneDiscord plugin;
    private File configFile;
    private YamlConfiguration config;

    // Gecachte Felder statt bei jedem Aufruf YamlConfiguration#get* (String-Split
    // von "discord.enabled" + Section-Walk) auszufuehren. isDiscordEnabled(),
    // getDiscordLink() und getDiscordCooldownSeconds() werden bei jedem /discord
    // gelesen - bei 250+ Spielern, die z.B. nach einem Broadcast gleichzeitig
    // reagieren, spart das pro Aufruf mehrere Map-Lookups zugunsten von O(1)
    // Feldzugriffen. Nur beim (seltenen) load()/reload() neu aufgeloest.
    private String language;
    private boolean discordEnabled;
    private String discordLink;
    private String discordMessage;
    private int discordCooldownSeconds;

    public ConfigManager(StoneDiscord plugin) {
        this.plugin = plugin;
    }

    public void load() {
        configFile = new File(plugin.getDataFolder(), RESOURCE_PATH);
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

        config = YamlConfiguration.loadConfiguration(configFile);

        language = config.getString("language", "en");
        discordEnabled = config.getBoolean("discord.enabled", true);
        discordLink = config.getString("discord.link", "https://discord.gg/your-invite");
        discordMessage = config.getString("discord.message",
                "<gradient:#5865F2:#7289DA><bold>Join our Discord!</bold></gradient> <gray>»</gray> <hover:show_text:'Click to open'><click:open_url:'{link}'><#5865F2>{link}</#5865F2></click></hover>");
        discordCooldownSeconds = config.getInt("discord.cooldown-seconds", 5);
    }

    public void reload() {
        load();
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public String getLanguage() {
        return language;
    }

    public boolean isDiscordEnabled() {
        return discordEnabled;
    }

    public String getDiscordLink() {
        return discordLink;
    }

    public String getDiscordMessage() {
        return discordMessage;
    }

    public int getDiscordCooldownSeconds() {
        return discordCooldownSeconds;
    }
}
