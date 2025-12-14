package net.groundzero.item.handler;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AssaultHandler implements ItemHandler {

    @Override
    public boolean onLeftClick(Player player, ItemStack item) {
        // TODO: Fire projectile
        return true;
    }

    @Override
    public boolean onRightClick(Player player, ItemStack item) {
        // TODO: Reload
        return true;
    }
}