package net.groundzero.item;

import net.groundzero.app.Core;
import net.groundzero.item.handler.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for item creation and handler management
 */
public class ItemRegistry {

    public static final NamespacedKey KEY_ITEM_ID = new NamespacedKey(Core.plugin, "gz_item_id");

    private final Map<ItemType, ItemHandler> handlers = new EnumMap<>(ItemType.class);

    /**
     * Register all item handlers
     * Called during plugin initialization
     */
    public void init() {
        // Console
        registerHandler(ItemType.CONSOLE, new ConsoleHandler());

        // Personal Weapons
        registerHandler(ItemType.ASSAULT, new AssaultHandler());
//        registerHandler(ItemType.AUTO, new AutoHandler());
//        registerHandler(ItemType.SNIPER, new SniperHandler());
//        registerHandler(ItemType.RPG, new RpgHandler());
//        registerHandler(ItemType.CONCUSSIVE, new ConcussiveHandler());
//        registerHandler(ItemType.SMOKE, new SmokeHandler());

        // Supportive
//        registerHandler(ItemType.MEDKIT, new MedkitHandler());
//        registerHandler(ItemType.BLOCKS, new BlocksHandler());
//        registerHandler(ItemType.BRIDGE, new BridgeHandler());
//        registerHandler(ItemType.BUNKER, new BunkerHandler());
//        registerHandler(ItemType.ANTIEXP, new AntiExpHandler());
//        registerHandler(ItemType.PEARL, new PearlHandler());

        // Aerial Support
//        registerHandler(ItemType.AERIAL_SIMPLE, new AerialSimpleHandler());
//        registerHandler(ItemType.AERIAL_ARROW, new AerialArrowHandler());
//        registerHandler(ItemType.AERIAL_CLUSTER, new AerialClusterHandler());
//        registerHandler(ItemType.AERIAL_SPREADER, new AerialSpreaderHandler());
//        registerHandler(ItemType.AERIAL_CARPET, new AerialCarpetHandler());
//        registerHandler(ItemType.AERIAL_HACK, new AerialHackHandler());

        // Missiles
//        registerHandler(ItemType.MISSILE_SIMPLE, new MissileSimpleHandler());
//        registerHandler(ItemType.MISSILE_POISON, new MissilePoisonHandler());
//        registerHandler(ItemType.MISSILE_BUNKER, new MissileBunkerHandler());
//        registerHandler(ItemType.MISSILE_HIGHEXP, new MissileHeHandler());
//        registerHandler(ItemType.MISSILE_NUCLEAR, new MissileNuclearHandler());
//        registerHandler(ItemType.MISSILE_ABM, new MissileAbmHandler());
    }

    /**
     * Create an ItemStack for given type
     */
    public ItemStack createItem(ItemType type, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();

        // Set custom model data for resource pack
        meta.setCustomModelData(type.customModelData);

        // Tag with PDC
        meta.getPersistentDataContainer().set(
                KEY_ITEM_ID,
                PersistentDataType.STRING,
                type.id
        );

        // Set display name
        meta.setDisplayName("§b" + type.displayName);

        // Set lore based on item type
        List<String> lore = new ArrayList<>();
        lore.add("");

        if (type == ItemType.CONSOLE) {
            lore.add("§7Left click: §fOpen shop");
            lore.add("§7Right click: §fSwap hotbar");
        } else if (type == ItemType.ASSAULT || type == ItemType.AUTO ||
                type == ItemType.SNIPER || type == ItemType.RPG) {
            // Non-consumable weapons (TODO: add ammo system later)
            lore.add("§7Left click: §fFire");
            lore.add("§7Right click: §fReload");
        } else {
            // All other items are consumable
            lore.add("§7Right click: §fUse");
        }

        meta.setLore(lore);

        // Hide all flags for cleaner look
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Get ItemType from ItemStack by reading PDC
     */
    public ItemType getType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        String id = item.getItemMeta()
                .getPersistentDataContainer()
                .get(KEY_ITEM_ID, PersistentDataType.STRING);

        return ItemType.fromId(id);
    }

    /**
     * Check if ItemStack is a GroundZero item
     */
    public boolean isGZItem(ItemStack item) {
        return getType(item) != null;
    }

    /**
     * Register a handler for an item type
     */
    public void registerHandler(ItemType type, ItemHandler handler) {
        handlers.put(type, handler);
    }

    /**
     * Get handler for an item type
     */
    public ItemHandler getHandler(ItemType type) {
        return handlers.get(type);
    }
}