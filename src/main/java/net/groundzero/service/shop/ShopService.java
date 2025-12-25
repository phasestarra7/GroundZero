package net.groundzero.service.shop;

import net.groundzero.app.Core;
import net.groundzero.item.ItemTexts;
import net.groundzero.item.ItemType;
import net.groundzero.ui.options.ShopCategory;
import net.groundzero.ui.options.ShopItem;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.groundzero.service.GameService;

import java.util.UUID;

/**
 * Shop service - handles purchase logic.
 *
 * Flow:
 * 1. handleShopClick() receives raw slot
 * 2. Check if category slot (ignore) or item slot (process)
 * 3. processPurchase() handles plasma check, deduction, item giving, income add
 */
public class ShopService implements GameService {

    public ShopService() {}

    @Override
    public void reset() {
        // Nothing to reset currently
    }

    /* =========================================================
       MAIN ENTRY POINT
       ========================================================= */

    /**
     * Handle shop GUI click.
     * Called from GuiClickListener.
     */
    public void handleShopClick(UUID playerId, int rawSlot) {
        // 1. Check if category slot (not clickable)
        if (ShopCategory.isCategorySlot(rawSlot)) {
            // Category headers are just display, do nothing
            return;
        }

        // 2. Check if item slot
        ShopItem shopItem = ShopItem.fromSlot(rawSlot);
        if (shopItem == null) {
            // Empty slot or invalid
            return;
        }

        // 3. Process purchase
        processPurchase(playerId, shopItem);
    }

    /* =========================================================
       PURCHASE LOGIC
       ========================================================= */

    /**
     * Process item purchase.
     */
    private void processPurchase(UUID playerId, ShopItem shopItem) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        int price = shopItem.getPrice();
        double incomeAdd = shopItem.getIncomeAdd();
        ItemType type = shopItem.type;

        // 1. Check plasma
        if (!hasEnoughPlasma(playerId, price)) {
            onPurchaseFailed(player, type, price, PurchaseFailReason.NOT_ENOUGH_PLASMA);
            return;
        }

        // 2. Check inventory space (for consumables)
        if (!hasInventorySpace(player, type)) {
            onPurchaseFailed(player, type, price, PurchaseFailReason.INVENTORY_FULL);
            return;
        }

        // 3. Special handling for non-consumable weapons (ammo purchase)
        if (type.isNonConsumable() && type != ItemType.CONSOLE) {
            processAmmoPurchase(player, playerId, shopItem);
            return;
        }

        // 4. Deduct plasma
        deductPlasma(playerId, price);

        // 5. Add income
        addIncome(playerId, incomeAdd);

        // 6. Give item
        giveItem(player, type, shopItem.getAmount());

        // 7. Notify success
        onPurchaseSuccess(player, type, price, incomeAdd, shopItem.getAmount());
    }

    /**
     * Process ammo purchase for non-consumable weapons.
     * These weapons are given at start, shop only sells ammo.
     */
    private void processAmmoPurchase(Player player, UUID playerId, ShopItem shopItem) {
        int price = shopItem.getPrice();
        double incomeAdd = shopItem.getIncomeAdd();
        ItemType type = shopItem.type;

        // Deduct plasma
        deductPlasma(playerId, price);

        // Add income
        addIncome(playerId, incomeAdd);

        // Add ammo based on weapon type
        addAmmo(playerId, type);

        // Notify
        onAmmoPurchaseSuccess(player, type, price, incomeAdd);
    }

    /* =========================================================
       PLASMA OPERATIONS
       ========================================================= */

    private boolean hasEnoughPlasma(UUID playerId, int price) {
        double plasma = Core.session.getPlasmaMap().getOrDefault(playerId, 0.0);
        return plasma >= price;
    }

    private void deductPlasma(UUID playerId, int price) {
        double current = Core.session.getPlasmaMap().getOrDefault(playerId, 0.0);
        Core.session.getPlasmaMap().put(playerId, Math.max(0.0, current - price));
    }

    /* =========================================================
       INCOME OPERATIONS
       ========================================================= */

    private void addIncome(UUID playerId, double incomeAdd) {
        double current = Core.session.getIncomeMap().getOrDefault(playerId, Core.gameConfig.baseIncomePerSecond);
        Core.session.getIncomeMap().put(playerId, current + incomeAdd);
    }

    /* =========================================================
       ITEM GIVING
       ========================================================= */

    private boolean hasInventorySpace(Player player, ItemType type) {
        // Non-consumables don't need space (ammo only)
        if (type.isNonConsumable()) return true;

        // Check for empty slot
        return player.getInventory().firstEmpty() != -1;
    }

    private void giveItem(Player player, ItemType type, int amount) {
        // Create item shell with amount
        ItemStack item = Core.itemRegistry.createItem(type, amount);

        // Apply inventory text
        ItemTexts.applyInventoryText(item, type);

        // Add to inventory
        player.getInventory().addItem(item);
    }

    /* =========================================================
       AMMO OPERATIONS (PLACEHOLDER)
       ========================================================= */

    private void addAmmo(UUID playerId, ItemType weaponType) {
        // TODO: Implement ammo system properly
        // For now, just add a fixed amount

        var state = Core.playerStates.getOrCreate(playerId);

        switch (weaponType) {
            case ASSAULT -> {
                int current = state.getAssaultAmmo();
                state.setAssaultAmmo(current + Core.gameConfig.assaultMagazineSize);
            }
            case AUTO -> {
                // TODO: state.setAutoAmmo(current + Core.gameConfig.autoMagazineSize);
            }
            case SNIPER -> {
                // TODO: state.setSniperAmmo(current + Core.gameConfig.sniperMagazineSize);
            }
            case RPG -> {
                // TODO: state.setRpgAmmo(current + Core.gameConfig.rpgMagazineSize);
            }
            default -> {
                // Not a weapon
            }
        }
    }

    /* =========================================================
       NOTIFICATIONS
       ========================================================= */

    private enum PurchaseFailReason {
        NOT_ENOUGH_PLASMA,
        INVENTORY_FULL
    }

    private void onPurchaseFailed(Player player, ItemType type, int price, PurchaseFailReason reason) {
        String message = switch (reason) {
            case NOT_ENOUGH_PLASMA -> "Not enough plasma! Need §e" + price;
            case INVENTORY_FULL -> "Inventory full!";
        };

        Core.notifier.message(player, true, message);
        Core.notifier.sound(player, Sound.ENTITY_VILLAGER_NO, Notifier.PitchLevel.MID);
    }

    private void onPurchaseSuccess(Player player, ItemType type, int price, double incomeAdd, int amount) {
        String amountText = amount > 1 ? " x" + amount : "";
        Core.notifier.message(player, false,
                "Purchased §b" + type.displayName + amountText,
                "§7-" + price + " Plasma §8| §7+" + String.format("%.1f", incomeAdd) + "/s Income"
        );
        Core.notifier.sound(player, Sound.ENTITY_PLAYER_LEVELUP, Notifier.PitchLevel.HIGH);
    }

    private void onAmmoPurchaseSuccess(Player player, ItemType type, int price, double incomeAdd) {
        Core.notifier.message(player, false,
                "Purchased §b" + type.displayName + " Ammo",
                "§7-" + price + " Plasma §8| §7+" + String.format("%.1f", incomeAdd) + "/s Income"
        );
        Core.notifier.sound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, Notifier.PitchLevel.HIGH);
    }
}