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
 * ActionBar UI service with per-tick updates.
 *
 * Design:
 * - Updates every tick for smooth cooldown display
 * - Dirty check prevents redundant packets
 * - Dead players get empty ActionBar
 * - Cooldown replaces action text (e.g., "[L] Fire" → "[L] 2.5")
 */
public final class ActionBarService implements TickBus.Tickable, GameService {

    private static final int UI_UPDATE_PERIOD_TICKS = Core.gameConfig.actionBarUpdatePeriodTicks;
    private int lastUiUpdateTick = 0;

    // Per-player last message cache for dirty check
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastSentTick = new ConcurrentHashMap<>();

    private boolean running = false;

    /* ===================== Lifecycle ===================== */

    @Override
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
        lastMessage.clear();
        lastSentTick.clear();
        lastUiUpdateTick = 0;
    }

    /* ===================== Tick-based Updates ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!running) return;
        if (!Core.session.state().isIngame()) return;

        if (currentTick - lastUiUpdateTick < UI_UPDATE_PERIOD_TICKS) return;
        lastUiUpdateTick = currentTick;

        for (UUID id : Core.session.getParticipantsView()) {
            updatePlayer(id, currentTick);
        }
    }

    /* ===================== Core Update Logic ===================== */

    private void updatePlayer(UUID playerId, int currentTick) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        // Dead players get empty ActionBar
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state != null && state.isDead()) {
            String cached = lastMessage.get(playerId);
            if (cached != null && !cached.isEmpty()) {
                player.sendActionBar(Component.empty());
                lastMessage.put(playerId, "");
            }
            return;
        }

        // Get item in main hand
        ItemStack item = player.getInventory().getItemInMainHand();

        // Generate message
        String message = generateMessage(player, item, currentTick);

        // Dirty check - skip if message unchanged
        String cached = lastMessage.get(playerId);
        int lastSent = lastSentTick.getOrDefault(playerId, 0);
        boolean forceUpdate = (currentTick - lastSent) >= Core.gameConfig.actionBarForceUpdatePeriodTicks;

        if (message.equals(cached) && !forceUpdate) {
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
        lastSentTick.put(playerId, currentTick);
    }

    /* ===================== Message Generation ===================== */

    private String generateMessage(Player player, ItemStack item, int currentTick) {
        if (item == null || item.getType().isAir()) {
            return "";
        }

        ItemType type = Core.itemRegistry.getType(item);
        if (type == null) {
            return "";
        }

        return generateDefaultMessage(player, type, currentTick);
    }

    private String generateDefaultMessage(Player player, ItemType type, int currentTick) {
        UUID playerId = player.getUniqueId();
        PlayerGameState state = Core.playerStates.getOrCreate(playerId);

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
                // Non-magazine items
            }
        }

        // Get base text from ItemTexts
        String base = ItemTexts.getActionBar(type, magazine, reserve, isReloading);

        // Apply toggle state
        base = applyToggleStates(base, type, playerId, state);

        // Apply cooldown state
        return applyCooldownOverlay(base, type, playerId);
    }

    /* ===================== Cooldown Overlay ===================== */

    /**
     * Replace action text with cooldown time if on cooldown.
     * e.g., "[L] Fire" → "[L] 2.5" when left action is on cooldown
     */
    private String applyCooldownOverlay(String base, ItemType type, UUID playerId) {
        // Get cooldown remaining ticks
        int leftCooldown = Core.cooldownService.getRemainingCooldown(playerId, type, true);
        int rightCooldown = Core.cooldownService.getRemainingCooldown(playerId, type, false);

        String result = base;

        // Replace left action if on cooldown
        if (leftCooldown > 0) {
            String cooldownText = formatCooldown(leftCooldown);
            // Pattern: "§e[L]§f <action>" → "§e[L]§c <cooldown>"
            result = result.replaceFirst("§e\\[L]§f [^§\\[]+", "§e[L]§c " + cooldownText + " ");
        }

        // Replace right action if on cooldown
        if (rightCooldown > 0) {
            String cooldownText = formatCooldown(rightCooldown);
            // Pattern: "§e[R]§f <action>" → "§e[R]§c <cooldown>"
            result = result.replaceFirst("§e\\[R]§f [^§\\[]+", "§e[R]§c " + cooldownText + " ");
        }

        return result;
    }

    /**
     * Format cooldown ticks to seconds with 1 decimal place.
     * e.g., 25 ticks → "1.3"
     */
    private String formatCooldown(int ticks) {
        double seconds = ticks / 20.0;
        return String.format("%.1f", seconds);
    }

    /* ===================== Toggle States ===================== */

    private String applyToggleStates(String base, ItemType type, UUID playerId, PlayerGameState state) {
        // Strip literal "(Toggle)" for cleaner display
        String out = base.replace(" (Toggle)", "");

        return switch (type) {
            case ASSAULT -> {
                boolean isADS = Core.playerEffectService.hasSource(playerId, EffectSource.ASSAULT_ADS);
                yield out.replace("ADS Mode", "ADS Mode" + toggleDot(isADS));
            }
            case AUTO -> {
                if (state == null) yield out;

                boolean isFiring = state.isAutoFireMode();
                boolean isOverdrive = state.isAutoOverdrive();
                int power = state.getAutoOverdriveStack();

                String result = out.replace("Auto Fire", "Auto Fire" + toggleDot(isFiring));
                result = result.replace("Overdrive", "Overdrive" + toggleDot(isOverdrive));
                result = result + " §e⚡" + power;

                yield result;
            }
            case SNIPER -> {
                boolean isScoped = Core.playerEffectService.hasSource(playerId, EffectSource.SNIPER_SCOPED);
                yield out.replace("Scope", "Scope" + toggleDot(isScoped));
            }
            default -> out;
        };
    }

    private static String toggleDot(boolean on) {
        return on ? " §a●§f" : " §7●§f";
    }

    /* ===================== Utility ===================== */

    /**
     * Clear player's cached state (called on quit/death).
     */
    public void clearPlayer(UUID playerId) {
        lastMessage.remove(playerId);
        lastSentTick.remove(playerId);
    }
}