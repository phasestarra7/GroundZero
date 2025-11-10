package net.groundzero.service.tick;

import net.groundzero.app.Core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central 1-tick loop for the whole plugin.
 * Register subsystems that need to run every tick (scoreboard, combat tags, cooldowns, DoT, etc.).
 */
public final class TickBus {

    /** Interface for subscribers. */
    public interface Tickable {
        /** Called every server tick while the bus is running. */
        void onTick(int currentTick);
    }

    private final Set<Tickable> subs = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;
    private int currentTick = 0;

    public void register(Tickable t) {
        if (t != null) subs.add(t);
    }

    public void unregister(Tickable t) {
        if (t != null) subs.remove(t);
    }

    /** Start the repeating 1-tick task. Safe to call multiple times. */
    public void start() {
        if (running) return;
        running = true;
        scheduleNext();
    }

    /** Stop the task and reset counters (subscribers are kept, your choice). */
    public void stop() {
        running = false;
        currentTick = 0;
        subs.clear();
    }

    private void scheduleNext() {
        if (!running) return;
        Core.schedulers.runLater(() -> {
            try {
                currentTick++;
                // iterate over a snapshot to avoid CME if subs mutate
                for (Tickable t : subs.toArray(new Tickable[0])) {
                    try { t.onTick(currentTick); } catch (Throwable ignored) {}
                }
            } finally {
                if (running) scheduleNext();
            }
        }, 1L);
    }
}
