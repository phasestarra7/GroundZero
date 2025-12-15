package net.groundzero.item.handler;

import net.groundzero.app.Core;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AssaultHandler implements ItemHandler {

    @Override
    public boolean onLeftClick(Player player, ItemStack item) {
        Core.notifier.message(player, false, "Left Click Handled");
        return true;
    }

    @Override
    public boolean onRightClick(Player player, ItemStack item) {
        Core.notifier.message(player, false, "Right Click Handled");
        return true;
    }
}