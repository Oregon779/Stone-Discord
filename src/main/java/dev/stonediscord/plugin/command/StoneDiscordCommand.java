package dev.stonediscord.plugin.command;

import dev.stonediscord.plugin.StoneDiscord;
import dev.stonediscord.plugin.manager.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class StoneDiscordCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "help", "checkupdate");

    private final StoneDiscord plugin;

    public StoneDiscordCommand(StoneDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission("stonediscord.admin")) {
            mm.sendChat(sender, "general.no-permission", null);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                // Reload laeuft jetzt async (siehe StoneDiscord#reloadAsync) statt den
                // Main-Thread waehrend des Datei-I/O zu blockieren. Sofortiges Feedback
                // hier ("triggered"), die eigentliche Erfolgsmeldung kommt per Callback
                // zurueck auf den Main-Thread, sobald der Reload wirklich fertig ist.
                mm.sendChat(sender, "general.reload-triggered", null);
                plugin.reloadAsync(() -> {
                    if (sender instanceof Player player && !player.isOnline()) {
                        return;
                    }
                    plugin.getMessageManager().sendChat(sender, "general.reload-success", null);
                });
            }
            case "checkupdate" -> {
                mm.sendChat(sender, "update.check-triggered", null);
                plugin.getUpdateChecker().checkNow();
            }
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendRaw(sender, "help.header", null);
        mm.sendRaw(sender, "help.reload", null);
        mm.sendRaw(sender, "help.checkupdate", null);
        mm.sendRaw(sender, "help.help", null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("stonediscord.admin")) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        return List.of();
    }
}
