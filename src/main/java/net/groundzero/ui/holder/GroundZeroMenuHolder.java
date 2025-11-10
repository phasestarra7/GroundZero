package net.groundzero.ui.holder;

import net.groundzero.ui.MenuType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Custom holder to reliably identify our menus. */
public final class GroundZeroMenuHolder implements InventoryHolder {
    private final MenuType type;

    public GroundZeroMenuHolder(MenuType type) { this.type = type; }

    public MenuType type() { return type; }

    @Override public Inventory getInventory() { return null; } // not used
}
