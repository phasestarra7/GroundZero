package net.groundzero.ui.options;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;

/**
 * Purchasable shop items (24 items).
 * Price and income values are retrieved from GameConfig.
 */
public enum ShopItem {

    /* ===== Weapons (6) ===== */
    ASSAULT(ItemType.ASSAULT, 11),
    AUTO(ItemType.AUTO, 12),
    SNIPER(ItemType.SNIPER, 13),
    RPG(ItemType.RPG, 14),
    CONCUSSIVE(ItemType.CONCUSSIVE, 15),
    SMOKE(ItemType.SMOKE, 16),

    /* ===== Support (6) ===== */
    MEDKIT(ItemType.MEDKIT, 20),
    BLOCKS(ItemType.BLOCKS, 21),
    BRIDGE(ItemType.BRIDGE, 22),
    BUNKER(ItemType.BUNKER, 23),
    ANTIEXP(ItemType.ANTIEXP, 24),
    PEARL(ItemType.PEARL, 25),

    /* ===== Aerial (6) ===== */
    AERIAL_SIMPLE(ItemType.AERIAL_SIMPLE, 29),
    AERIAL_ARROW(ItemType.AERIAL_ARROW, 30),
    AERIAL_CLUSTER(ItemType.AERIAL_CLUSTER, 31),
    AERIAL_SPREADER(ItemType.AERIAL_SPREADER, 32),
    AERIAL_CARPET(ItemType.AERIAL_CARPET, 33),
    AERIAL_HACK(ItemType.AERIAL_HACK, 34),

    /* ===== Missile (6) ===== */
    MISSILE_SIMPLE(ItemType.MISSILE_SIMPLE, 38),
    MISSILE_POISON(ItemType.MISSILE_POISON, 39),
    MISSILE_BUNKER(ItemType.MISSILE_BUNKER, 40),
    MISSILE_HIGHEXP(ItemType.MISSILE_HIGHEXP, 41),
    MISSILE_NUCLEAR(ItemType.MISSILE_NUCLEAR, 42),
    MISSILE_ABM(ItemType.MISSILE_ABM, 43);

    public final ItemType type;
    public final int slot;

    ShopItem(ItemType type, int slot) {
        this.type = type;
        this.slot = slot;
    }

    /**
     * Get price from GameConfig
     */
    public int getPrice() {
        return switch (this) {
            // Weapons
            case ASSAULT -> Core.gameConfig.assaultPrice;
            case AUTO -> Core.gameConfig.autoPrice;
            case SNIPER -> Core.gameConfig.sniperPrice;
            case RPG -> Core.gameConfig.rpgPrice;
            case CONCUSSIVE -> Core.gameConfig.concussivePrice;
            case SMOKE -> Core.gameConfig.smokePrice;

            // Support
            case MEDKIT -> Core.gameConfig.medkitPrice;
            case BLOCKS -> Core.gameConfig.blocksPrice;
            case BRIDGE -> Core.gameConfig.bridgePrice;
            case BUNKER -> Core.gameConfig.bunkerPrice;
            case ANTIEXP -> Core.gameConfig.antiExpPrice;
            case PEARL -> Core.gameConfig.pearlPrice;

            // Aerial
            case AERIAL_SIMPLE -> Core.gameConfig.aerialSimplePrice;
            case AERIAL_ARROW -> Core.gameConfig.aerialArrowPrice;
            case AERIAL_CLUSTER -> Core.gameConfig.aerialClusterPrice;
            case AERIAL_SPREADER -> Core.gameConfig.aerialSpreaderPrice;
            case AERIAL_CARPET -> Core.gameConfig.aerialCarpetPrice;
            case AERIAL_HACK -> Core.gameConfig.aerialHackPrice;

            // Missile
            case MISSILE_SIMPLE -> Core.gameConfig.missileSimplePrice;
            case MISSILE_POISON -> Core.gameConfig.missilePoisonPrice;
            case MISSILE_BUNKER -> Core.gameConfig.missileBunkerPrice;
            case MISSILE_HIGHEXP -> Core.gameConfig.missileHighExpPrice;
            case MISSILE_NUCLEAR -> Core.gameConfig.missileNuclearPrice;
            case MISSILE_ABM -> Core.gameConfig.missileAbmPrice;
        };
    }

