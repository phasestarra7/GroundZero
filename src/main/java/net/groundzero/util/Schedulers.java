package net.groundzero.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Centralized scheduler helper that tracks all scheduled jobs
 * and can cancel them in one shot.
 */
public final class Schedulers {

    private final Plugin plugin;
    // We track the BukkitRunnable instances, because they are the actual tasks.
    private final Set<BukkitRunnable> tasks = ConcurrentHashMap.newKeySet();

    public Schedulers(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Schedule a one-shot task on the main thread after delayTicks.
     * The task is auto-removed from tracking right after it runs.
     */
    public BukkitTask runLater(Runnable r, long delayTicks) {
        Objects.requireNonNull(r, "r");

        BukkitRunnable br = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "[Schedulers] Exception in runLater", t);
                } finally {
                    // auto-clean after one-shot execution
                    tasks.remove(this);
                }
            }
        };

        tasks.add(br);
        return br.runTaskLater(plugin, Math.max(0L, delayTicks));
    }

    public BukkitTask runTimer(Runnable r, long delay, long period) {
        Objects.requireNonNull(r, "r");

        BukkitRunnable br = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "[Schedulers] Exception in runTimer", t);
                }
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                tasks.remove(this);
            }
        };

        tasks.add(br);
        return br.runTaskTimer(plugin, Math.max(0L, delay), Math.max(1L, period));
    }

    /**
     * Cancel a single tracked task, if we can find it.
     * Safe to call multiple times or with null.
     */
    public void cancelTask(BukkitTask task) {
        if (task == null) return;

        try {
            int targetId = task.getTaskId();

            for (BukkitRunnable br : tasks) {
                try {
                    if (br.getTaskId() == targetId) {
                        // This will also remove it from the scheduler.
                        br.cancel();
                        // Make sure we also drop it from our tracking set.
                        tasks.remove(br);
                        break;
                    }
                } catch (Throwable ignored) {
                    // Ignore per-task errors so we still try to clean others.
                }
            }
        } catch (Throwable ignored) {
            // If anything goes wrong, we just do nothing.
        }
    }

    /**
     * Cancel and clear all tracked tasks (safe to call multiple times).
     */
    public void cancelAll() {
        for (BukkitRunnable br : tasks) {
            try {
                br.cancel();
            } catch (Throwable ignored) {
                // ignore individual failures so we always clear the set
            }
        }
        tasks.clear();
    }
}
