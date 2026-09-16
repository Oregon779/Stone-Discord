package dev.stonediscord.plugin.command;

import dev.stonediscord.plugin.StoneDiscord;
import dev.stonediscord.plugin.manager.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class DiscordCommand implements CommandExecutor, TabCompleter {

    private final StoneDiscord plugin;

    public DiscordCommand(StoneDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission("stonediscord.use")) {
            mm.sendChat(sender, "general.no-permission", null);
            return true;
        }

        if (!plugin.getConfigManager().isDiscordEnabled()) {
            mm.sendChat(sender, "discord.disabled", null);
            return true;
        }

        if (sender instanceof Player player && !player.hasPermission("stonediscord.admin")) {
            int cooldownSeconds = plugin.getConfigManager().getDiscordCooldownSeconds();
            int remaining = plugin.getCooldownManager().tryUse(player.getUniqueId(), cooldownSeconds);
            if (remaining > 0) {
                mm.sendChat(sender, "discord.cooldown", Map.of("seconds", String.valueOf(remaining)));
                return true;
            }
        }

        sender.sendMessage(mm.format(plugin.getConfigManager().getDiscordMessage(), Map.of(
                "player", sender.getName(),
                "link", plugin.getConfigManager().getDiscordLink()
        )));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
