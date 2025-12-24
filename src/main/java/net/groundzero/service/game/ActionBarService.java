package net.groundzero.service.game;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.item.handler.ItemHandler;
import net.groundzero.service.Resettable;
import net.groundzero.service.tick.TickBus;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
public final class ActionBarService implements TickBus.Tickable, Resettable {

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

    public void stop() {
        if (!running) return;
        reset();
    }

    @Override
    public void reset() {
        running = false;
        Core.tickBus.unregister(this);

        // Clear all ActionBars
        for (UUID id : Core.session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendActionBar(Component.empty());
            }
        }

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
        String message = generateMessage(player, item);

        // Check if changed
        String prev = lastMessage.get(playerId);
        if (message.equals(prev)) {
            // Message unchanged, but still update tick to prevent drift
            lastUpdateTick.put(playerId, currentTick);
            return;
        }

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
    private String generateMessage(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return ""; // Hide ActionBar for empty hand
        }

        // Check if GroundZero item
        ItemType type = Core.itemRegistry.getType(item);
        if (type == null) {
            return ""; // Hide ActionBar for vanilla items
        }

        // Get handler
        ItemHandler handler = Core.itemRegistry.getHandler(type);
        if (handler == null) {
            return ""; // No handler, hide ActionBar
        }

        // Ask handler for message
        String message = handler.getActionBar(player, item);
        if (message == null) {
            // Handler returned null, use default message
            return generateDefaultMessage(type);
        }

        return message;
    }

    /**
     * Generate default message for an item type.
     * Used when handler doesn't provide custom message.
     */
    private String generateDefaultMessage(ItemType type) {
        return switch (type) {
            case CONSOLE ->
                    "&e[L]&f Open Shop  &e[R]&f Swap Hotbar";

            case ASSAULT, AUTO, SNIPER, RPG ->
                    "&e[L]&f Fire  &e[R]&f Act";

            case CONCUSSIVE, SMOKE ->
                    "&e[R]&f Use";

            case MEDKIT ->
                    "&e[R]&f Heal";

            case BLOCKS ->
                    "&7Vanilla blocks";

            case BRIDGE ->
                    "&e[R]&f Deploy Bridge";

            case BUNKER ->
                    "&e[R]&f Deploy Bunker";

            case ANTIEXP ->
                    "&e[R]&f Deploy Anti-Explosive";

            case PEARL ->
                    "&7Vanilla ender pearl";

            case AERIAL_SIMPLE, AERIAL_ARROW, AERIAL_CLUSTER,
                 AERIAL_SPREADER, AERIAL_CARPET ->
                    "&e[R]&f Call Airstrike";

            case AERIAL_HACK ->
                    "&e[R]&f Fire Remote Hack";

            case MISSILE_SIMPLE, MISSILE_POISON, MISSILE_BUNKER,
                 MISSILE_HIGHEXP, MISSILE_NUCLEAR ->
                    "&e[L]&f Set Target  &e[R]&f Launch";

            case MISSILE_ABM ->
                    "&e[R]&f Fire ABM";

            default -> "";
        };
    }

    /* ===================== Utility ===================== */

    /**
     * Clear player's cached state (called on quit/death).
     */
    public void clearPlayer(UUID playerId) {
        lastUpdateTick.remove(playerId);
        lastMessage.remove(playerId);
    }
}