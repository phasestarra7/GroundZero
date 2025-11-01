package net.groundzero.command;

import net.groundzero.app.Core;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;

/** Dev-only helpers. No permission gate for now (add if you want). */
public final class AdminCommands {

    public boolean handleTest(CommandSender sender) {
        // Force reset from ANY state (even RUNNING/ENDED)
        Core.notifier.broadcast(Bukkit.getOnlinePlayers(), Sound.BLOCK_ANVIL_LAND, Notifier.PitchLevel.LOW,false,"Admin command handled : Terminating");
        Core.game.cancelAll();
        return true;
    }
}
