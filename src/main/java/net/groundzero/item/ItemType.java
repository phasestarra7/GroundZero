package net.groundzero.item;

/**
 * All 25 custom items in GroundZero.
 * Each uses Material.PAPER with unique CustomModelData.
 *
 * Total count: 1 Console + 6 Weapons + 6 Supportive + 6 Airstrike + 6 Missiles = 25
 */
public enum ItemType {

    /* ===== Console (1) ===== */
    CONSOLE("gz_console", 9001, Category.CONSOLE, "Console"),

    /* ===== gui display (4) ===== */
    GUI_WEAPON("gz_gui_weapon", 8001, Category.GUI, "Weapon"),
    GUI_SUPPORT("gz_gui_support", 8002, Category.GUI, "Support"),
    GUI_AERIAL("gz_gui_aerial", 8003, Category.GUI, "Aerial"),
    GUI_MISSILE("gz_gui_missile", 8004, Category.GUI, "Missile"),

    /* ===== Weapons (6) - First 4 are non-consumable, ammo-based ===== */
    ASSAULT("gz_assault", 1001, Category.WEAPON, "Assault Rifle"),
    AUTO("gz_auto", 1002, Category.WEAPON, "Auto Rifle"),
    SNIPER("gz_sniper", 1003, Category.WEAPON, "Sniper Rifle"),
    RPG("gz_rpg", 1004, Category.WEAPON, "RPG"),
    CONCUSSIVE("gz_concussive", 1005, Category.WEAPON, "Concussive Shell"),
    SMOKE("gz_smoke", 1006, Category.WEAPON, "Smoke Grenade"),

    /* ===== Support (6) - All consumable ===== */
    MEDKIT("gz_medkit", 2001, Category.SUPPORT, "Medkit"),
    BLOCKS("gz_blocks", 2002, Category.SUPPORT, "Dummy Blocks"),
    BRIDGE("gz_bridge", 2003, Category.SUPPORT, "Bridge Plate"),
    BUNKER("gz_bunker", 2004, Category.SUPPORT, "Bunker"),
    ANTIEXP("gz_antiexp", 2005, Category.SUPPORT, "Anti-Explosive"),
    PEARL("gz_pearl", 2006, Category.SUPPORT, "Ender Pearl"),

    /* ===== Aerial (6) - All consumable ===== */
    AERIAL_SIMPLE("gz_aerial_simple", 3001, Category.AERIAL, "Simple Airstrike"),
    AERIAL_ARROW("gz_aerial_arrow", 3002, Category.AERIAL, "Arrow Rain"),
    AERIAL_CLUSTER("gz_aerial_cluster", 3003, Category.AERIAL, "Cluster Bomb"),
    AERIAL_SPREADER("gz_aerial_spreader", 3004, Category.AERIAL, "Spreader"),
    AERIAL_CARPET("gz_aerial_carpet", 3005, Category.AERIAL, "Carpet Bombing"),
    AERIAL_HACK("gz_aerial_hack", 3006, Category.AERIAL, "Remote Hack"),

    /* ===== Missile (6) - All consumable ===== */
    MISSILE_SIMPLE("gz_missile_simple", 4001, Category.MISSILE, "Simple Missile"),
    MISSILE_POISON("gz_missile_poison", 4002, Category.MISSILE, "Poison Missile"),
    MISSILE_BUNKER("gz_missile_bunker", 4003, Category.MISSILE, "Bunker Buster"),
    MISSILE_HIGHEXP("gz_missile_highexp", 4004, Category.MISSILE, "High-Explosive Missile"),
    MISSILE_NUCLEAR("gz_missile_nuclear", 4005, Category.MISSILE, "Nuclear Missile"),
    MISSILE_ABM("gz_missile_abm", 4006, Category.MISSILE, "ABM");

    public final String id;              // Unique ID for PDC
    public final int customModelData;    // For resource pack texture
    public final Category category;
    public final String displayName;

    ItemType(String id, int customModelData, Category category, String displayName) {
        this.id = id;
        this.customModelData = customModelData;
        this.category = category;
        this.displayName = displayName;
    }

    /**
     * Lookup ItemType by string ID from PDC
     */
    public static ItemType fromId(String id) {
        if (id == null) return null;
        for (ItemType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this item type is non-consumable (never removed from inventory)
     * Only Console and first 4 weapons (Assault, Auto, Sniper, RPG)
     */
    public boolean isNonConsumable() {
        return this == CONSOLE ||
                this == ASSAULT ||
                this == AUTO ||
                this == SNIPER ||
                this == RPG;
    }

    public boolean hasNoLeftClick() {
        boolean hasLeftClick = this == CONSOLE ||
                this == ASSAULT ||
                this == AUTO ||
                this == SNIPER ||
                this == RPG ||
                this == MISSILE_SIMPLE ||
                this == MISSILE_POISON ||
                this == MISSILE_BUNKER ||
                this == MISSILE_HIGHEXP ||
                this == MISSILE_NUCLEAR;
        return !hasLeftClick;
    }

    public enum Category {
        CONSOLE,      // Shop + hotbar swap
        GUI,          // gui display
        WEAPON,       // 4 non-consumable + 2 consumable
        SUPPORT,      // 6 consumable support items
        AERIAL,       // 6 airstrike variants
        MISSILE       // 6 missile variants
    }
}