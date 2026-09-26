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

import java.util.concurrent.atomic.AtomicBoolean;

public final class StoneDiscord extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private CooldownManager cooldownManager;
    private UpdateChecker updateChecker;
    // Bugfix: verhindert, dass zwei gleichzeitige /stonediscord reload zwei
    // parallele async Tasks starten, die dieselbe config.yml/messages.yml
    // gleichzeitig lesen und beschreiben (moegliche Dateikorruption bei zwei
    // Schreibzugriffen auf denselben Datensatz).
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);

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
    // Rueckgabewert zeigt an, ob der Reload wirklich gestartet wurde. false
    // bedeutet: es laeuft bereits ein anderer Reload, dieser Aufruf wurde
    // verworfen (siehe reloadInProgress oben) statt einen zweiten, parallelen
    // Schreibzugriff auf dieselben Dateien auszuloesen.
    public boolean reloadAsync(Runnable onMainThreadDone) {
        if (!reloadInProgress.compareAndSet(false, true)) {
            return false;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                configManager.reload();
                messageManager.load();
                cooldownManager.clear();
            } finally {
                reloadInProgress.set(false);
            }

            Bukkit.getScheduler().runTask(this, () -> {
                updateChecker.start();
                if (onMainThreadDone != null) {
                    onMainThreadDone.run();
                }
            });
        });
        return true;
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
