package net.groundzero.service.model;

import net.groundzero.app.Core;
import net.groundzero.service.GameService;
import net.groundzero.service.model.handler.AssaultModelHandler;
import net.groundzero.service.model.handler.AutoModelHandler;
import net.groundzero.service.model.handler.RpgModelHandler;
import net.groundzero.service.model.handler.SniperModelHandler;
import net.groundzero.service.tick.TickBus;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages visual models for projectiles and vehicles.
 *
 * Responsibilities:
 * - Register and lookup ModelHandlers by type
 * - Track active models (anchor → ModelData)
 * - Per-tick position sync via teleport
 * - Per-tick rotation updates for BULLET category
 * - Cleanup when anchor is removed
 *
 * Flow:
 * 1. Handler calls attachModel() after spawning anchor (Arrow, etc.)
 * 2. Service creates Display(s) via handler
 * 3. Every tick: teleport to anchor, update rotations, call onTick(), cleanup dead anchors
 * 4. When anchor dies: remove all Display entities
 */
public final class ProjectileModelService implements TickBus.Tickable, GameService {

    // Handler registry: ModelType → Handler
    private final Map<ModelType, ModelHandler> handlers = new EnumMap<>(ModelType.class);

    // Active models: anchorId → ModelData
    private final Map<UUID, ModelData> activeModels = new ConcurrentHashMap<>();

    // Maximum lifetime before forced cleanup (20 seconds = 400 ticks)
    private static final int MAX_LIFETIME_TICKS = 400;

    private boolean running = false;

    /* ===================== Model Data ===================== */

    private record ModelData(
            Entity anchor,
            List<Display> displays,
            ModelType type,
            ModelHandler handler,
            int spawnTick
    ) {
        boolean isValid() {
            if (anchor == null || !anchor.isValid() || anchor.isDead()) return false;
            for (Display d : displays) {
                if (d == null || !d.isValid() || d.isDead()) return false;
            }
            return true;
        }
    }

    /* ===================== Lifecycle ===================== */

    @Override
    public void start() {
        if (running) return;
        running = true;
        registerHandlers();
        Core.tickBus.register(this);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        Core.tickBus.unregister(this);

        // Remove all active display entities
        for (ModelData data : activeModels.values()) {
            removeDisplays(data.displays);
        }
        activeModels.clear();
    }

    @Override
    public void reset() {
        // Remove all displays on reset too
        for (ModelData data : activeModels.values()) {
            removeDisplays(data.displays);
        }
        activeModels.clear();
    }

    /* ===================== Handler Registration ===================== */

    private void registerHandlers() {
        // Clear existing
        handlers.clear();

        // Register all handlers
        registerHandler(new AssaultModelHandler());
        registerHandler(new AutoModelHandler());
        registerHandler(new SniperModelHandler());
        registerHandler(new RpgModelHandler());
//        registerHandler(new StunModelHandler());
//        registerHandler(new SmokeModelHandler());
//
//        registerHandler(new AerialSimpleModelHandler());
//        registerHandler(new AerialArrowModelHandler());
//        registerHandler(new AerialClusterModelHandler());
//        registerHandler(new AerialSpreaderModelHandler());
//        registerHandler(new AerialCarpetModelHandler());
//        registerHandler(new AerialHackHandler());
//
//        registerHandler(new MissileSimpleModelHandler());
//        registerHandler(new MissilePoisonModelHandler());
//        registerHandler(new MissileBunkerModelHandler());
//        registerHandler(new MissileHighExpModelHandler());
//        registerHandler(new MissileNuclearHandler());
//        registerHandler(new MissileAbmHandler());
    }

    private void registerHandler(ModelHandler handler) {
        handlers.put(handler.getModelType(), handler);
    }

    public ModelHandler getHandler(ModelType type) {
        return handlers.get(type);
    }

    /* ===================== Model Attachment ===================== */

    /**
     * Attach visual model(s) to an anchor entity.
     *
     * @param anchor The entity to attach to (Arrow, TNTPrimed, etc.)
     * @param type   The ModelType to attach
     * @return true if successfully attached
     */
    public boolean attachModel(Entity anchor, ModelType type) {
        if (anchor == null || type == null) return false;
        if (!running) {
            Core.plugin.getLogger().warning("[ProjectileModelService] Not running, cannot attach");
            return false;
        }

        ModelHandler handler = handlers.get(type);
        if (handler == null) {
            Core.plugin.getLogger().warning("[ProjectileModelService] No handler for type: " + type);
            return false;
        }

        if (activeModels.containsKey(anchor.getUniqueId())) {
            return false;
        }

        try {
            List<Display> displays = handler.createModels(anchor);
            if (displays == null || displays.isEmpty()) {
                Core.plugin.getLogger().warning("[ProjectileModelService] Handler returned empty displays");
                return false;
            }

            int currentTick = Core.tickBus.getCurrentTick();
            ModelData data = new ModelData(anchor, displays, type, handler, currentTick);
            activeModels.put(anchor.getUniqueId(), data);

            return true;
        } catch (Exception e) {
            Core.plugin.getLogger().warning("[ProjectileModelService] Failed to attach: " + e.getMessage());
            return false;
        }
    }

    /**
     * Manually remove model for an anchor.
     */
    public void removeModel(UUID anchorId) {
        if (anchorId == null) return;

        ModelData data = activeModels.remove(anchorId);
        if (data != null) {
            removeDisplays(data.displays);
        }
    }

    /* ===================== Tick Processing ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!running) return;
        if (activeModels.isEmpty()) return;

        Iterator<Map.Entry<UUID, ModelData>> it = activeModels.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, ModelData> entry = it.next();
            ModelData data = entry.getValue();

            // Check if anchor still valid
            if (!data.isValid()) {
                removeDisplays(data.displays);
                it.remove();
                continue;
            }

            // Check max lifetime (orphaned cleanup)
            int ticksAlive = currentTick - data.spawnTick;
            if (ticksAlive > MAX_LIFETIME_TICKS) {
                removeDisplays(data.displays);
                it.remove();
                continue;
            }

            // Sync position: teleport all displays to anchor
            // CRITICAL: yaw/pitch must be 0 to prevent double rotation
            Location a = data.anchor.getLocation();
            Location dst = new Location(a.getWorld(), a.getX(), a.getY(), a.getZ(), 0f, 0f);
            for (Display d : data.displays) {
                d.teleport(dst);
            }

            // Update rotation for BULLET category
            if (data.type.needsRotationUpdate()) {
                Vector velocity = data.anchor.getVelocity();
                data.handler.updateRotation(data.displays, velocity);
            }

            // Per-tick effects
            data.handler.onTick(data.displays, data.anchor, ticksAlive);
        }
    }

    /* ===================== Internal Helpers ===================== */

    private void removeDisplays(List<Display> displays) {
        if (displays == null) return;
        for (Display d : displays) {
            try {
                if (d != null && d.isValid()) {
                    d.remove();
                }
            } catch (Throwable ignored) {}
        }
    }
}