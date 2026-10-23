package dev.stonediscord.plugin.command;

import dev.stonediscord.plugin.StoneDiscord;
import dev.stonediscord.plugin.manager.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shared executor for every configurable link-menu command (/media,
 * /social, ...). One instance per command, parameterized with which menu
 * to open, which permission guards it and which message to send if that
 * menu is disabled.
 */
public class LinkMenuCommand implements CommandExecutor {

    private final StoneDiscord plugin;
    private final String menuKey;
    private final String permission;
    private final String disabledMessageKey;

    public LinkMenuCommand(StoneDiscord plugin, String menuKey, String permission, String disabledMessageKey) {
        this.plugin = plugin;
        this.menuKey = menuKey;
        this.permission = permission;
        this.disabledMessageKey = disabledMessageKey;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission(permission)) {
            mm.sendChat(sender, "general.no-permission", null);
            return true;
        }
        if (!(sender instanceof Player player)) {
            mm.sendChat(sender, "general.player-only", null);
            return true;
        }

        plugin.getLinkGuiManager().open(player, menuKey, disabledMessageKey);
        return true;
    }
}
