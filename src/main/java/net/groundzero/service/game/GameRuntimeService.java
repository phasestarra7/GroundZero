package net.groundzero.service.game;

import net.groundzero.app.Core;
import net.groundzero.service.GameService;
import net.groundzero.service.tick.TickBus;

import java.util.UUID;

/**
 * Global per-tick runtime controller:
 * - Decrements remaining time every tick.
 * - Every second, adds income to plasma and score.
 * - Ends the game when time reaches zero.
 */
public final class GameRuntimeService implements TickBus.Tickable, GameService {

    private boolean running = false;

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
    }

    @Override
    public void reset() {
        // No internal state to clear
    }

    @Override
    public void onTick(int currentTick) {
        if (!Core.session.state().isIngame()) return;

        // 1) time
        int left = Core.session.remainingTicks();
        if (left > 0) {
            Core.session.setRemainingTicks(left - 1);
        } else {
            Core.game.endMatch();
            return;
        }

        // 2) income per second -> plasma
        if (currentTick % 20 == 0) {
            for (UUID id : Core.session.getParticipantsView()) {
                double incPerSec = Core.session.getIncomeMap()
                        .getOrDefault(id, 0.0);

                double plasma = Core.session.getPlasmaMap()
                        .getOrDefault(id, Core.gameConfig.basePlasma);
                Core.session.getPlasmaMap().put(id, plasma + incPerSec);
            }
        }
    }
}