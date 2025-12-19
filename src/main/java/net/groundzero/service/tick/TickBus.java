package net.groundzero.service.tick;

import net.groundzero.app.Core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TickBus {

    public interface Tickable {
        void onTick(int currentTick);
    }

    private final Set<Tickable> subs = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;
    private int currentTick = 0;

    public int getCurrentTick() { return currentTick; }

    public void register(Tickable t) {
        if (t != null) subs.add(t);
    }

    public void unregister(Tickable t) {
        if (t != null) subs.remove(t);
    }

    public void start() {
        if (running) return;
        running = true;
        scheduleNext();
    }

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
                for (Tickable t : subs.toArray(new Tickable[0])) {
                    try {
                        t.onTick(currentTick);
                    } catch (Throwable ex) {
                        Core.plugin.getLogger().warning("[TickBus] Tickable threw exception: " + ex.getMessage());
                    }
                }
            } finally {
                if (running) scheduleNext();
            }
        }, 1L);
    }
}