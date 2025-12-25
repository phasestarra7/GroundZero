package net.groundzero.ui.options;

import net.groundzero.item.ItemType;

/**
 * Shop category display items (not purchasable).
 * These are just visual separators in the shop GUI.
 */
public enum ShopCategory {
    WEAPON(ItemType.GUI_WEAPON, 10),
    SUPPORT(ItemType.GUI_SUPPORT, 19),
    AERIAL(ItemType.GUI_AERIAL, 28),
    MISSILE(ItemType.GUI_MISSILE, 37);

    public final ItemType type;
    public final int slot;

    ShopCategory(ItemType type, int slot) {
        this.type = type;
        this.slot = slot;
    }

    public static ShopCategory fromSlot(int slot) {
        for (ShopCategory cat : values()) {
            if (cat.slot == slot) return cat;
        }
        return null;
    }

    public static boolean isCategorySlot(int slot) {
        return fromSlot(slot) != null;
    }
}