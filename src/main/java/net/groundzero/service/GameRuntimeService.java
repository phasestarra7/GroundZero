package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.game.GameSession;
import net.groundzero.service.tick.TickBus;

import java.util.UUID;

/**
 * Global per-tick runtime controller:
 * - Decrements remaining time every tick.
 * - Every second, adds income to plasma and score.
 * - Ends the game when time reaches zero.
 * - Future: cooldowns / DoT / combat tags (subscribe here or via separate services).
 */
public final class GameRuntimeService implements TickBus.Tickable {

    private GameSession session;

    public void start(GameSession session) {
        this.session = session;
        Core.tickBus.register(this);
    }

    public void stop() {
        Core.tickBus.unregister(this);
        this.session = null;
    }

    @Override
    public void onTick(int currentTick) {
        if (session == null) return;
        if (!Core.session.state().isIngame()) return;

        // 1) time
        int left = session.remainingTicks();
        if (left > 0) {
            session.setRemainingTicks(left - 1);
        } else {
            // stop bus first is handled by GameManager; here we just end the game.
            Core.game.endGame();
            return;
        }

        // 2) income per second -> plasma & score
        if (currentTick % 20 == 0) {
            for (UUID id : session.getParticipantsView()) {
                double incPerSec = session.getIncomeMap()
                        .getOrDefault(id, 0.0);

                // plasma
                double plasma = session.getPlasmaMap()
                        .getOrDefault(id, Core.gameConfig.basePlasma);
                session.getPlasmaMap().put(id, plasma + incPerSec);
            }
        }

        // TODO:
        // 3) (future) cooldown / DoT / combat tag updates
        // e.g., cooldownService.onTick(currentTick);
    }
}
