package net.groundzero.item.handler;

import net.groundzero.app.Core;
import net.groundzero.service.player.PlayerGameState;
import net.groundzero.util.Notifier;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ConsoleHandler implements ItemHandler {

    @Override
    public boolean onLeftClick(Player player, ItemStack item) {
        // Left click: Open shop
        Core.guiService.openShop(player);
        return true;
    }

    @Override
    public boolean onRightClick(Player player, ItemStack item) {
        // Right click: Hotbar swap
        PlayerGameState state = Core.playerStates.getOrCreate(player.getUniqueId());

        swapHotbar(player);
        state.toggleHotbarSwap();

        Core.notifier.sound(player.getUniqueId(), Sound.ITEM_ARMOR_EQUIP_CHAIN, Notifier.PitchLevel.MID);

        return true;
    }

    /**
     * Swap hotbar slots 0-7 with bottom inventory row slots 27-34
     * Console (slot 8 / 35) stays in place
     */
    private void swapHotbar(Player player) {
        Inventory inv = player.getInventory();

        // Swap only slots 0-7 (hotbar) with slots 27-34 (bottom inventory row)
        // Slot 8 (console) and slot 35 remain unchanged
        for (int i = 0; i < 8; i++) {
            ItemStack hotbar = inv.getItem(i);
            ItemStack bottom = inv.getItem(27 + i);

            inv.setItem(i, bottom);
            inv.setItem(27 + i, hotbar);
        }
    }
}