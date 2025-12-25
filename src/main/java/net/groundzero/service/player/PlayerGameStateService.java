package net.groundzero.service.player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.groundzero.service.GameService;

/**
 * Central service for managing all player game states.
 *
 * This replaces scattered state management across:
 * - PlayerService (death/respawn)
 * - CombatIdleService (idle/camping)
 * - Future weapon/support services
 *
 * Usage:
 *   PlayerGameState state = Core.playerStates.getOrCreate(playerId);
 *   state.markDead();
 *   state.resetIdleToGrace(-200);
 */
public final class PlayerGameStateService implements GameService {

    private final Map<UUID, PlayerGameState> states = new ConcurrentHashMap<>();

    public PlayerGameStateService() {}

    /**
     * Reset all player states (called on session end).
     */
    @Override
    public void reset() {
        for (PlayerGameState state : states.values()) {
            state.resetAll();
        }
        states.clear();
    }

    /**
     * Get existing state or create new one for player.
     */
    public PlayerGameState getOrCreate(UUID playerId) {
        return states.computeIfAbsent(playerId, k -> new PlayerGameState());
    }

    /**
     * Get existing state (null if not exists).
     */
    public PlayerGameState get(UUID playerId) {
        return states.get(playerId);
    }

    /**
     * Check if player has state.
     */
    public boolean has(UUID playerId) {
        return states.containsKey(playerId);
    }

    /**
     * Remove player's state completely.
     */
    public void remove(UUID playerId) {
        PlayerGameState state = states.remove(playerId);
        if (state != null) {
            state.resetAll();
        }
    }

    /**
     * Reset specific player's state without removing entry.
     */
    public void resetPlayer(UUID playerId) {
        PlayerGameState state = states.get(playerId);
        if (state != null) {
            state.resetAll();
        }
    }
}