    /**
     * Get income addition from GameConfig
     */
    public double getIncomeAdd() {
        return switch (this) {
            // Weapons
            case ASSAULT -> Core.gameConfig.assaultIncome;
            case AUTO -> Core.gameConfig.autoIncome;
            case SNIPER -> Core.gameConfig.sniperIncome;
            case RPG -> Core.gameConfig.rpgIncome;
            case CONCUSSIVE -> Core.gameConfig.concussiveIncome;
            case SMOKE -> Core.gameConfig.smokeIncome;

            // Support
            case MEDKIT -> Core.gameConfig.medkitIncome;
            case BLOCKS -> Core.gameConfig.blocksIncome;
            case BRIDGE -> Core.gameConfig.bridgeIncome;
            case BUNKER -> Core.gameConfig.bunkerIncome;
            case ANTIEXP -> Core.gameConfig.antiExpIncome;
            case PEARL -> Core.gameConfig.pearlIncome;

            // Aerial
            case AERIAL_SIMPLE -> Core.gameConfig.aerialSimpleIncome;
            case AERIAL_ARROW -> Core.gameConfig.aerialArrowIncome;
            case AERIAL_CLUSTER -> Core.gameConfig.aerialClusterIncome;
            case AERIAL_SPREADER -> Core.gameConfig.aerialSpreaderIncome;
            case AERIAL_CARPET -> Core.gameConfig.aerialCarpetIncome;
            case AERIAL_HACK -> Core.gameConfig.aerialHackIncome;

            // Missile
            case MISSILE_SIMPLE -> Core.gameConfig.missileSimpleIncome;
            case MISSILE_POISON -> Core.gameConfig.missilePoisonIncome;
            case MISSILE_BUNKER -> Core.gameConfig.missileBunkerIncome;
            case MISSILE_HIGHEXP -> Core.gameConfig.missileHighExpIncome;
            case MISSILE_NUCLEAR -> Core.gameConfig.missileNuclearIncome;
            case MISSILE_ABM -> Core.gameConfig.missileAbmIncome;
        };
    }

    /**
     * Get amount per purchase from GameConfig
     */
    public int getAmount() {
        return switch (this) {
            // Weapons
            case ASSAULT -> Core.gameConfig.assaultAmount;
            case AUTO -> Core.gameConfig.autoAmount;
            case SNIPER -> Core.gameConfig.sniperAmount;
            case RPG -> Core.gameConfig.rpgAmount;
            case CONCUSSIVE -> Core.gameConfig.concussiveAmount;
            case SMOKE -> Core.gameConfig.smokeAmount;

            // Support
            case MEDKIT -> Core.gameConfig.medkitAmount;
            case BLOCKS -> Core.gameConfig.blocksAmount;
            case BRIDGE -> Core.gameConfig.bridgeAmount;
            case BUNKER -> Core.gameConfig.bunkerAmount;
            case ANTIEXP -> Core.gameConfig.antiExpAmount;
            case PEARL -> Core.gameConfig.pearlAmount;

            // Aerial
            case AERIAL_SIMPLE -> Core.gameConfig.aerialSimpleAmount;
            case AERIAL_ARROW -> Core.gameConfig.aerialArrowAmount;
            case AERIAL_CLUSTER -> Core.gameConfig.aerialClusterAmount;
            case AERIAL_SPREADER -> Core.gameConfig.aerialSpreaderAmount;
            case AERIAL_CARPET -> Core.gameConfig.aerialCarpetAmount;
            case AERIAL_HACK -> Core.gameConfig.aerialHackAmount;

            // Missile
            case MISSILE_SIMPLE -> Core.gameConfig.missileSimpleAmount;
            case MISSILE_POISON -> Core.gameConfig.missilePoisonAmount;
            case MISSILE_BUNKER -> Core.gameConfig.missileBunkerAmount;
            case MISSILE_HIGHEXP -> Core.gameConfig.missileHighExpAmount;
            case MISSILE_NUCLEAR -> Core.gameConfig.missileNuclearAmount;
            case MISSILE_ABM -> Core.gameConfig.missileAbmAmount;
        };
    }

    public static ShopItem fromSlot(int slot) {
        for (ShopItem item : values()) {
            if (item.slot == slot) return item;
        }
        return null;
    }

    public static ShopItem fromType(ItemType type) {
        if (type == null) return null;
        for (ShopItem item : values()) {
            if (item.type == type) return item;
        }
        return null;
    }
}