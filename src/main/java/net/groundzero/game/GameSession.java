package net.groundzero.game;

import java.util.*;

import net.groundzero.ui.options.GameModeOption;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Holds per-match chosen options and runtime player groups.
 *
 * Invariants:
 *   - participants ∩ spectators = ∅
 *   - A player must be in exactly one group while online
 *   - On join: defaults to spectator
 */

public final class GameSession {
    private MapSizeOption   mapSize;
    private IncomeOption    income;
    private GameModeOption  gameMode;

    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();

    private World world;
    private Location center;

    // original border info (to restore)
    private Location originalBorderCenter;
    private double   originalBorderSize;

    private final Map<UUID, Double> plasma = new HashMap<>();
    private final Map<UUID, Double> incomes = new HashMap<>();
    private final Map<UUID, Double> scores = new HashMap<>();

    public GameSession() {}

    /* ===== getters for match options ===== */
    public MapSizeOption mapSize() { return mapSize; }
    public IncomeOption income() { return income; }
    public GameModeOption gameMode() { return gameMode; }
    public World getWorld() { return world; }
    public Location getCenter() { return center; }
    public Location getOriginalBorderCenter() { return originalBorderCenter; }
    public double getOriginalBorderSize() { return originalBorderSize; }

    public Map<UUID, Double> getPlasmaMap() { return plasma; }
    public Map<UUID, Double> getIncomeMap() { return incomes; }
    public Map<UUID, Double> getScoresMap() { return scores; }


    public void setMapSize(MapSizeOption v) { this.mapSize = v; }
    public void setIncome(IncomeOption v) { this.income = v; }
    public void setGameMode(GameModeOption v) { this.gameMode = v; }
    public void setWorld(World world) { this.world = world; }
    public void setCenter(Location center) { this.center = center; }
    public void setOriginalBorderCenter(Location originalBorderCenter) { this.originalBorderCenter = originalBorderCenter; }
    public void setOriginalBorderSize(double originalBorderSize) { this.originalBorderSize = originalBorderSize; }

    // ----- read-only views -----
    public Set<UUID> getParticipantsView() { return Collections.unmodifiableSet(participants); }
    public Set<UUID> getSpectatorsView()   { return Collections.unmodifiableSet(spectators); }

    public boolean isParticipant(UUID id)  { return participants.contains(id); }
    public boolean isSpectator(UUID id)    { return spectators.contains(id); }

    public void captureOriginalBorder(World world) {
        if (world == null) return;
        WorldBorder wb = world.getWorldBorder();
        this.originalBorderCenter = wb.getCenter();
        this.originalBorderSize = wb.getSize();
    }

    public void restoreOriginalBorder() {
        if (world == null) return;
        if (originalBorderCenter == null || originalBorderSize <= 0) return;
        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(originalBorderCenter);
        wb.setSize(originalBorderSize);
    }

    /** Make spectator (side-effect: remove from participants always) */
    public void moveToSpectator(UUID id) {
        if (id == null) return;
        participants.remove(id);
        spectators.add(id);
    }

    /** Make participant (side-effect: remove from spectators always) */
    public void moveToParticipant(UUID id) {
        if (id == null) return;
        spectators.remove(id);
        participants.add(id);
    }

    /** /gz start: spectators → participants, spectators cleared */
    public void snapshotParticipantsFromSpectators() {
        participants.addAll(spectators);
        spectators.clear();

        for (UUID id : participants) {
            plasma.putIfAbsent(id, 0.0);
            incomes.putIfAbsent(id, 0.0);
            scores.putIfAbsent(id, 0.0);
        }
    }

    /** /gz cancel: everyone online → spectator */
    public void resetToAllSpectators(Set<UUID> online) {
        participants.clear();
        spectators.clear();
        spectators.addAll(online);
    }

    // on join
    public void registerJoinAsSpectator(UUID id) {
        if (id == null) return;
        participants.remove(id);
        spectators.add(id);
    }

    @Override public String toString() {
        return "Session{" +
                "mapSize=" + (mapSize == null ? "-" : mapSize.label) +
                ", income=" + (income == null ? "-" : income.label) +
                ", gameMode=" + (gameMode == null ? "-" : gameMode.label) +
                '}';
    }
}
