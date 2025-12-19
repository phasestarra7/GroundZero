package net.groundzero.util;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class Schedulers {

    private final Plugin plugin;
    private final Set<BukkitRunnable> tasks = ConcurrentHashMap.newKeySet();

    public Schedulers(Plugin plugin) {
        this.plugin = plugin;
    }

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

    public void cancelTask(BukkitTask task) {
        if (task == null) return;

        try {
            int targetId = task.getTaskId();

            for (BukkitRunnable br : tasks) {
                try {
                    if (br.getTaskId() == targetId) {
                        br.cancel();
                        tasks.remove(br);
                        break;
                    }
                } catch (Throwable t) {
                    plugin.getLogger().fine("[Schedulers] Failed to cancel task: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("[Schedulers] Error during task cancellation: " + t.getMessage());
        }
    }

    public void cancelAll() {
        for (BukkitRunnable br : tasks) {
            try {
                br.cancel();
            } catch (Throwable t) {
                plugin.getLogger().fine("[Schedulers] Failed to cancel task during cancelAll: " + t.getMessage());
            }
        }
        tasks.clear();
    }
}