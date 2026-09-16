package dev.stonediscord.plugin;

import dev.stonediscord.plugin.command.DiscordCommand;
import dev.stonediscord.plugin.command.StoneDiscordCommand;
import dev.stonediscord.plugin.listener.PlayerQuitListener;
import dev.stonediscord.plugin.manager.ConfigManager;
import dev.stonediscord.plugin.manager.CooldownManager;
import dev.stonediscord.plugin.manager.MessageManager;
import dev.stonediscord.plugin.manager.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class StoneDiscord extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private CooldownManager cooldownManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        // Synchron und nur EINMALIG beim Serverstart (vor dem ersten Tick) -
        // hier ist Main-Thread-I/O unvermeidbar und unkritisch, da noch keine
        // Spieler verbunden sind. Der kritische Fall ist der spaetere
        // /stonediscord reload zur Laufzeit (siehe reloadAsync unten).
        getLogger().info("Loading configuration...");
        configManager = new ConfigManager(this);
        configManager.load();

        getLogger().info("Loading messages (" + configManager.getLanguage() + ")...");
        messageManager = new MessageManager(this);
        messageManager.load();

        cooldownManager = new CooldownManager();
        updateChecker = new UpdateChecker(this);

        getLogger().info("Registering commands...");
        registerCommands();

        getLogger().info("Registering listeners...");
        registerListeners();

        getLogger().info("Starting update checker...");
        updateChecker.start();

        getLogger().info("Stone Discord has been enabled - /discord is now ready to use.");
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.stop();
        }
        getLogger().info("Stone Discord has been disabled.");
    }

    // Ersetzt die alte synchrone reload(): config.yml + languages/*.yml lesen
    // und ggf. neu schreiben ist Disk-I/O, das bei 250+ Online-Spielern JEDEN
    // Tick fuer ALLE gleichzeitig verzoegert, wenn es im Main-Thread laeuft
    // (z.B. bei langsamem/virtualisiertem Storage). Die Bukkit-API wird dabei
    // nicht beruehrt (reine File-/YAML-Operationen), daher unbedenklich async.
    // Nur die Bestaetigungsnachricht an den Sender hoppt zurueck auf den
    // Main-Thread, wie es fuer Bukkit-Command-Feedback ueblich ist.
    public void reloadAsync(Runnable onMainThreadDone) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            configManager.reload();
            messageManager.load();
            cooldownManager.clear();

            Bukkit.getScheduler().runTask(this, () -> {
                updateChecker.start();
                if (onMainThreadDone != null) {
                    onMainThreadDone.run();
                }
            });
        });
    }

    private void registerCommands() {
        DiscordCommand discordCommand = new DiscordCommand(this);
        getCommand("discord").setExecutor((CommandExecutor) discordCommand);
        getCommand("discord").setTabCompleter((TabCompleter) discordCommand);

        StoneDiscordCommand adminCommand = new StoneDiscordCommand(this);
        getCommand("stonediscord").setExecutor((CommandExecutor) adminCommand);
        getCommand("stonediscord").setTabCompleter((TabCompleter) adminCommand);
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents((Listener) updateChecker, (Plugin) this);
        // Verhindert das CooldownManager-Memory-Leak (siehe dortiger Kommentar).
        pm.registerEvents(new PlayerQuitListener(this), this);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
