package net.groundzero.command;

import net.groundzero.app.Core;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Player-facing subcommands in vanilla style. */
public final class PlayerCommands {

    public boolean handleStart(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§r§cGroundZero §f| §cThis command is only available to players§r");
            return true; // handled
        }
        Core.game.start(p);  // GameManager decides and notifies
        return true;        // handled (no usage print)
    }

    public boolean handleCancel(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§r§cGroundZero §f| §cThis command is only available to players§r");
            return true;
        }
        Core.game.tryCancel(p); // GameManager decides and notifies
        return true;
    }
}
