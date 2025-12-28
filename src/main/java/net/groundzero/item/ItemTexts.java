package net.groundzero.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized text management for all items.
 *
 * Three text contexts:
 * - Shop GUI: Full description + actions + price + income
 * - Inventory: Simple action hints only
 * - Category: Section headers in shop
 */
public final class ItemTexts {

    private ItemTexts() {}

    /* =========================================================
       SHOP GUI TEXTS (displayed in shop menu)
       ========================================================= */

    public static String shopName(ItemType type) {
        return "§b" + type.displayName;
    }

    public static List<String> shopLore(ItemType type, int price, double incomeAdd, int amount) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(getDescription(type));
        lore.add("");
        lore.add(getAmountDescription(type, amount));
        lore.add("");
        lore.addAll(getActionDescription(type));
        lore.add("");
        lore.add("§7Price: §e" + price + " §7Plasma");
        lore.add("§7Income: §a+" + String.format("%.1f", incomeAdd) + "§7/s");
        return lore;
    }

    /* =========================================================
       INVENTORY TEXTS (when item is in player inventory)
       ========================================================= */

    public static String inventoryName(ItemType type) {
        return "§b" + type.displayName;
    }

    public static List<String> inventoryLore(ItemType type) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(getActionDescription(type));
        return lore;
    }

    /* =========================================================
       CATEGORY TEXTS (shop section headers)
       ========================================================= */

    public static String categoryName(ItemType type) {
        return switch (type) {
            case GUI_WEAPON -> "§6════ WEAPONS ════";
            case GUI_SUPPORT -> "§a════ SUPPORT ════";
            case GUI_AERIAL -> "§b════ AERIAL ════";
            case GUI_MISSILE -> "§c════ MISSILES ════";
            default -> "§f" + type.displayName;
        };
    }

    public static List<String> categoryLore(ItemType type) {
        return switch (type) {
            case GUI_WEAPON -> List.of(
                    "",
                    "§7Primary combat tools designed for direct engagements",
                    "§7Each weapon features unique fire rate, accuracy, and ammo behavior",
                    "§7Positioning and aim directly translate into damage output",
                    "",
                    "\"The core choice for players who win fights head-on\""
            );
            case GUI_SUPPORT -> List.of(
                    "",
                    "§7Utility items that influence the battlefield beyond raw damage",
                    "§7Focused on survival, control, and tactical advantages",
                    "§7Effective alone, but significantly stronger in coordinated play",
                    "",
                    "\"Best suited for players who think ahead and control tempo\""
            );
            case GUI_AERIAL -> List.of(
                    "",
                    "§7High-impact support deployed from above the battlefield",
                    "§7Applies pressure over a wide area and disrupts enemy formations",
                    "§7Proper timing can completely turn the tide of a fight",
                    "",
                    "\"Built for players who want to reshape the battlefield itself\""
            );
            case GUI_MISSILE -> List.of(
                    "",
                    "§7Precision strike weapons with extreme burst potential",
                    "§7Limited usage, but devastating when used correctly",
                    "§7Highly effective against cover, defenses, and clustered targets",
                    "",
                    "\"One shot can decide the entire engagement\""
            );
            default -> List.of();
        };
    }

    /* =========================================================
       ITEM DESCRIPTIONS (multi-line summary)
       ========================================================= */

    private static List<String> getDescription(ItemType type) {
        return switch (type) {
            // Console
            case CONSOLE -> List.of(
                    "§7Access shop and manage loadout"
            );

            // Weapons - Magazine based
            case ASSAULT -> List.of(
                    "§7Balanced assault rifle",
                    "§7Reliable in all situations"
            );
            case AUTO -> List.of(
                    "§7High fire rate rifle",
                    "§7Requires overdrive charge to fire"
            );
            case SNIPER -> List.of(
                    "§7Precision long-range rifle",
                    "§7Devastating at distance"
            );
            case RPG -> List.of(
                    "§7Explosive rocket launcher",
                    "§7Blast enemies or rocket jump"
            );

            // Weapons - Consumable
            case CONCUSSIVE -> List.of(
                    "§7Stun shell projectile",
                    "§7Disables enemies on impact"
            );
            case SMOKE -> List.of(
                    "§7Smoke grenade",
                    "§7Creates visual cover on impact"
            );

            // Support
            case MEDKIT -> List.of(
                    "§7Emergency healing kit",
                    "§7Restores health instantly"
            );
            case BLOCKS -> List.of(
                    "§7Dummy building blocks",
                    "§7Quick defensive construction"
            );
            case BRIDGE -> List.of(
                    "§7Instant bridge deployer",
                    "§7Cross gaps rapidly"
            );
            case BUNKER -> List.of(
                    "§7Protective bunker structure",
                    "§7Drops defensive cover"
            );
            case ANTIEXP -> List.of(
                    "§7Explosion nullifier field",
                    "§7Blocks blast damage in area"
            );
            case PEARL -> List.of(
                    "§7Ender pearl teleporter",
                    "§7Teleport to impact location"
            );

            // Aerial
            case AERIAL_SIMPLE -> List.of(
                    "§7Basic airstrike call",
                    "§7Single target bombardment"
            );
            case AERIAL_ARROW -> List.of(
                    "§7Arrow rain strike",
                    "§7Piercing projectile barrage"
            );
            case AERIAL_CLUSTER -> List.of(
                    "§7Cluster bomb strike",
                    "§7Splits into multiple munitions"
            );
            case AERIAL_SPREADER -> List.of(
                    "§7Spreader strike pattern",
                    "§7Wide area saturation"
            );
            case AERIAL_CARPET -> List.of(
                    "§7Carpet bombing run",
                    "§7Line of destruction"
            );
            case AERIAL_HACK -> List.of(
                    "§7Remote hack device",
                    "§7Redirects enemy airstrikes"
            );

            // Missiles
            case MISSILE_SIMPLE -> List.of(
                    "§7Basic guided missile",
                    "§7Reliable targeted strike"
            );
            case MISSILE_POISON -> List.of(
                    "§7Chemical warhead missile",
                    "§7Poisons area on impact"
            );
            case MISSILE_BUNKER -> List.of(
                    "§7Bunker buster missile",
                    "§7Penetrates defenses"
            );
            case MISSILE_HIGHEXP -> List.of(
                    "§7High-explosive missile",
                    "§7Massive blast radius"
            );
            case MISSILE_NUCLEAR -> List.of(
                    "§7Nuclear warhead missile",
                    "§7Catastrophic devastation"
            );
            case MISSILE_ABM -> List.of(
                    "§7Anti-ballistic missile",
                    "§7Intercepts enemy missiles"
            );

            // GUI (shouldn't be called)
            case GUI_WEAPON, GUI_SUPPORT, GUI_AERIAL, GUI_MISSILE -> List.of(
                    "§7Category"
            );

            default -> List.of(
                    "§7No description available"
            );
        };
    }

    /* =========================================================
       AMOUNT DESCRIPTIONS (purchase quantity)
       ========================================================= */

    private static String getAmountDescription(ItemType type, int amount) {
        // Magazine-based weapons
        if (type == ItemType.ASSAULT || type == ItemType.AUTO ||
                type == ItemType.SNIPER || type == ItemType.RPG) {
            return "§fBuy §a1 §fmagazine with §a" + amount + "§f ammo";
        }

        // All other items
        if (amount == 1) {
            return "§fBuy §a1§f item";
        }
        return "§fBuy §a" + amount + "§f items";
    }

    /* =========================================================
       ACTION DESCRIPTIONS (left/right click hints)
       ========================================================= */

    private static List<String> getActionDescription(ItemType type) {
        return switch (type) {
            // Console
            case CONSOLE -> List.of(
                    "§e[L]§f Open Shop",
                    "§e[R]§f Swap Hotbar"
            );

            // Weapons - Non-consumable (ammo-based)
            case ASSAULT -> List.of(
                    "§e[L]§f Fire",
                    "§e[R]§f ADS Mode (Toggle)"
            );
            case AUTO -> List.of(
                    "§e[L]§f Auto Fire (Toggle)",
                    "§e[R]§f Overdrive (Toggle)"
            );
            case SNIPER -> List.of(
                    "§e[L]§f Fire",
                    "§e[R]§f Scope (Toggle)"
            );
            case RPG -> List.of(
                    "§e[L]§f Fire",
                    "§e[R]§f Rocket Jump"
            );

            // Weapons - Consumable
            case CONCUSSIVE -> List.of(
                    "§e[R]§f Throw Shell"
            );
            case SMOKE -> List.of(
                    "§e[R]§f Throw Grenade"
            );

            // Support
            case MEDKIT -> List.of(
                    "§e[R]§f Heal"
            );
            case BLOCKS -> List.of(
                    "§e[R]§f Place"
            );
            case BRIDGE -> List.of(
                    "§e[R]§f Deploy Bridge"
            );
            case BUNKER -> List.of(
                    "§e[R]§f Deploy Bunker"
            );
            case ANTIEXP -> List.of(
                    "§e[R]§f Activate Shield"
            );
            case PEARL -> List.of(
                    "§e[R]§f Throw"
            );

            // Aerial
            case AERIAL_SIMPLE, AERIAL_ARROW, AERIAL_CLUSTER,
                 AERIAL_SPREADER, AERIAL_CARPET -> List.of(
                    "§e[R]§f Call Strike"
            );
            case AERIAL_HACK -> List.of(
                    "§e[R]§f Fire Hack"
            );

            // Missiles
            case MISSILE_SIMPLE, MISSILE_POISON, MISSILE_BUNKER,
                 MISSILE_HIGHEXP, MISSILE_NUCLEAR -> List.of(
                    "§e[L]§f Set Target",
                    "§e[R]§f Launch"
            );
            case MISSILE_ABM -> List.of(
                    "§e[R]§f Fire Interceptor"
            );

            // GUI categories
            case GUI_WEAPON, GUI_SUPPORT, GUI_AERIAL, GUI_MISSILE -> List.of();

            default -> List.of();
        };
    }

    /**
     * Get action bar string for item type.
     * For weapons with magazine system, shows magazine/reserve and reload state.
     *
     * @param type Item type
     * @param magazine Current magazine ammo
     * @param reserve Reserve ammo
     * @param isReloading Whether currently reloading
     * @return ActionBar string
     */
    public static String getActionBar(ItemType type, int magazine, int reserve, boolean isReloading) {
        List<String> actions = getActionDescription(type);
        if (actions.isEmpty()) return "";

        String base = String.join(" ", actions);

        // Append ammo display for magazine-based weapons
        if (type == ItemType.ASSAULT || type == ItemType.AUTO ||
                type == ItemType.SNIPER || type == ItemType.RPG) {

            if (isReloading) {
                // Show reloading indicator with current ammo state
                base += " §c[RELOADING]§f " + magazine + "/" + reserve;
            } else if (magazine == 0 && reserve == 0) {
                // Empty ammo display: <red>0/0</red>
                base += " §e[Ammo] §c0/0";
            } else {
                // Normal ammo display: magazine/reserve
                base += " §e[Ammo]§f " + magazine + "/" + reserve;
            }
        }

        return base;
    }

    /* =========================================================
       APPLY METHODS (convenience helpers)
       ========================================================= */

    public static void applyShopText(ItemStack item, ItemType type, int price, double incomeAdd, int amount) {
        if (item == null || type == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(shopName(type));
        meta.setLore(shopLore(type, price, incomeAdd, amount));
        item.setItemMeta(meta);
    }

    /**
     * Apply inventory text to ItemStack
     */
    public static void applyInventoryText(ItemStack item, ItemType type) {
        if (item == null || type == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(inventoryName(type));
        meta.setLore(inventoryLore(type));
        item.setItemMeta(meta);
    }

    /**
     * Apply category header text to ItemStack
     */
    public static void applyCategoryText(ItemStack item, ItemType type) {
        if (item == null || type == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(categoryName(type));
        meta.setLore(categoryLore(type));
        item.setItemMeta(meta);
    }
}