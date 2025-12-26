package net.groundzero.service.model;

import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;

import java.util.UUID;

/**
 * Tracks state for an active projectile model.
 *
 * Links:
 * - anchor: The physics entity (Arrow, Interaction, etc.)
 * - display: The visual entity (BlockDisplay/ItemDisplay)
 * - handler: The ModelHandler for this type
 */
public final class ModelData {

    private final UUID anchorId;
    private final UUID displayId;
    private final ModelType type;
    private final ModelHandler handler;
    private final int spawnTick;

    private Entity anchorCache;
    private Display displayCache;

    public ModelData(Entity anchor, Display display, ModelType type, ModelHandler handler, int spawnTick) {
        this.anchorId = anchor.getUniqueId();
        this.displayId = display.getUniqueId();
        this.type = type;
        this.handler = handler;
        this.spawnTick = spawnTick;

        this.anchorCache = anchor;
        this.displayCache = display;
    }

    public UUID getAnchorId() {
        return anchorId;
    }

    public UUID getDisplayId() {
        return displayId;
    }

    public ModelType getType() {
        return type;
    }

    public ModelHandler getHandler() {
        return handler;
    }

    public int getSpawnTick() {
        return spawnTick;
    }

    /**
     * Get anchor entity (with caching for performance).
     * Returns null if anchor no longer exists.
     */
    public Entity getAnchor() {
        if (anchorCache != null && anchorCache.isValid()) {
            return anchorCache;
        }
        // Cache invalidated, need lookup from Bukkit
        anchorCache = org.bukkit.Bukkit.getEntity(anchorId);
        return anchorCache;
    }

    /**
     * Get display entity (with caching for performance).
     * Returns null if display no longer exists.
     */
    public Display getDisplay() {
        if (displayCache != null && displayCache.isValid()) {
            return displayCache;
        }
        // Cache invalidated, need lookup from Bukkit
        Entity e = org.bukkit.Bukkit.getEntity(displayId);
        if (e instanceof Display d) {
            displayCache = d;
            return d;
        }
        return null;
    }

    /**
     * Check if both anchor and display are still valid.
     */
    public boolean isValid() {
        Entity anchor = getAnchor();
        Display display = getDisplay();
        return anchor != null && anchor.isValid() && !anchor.isDead()
                && display != null && display.isValid() && !display.isDead();
    }

    /**
     * Calculate ticks alive since spawn.
     */
    public int getTicksAlive(int currentTick) {
        return currentTick - spawnTick;
    }
}