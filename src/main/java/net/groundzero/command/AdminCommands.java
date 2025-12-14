package net.groundzero.command;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Dev-only helpers. No permission gate for now (add if you want). */
public final class AdminCommands {

    public boolean handleTest(CommandSender sender) {
        // Force reset from ANY state (even RUNNING/ENDED)
        Core.notifier.broadcast(Bukkit.getOnlinePlayers(), Sound.BLOCK_ANVIL_LAND, Notifier.PitchLevel.LOW,false,"Admin command handled : Terminating");
        Core.game.forceStop(null);
        return true;
    }

    public boolean handleItems(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command is only available to players");
            return true;
        }

        int count = 0;

        // Give all items from ItemType enum
        for (ItemType type : ItemType.values()) {
            ItemStack item = Core.itemRegistry.createItem(type, 1);
            player.getInventory().addItem(item);
            count++;
        }

        Core.notifier.message(player, false,
                "Gave " + count + " items for texture testing",
                "Check your inventory!"
        );

        Core.notifier.sound(player.getUniqueId(), Sound.ENTITY_PLAYER_LEVELUP, Notifier.PitchLevel.MID);

        return true;
    }
}
