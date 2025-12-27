package net.groundzero.service.game;

import net.groundzero.app.Core;
import net.groundzero.item.ItemTexts;
import net.groundzero.item.ItemType;
import net.groundzero.service.effect.EffectSource;
import net.groundzero.service.player.PlayerGameState;
import net.groundzero.service.tick.TickBus;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.groundzero.service.GameService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ActionBar UI service with hybrid update system:
 * - 10-tick periodic updates for dynamic info (ammo, cooldown)
 * - Immediate updates on item change or handler request
 *
 * Design:
 * - Per-player lastUpdateTick prevents currentTick % interval issues
 * - lastMessage cache prevents redundant sendActionBar() calls
 * - ItemHandler.getActionBar() provides item-specific messages
 */
public final class ActionBarService implements TickBus.Tickable, GameService {

    // Update interval (default : 10 ticks = 0.5 seconds)
    private static final int UPDATE_INTERVAL_TICKS = Core.gameConfig.actionBarIntervalTicks;

    // Per-player state
    private final Map<UUID, Integer> lastUpdateTick = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();

    private boolean running = false;

    /* ===================== Lifecycle ===================== */

    public void start() {
        if (running) return;
        running = true;
        Core.tickBus.register(this);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        Core.tickBus.unregister(this);

        // Clear all ActionBars
        for (UUID id : Core.session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendActionBar(Component.empty());
            }
        }
    }

    @Override
    public void reset() {
        lastUpdateTick.clear();
        lastMessage.clear();
    }

    /* ===================== Tick-based Periodic Updates ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!running) return;
        if (!Core.session.state().isIngame()) return;

        for (UUID id : Core.session.getParticipantsView()) {
            // Check if enough time has passed since last update
            int lastTick = lastUpdateTick.getOrDefault(id, 0);
            if (currentTick - lastTick < UPDATE_INTERVAL_TICKS) {
                continue;
            }

            // Update this player
            updatePlayer(id, currentTick);
        }
    }

    /* ===================== Immediate Update (Event-driven) ===================== */

    /**
     * Force immediate update for a player.
     * Called by:
     * - PlayerItemHeldEvent (slot change)
     * - ItemHandler after action (fire, reload, etc.)
     */
    public void updateImmediately(UUID playerId) {
        if (!running) return;
        if (!Core.session.state().isIngame()) return;
        if (playerId == null) return;

        int currentTick = Core.tickBus.getCurrentTick();
        updatePlayer(playerId, currentTick);
    }

    /* ===================== Core Update Logic ===================== */

    /**
     * Update a single player's ActionBar.
     * Only sends if message changed (prevents redundant packets). <-- done by onTick
     */
    private void updatePlayer(UUID playerId, int currentTick) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        // Get item in main hand
        ItemStack item = player.getInventory().getItemInMainHand();

        // Generate message
        String message = generateMessage(player, item, currentTick);

        // Send ActionBar
        if (message.isEmpty()) {
            player.sendActionBar(Component.empty());
        } else {
            player.sendActionBar(Component.text(message.replace('&', '§')));
        }

        // Update cache
        lastMessage.put(playerId, message);
        lastUpdateTick.put(playerId, currentTick);
    }

    /* ===================== Message Generation ===================== */

    /**
     * Generate ActionBar message for current item.
     *
     * Returns:
     * - Item-specific message (from ItemHandler)
     * - Empty string (hide ActionBar for vanilla items)
     */
    private String generateMessage(Player player, ItemStack item, int currentTick) {
        if (item == null || item.getType().isAir()) {
            return ""; // Hide ActionBar for empty hand
        }

        // Check if GroundZero item
        ItemType type = Core.itemRegistry.getType(item);
        if (type == null) {
            return ""; // Hide ActionBar for vanilla items
        }

        // Generate message using ItemTexts
        return generateDefaultMessage(player, type, currentTick);
    }

    /**
     * Generate ActionBar message using ItemTexts.
     */
    private String generateDefaultMessage(Player player, ItemType type, int currentTick) {
        PlayerGameState state = Core.playerStates.getOrCreate(player.getUniqueId());

        int magazine = 0;
        int reserve = 0;
        boolean isReloading = false;

        // Get ammo state for magazine-based weapons
        switch (type) {
            case ASSAULT -> {
                magazine = state.getAssaultMagazine();
                reserve = state.getAssaultReserve();
                isReloading = state.isAssaultReloading(currentTick);
            }
            case AUTO -> {
                magazine = state.getAutoMagazine();
                reserve = state.getAutoReserve();
                isReloading = state.isAutoReloading(currentTick);
            }
            case SNIPER -> {
                magazine = state.getSniperMagazine();
                reserve = state.getSniperReserve();
                isReloading = state.isSniperReloading(currentTick);
            }
            case RPG -> {
                magazine = state.getRpgMagazine();
                reserve = state.getRpgReserve();
                isReloading = state.isRpgReloading(currentTick);
            }
            default -> {
                // Non-magazine items (Console, consumables, etc.)
                // Use 0/0 which will be handled appropriately by ItemTexts
            }
        }

        String base = ItemTexts.getActionBar(type, magazine, reserve, isReloading);
        return applyToggleStatesForActionBar(base, type, player.getUniqueId());
    }

    private String applyToggleStatesForActionBar(String base, ItemType type, UUID playerId) {
        // Strip literal "(Toggle)" everywhere for actionbar readability
        String out = base.replace(" (Toggle)", "");

        // Only add ON/OFF tags for the toggles you actually have
        return switch (type) {
            case ASSAULT -> {
                boolean isADS = Core.playerEffectService.hasSource(playerId, EffectSource.ASSAULT_ADS);
                yield out.replace("ADS Mode", "ADS Mode" + toggleDot(isADS));
            }
//            case AUTO -> {
//                boolean isAutoFire = Core.playerEffectService.hasSource(playerId, EffectSource.AUTO_AUTOFIRE);
//                boolean isOverdrive = Core.playerEffectService.hasSource(playerId, EffectSource.AUTO_OVERDRIVE);
//                String t = out.replace("Auto Fire", "Auto " + toggleTag(isAutoFire));
//                t = t.replace("Overdrive", "Overdrive " + toggleTag(isOverdrive));
//                yield t;
//            }
            case SNIPER -> {
                boolean isScoped = Core.playerEffectService.hasSource(playerId, EffectSource.SNIPER_SCOPED);
                yield out.replace("Scope", "Scope" + toggleDot(isScoped));
            }
            default -> out;
        };
    }

    /* ===================== Utility ===================== */

    private static String toggleDot(boolean on) {
        return on ? " §a●§f" : " §7●§f";
    }

    /**
     * Clear player's cached state (called on quit/death).
     */
    public void clearPlayer(UUID playerId) {
        lastUpdateTick.remove(playerId);
        lastMessage.remove(playerId);
    }
}