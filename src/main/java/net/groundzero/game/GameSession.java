package net.groundzero.game;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.Location;

import net.groundzero.ui.options.GameModeOption;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;

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

    /* ===== getters for match options ===== */
    public MapSizeOption mapSize() { return mapSize; }
    public IncomeOption income() { return income; }
    public GameModeOption gameMode() { return gameMode; }

    public MapSizeOption getMapSize() { return mapSize; }
    public IncomeOption getIncome() { return income; }
    public GameModeOption getGameMode() { return gameMode; }
    public void setMapSize(MapSizeOption v) { this.mapSize = v; }
    public void setIncome(IncomeOption v) { this.income = v; }
    public void setGameMode(GameModeOption v) { this.gameMode = v; }

    // ----- read-only views -----
    public Set<UUID> getParticipantsView() { return Collections.unmodifiableSet(participants); }
    public Set<UUID> getSpectatorsView()   { return Collections.unmodifiableSet(spectators); }
    public World getWorld() { return world; }
    public Location getCenter() { return center; }

    public boolean isParticipant(UUID id)  { return participants.contains(id); }
    public boolean isSpectator(UUID id)    { return spectators.contains(id); }

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

    /** On quit: remove from all groups */
    // on quit
    public void purge(UUID id) {
        participants.remove(id);
        spectators.remove(id);
    }

    public void setWorld(World w) {
        this.world = w;
    }

    public void setCenter(Location center) {
        this.center = center;
    }

    @Override public String toString() {
        return "Session{" +
                "mapSize=" + (mapSize == null ? "-" : mapSize.label) +
                ", income=" + (income == null ? "-" : income.label) +
                ", gameMode=" + (gameMode == null ? "-" : gameMode.label) +
                '}';
    }
}
