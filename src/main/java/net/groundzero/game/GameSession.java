package net.groundzero.game;

import org.bukkit.*;
import org.bukkit.entity.Player;

import net.groundzero.ui.options.*;
import java.util.*;

/**
 * Per-game runtime snapshot:
 * - participants / spectators
 * - world / center
 * - per-player plasma / income / score
 * - original border (to restore)
 * - current game state
 */
public class GameSession {

    // ---- state ----
    private GameState state = GameState.IDLE;

    // ---- players ----
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> spectators   = new HashSet<>();

    // ---- world / area ----
    private World world;
    private Location center;

    // saved border
    private double originalBorderSize = -1;
    private Location originalBorderCenter = null;

    // ---- options voted ----
    private MapSizeOption mapSize;
    private IncomeOption income;
    private GameModeOption gameMode;

    // ---- per-player runtime ----
    private final Map<UUID, Double> plasmaMap = new HashMap<>();
    private final Map<UUID, Double> incomeMap = new HashMap<>();
    private final Map<UUID, Double> scoreMap  = new HashMap<>();

    /* =========================================================
       getters / setters (1-liner style)
       ========================================================= */

    public GameState state()              { return state; }
    public void setState(GameState s)     { this.state = s; }

    public Set<UUID> getParticipantsView(){ return Collections.unmodifiableSet(participants); }
    public Set<UUID> getSpectatorsView()  { return Collections.unmodifiableSet(spectators); }

    public World world()                  { return world; }
    public void setWorld(World w)         { this.world = w; }

    public Location center()              { return center; }
    public void setCenter(Location c)     { this.center = c; }

    public MapSizeOption mapSize()        { return mapSize; }
    public void setMapSize(MapSizeOption m){ this.mapSize = m; }

    public IncomeOption income()          { return income; }
    public void setIncome(IncomeOption i) { this.income = i; }

    public GameModeOption gameMode()      { return gameMode; }
    public void setGameMode(GameModeOption g){ this.gameMode = g; }

    public Map<UUID, Double> getPlasmaMap(){ return plasmaMap; }
    public Map<UUID, Double> getIncomeMap(){ return incomeMap; }
    public Map<UUID, Double> getScoreMap() { return scoreMap; }

    /* =========================================================
       participants management
       ========================================================= */

    /**
     * Called at game start. We take current spectators as participants.
     */
    public void snapshotParticipantsFromSpectators() {
        participants.clear();
        participants.addAll(spectators);
        spectators.clear();
    }

    public void resetToAllSpectators(Set<UUID> online) {
        participants.clear();
        spectators.clear();
        if (online != null) {
            spectators.addAll(online);
        }
    }

    public void addSpectator(UUID id) {
        if (id == null) return;
        // if player is already participant, do not touch
        if (participants.contains(id)) return;
        spectators.add(id);
        // TODO : player just joined
    }

    /* =========================================================
       world / border helpers
       ========================================================= */

    /**
     * Try to capture common world + averaged center from current participants.
     * @return true if world was consistent and captured
     */
    public boolean captureWorldAndCenterFromParticipants() {
        if (participants.isEmpty()) {
            return false;
        }

        World commonWorld = null;
        double sumX = 0;
        double sumZ = 0;
        int count = 0;

        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            Location loc = p.getLocation();
            if (commonWorld == null) {
                commonWorld = loc.getWorld();
            } else {
                if (!commonWorld.equals(loc.getWorld())) {
                    return false; // different worlds → fail
                }
            }

            sumX += loc.getX();
            sumZ += loc.getZ();
            count++;
        }

        if (commonWorld == null || count == 0) {
            return false;
        }

        double avgX = sumX / count;
        double avgZ = sumZ / count;

        int highestY = commonWorld.getHighestBlockYAt((int) Math.floor(avgX), (int) Math.floor(avgZ));
        Location computedCenter = new Location(commonWorld, avgX, highestY, avgZ);

        this.world = commonWorld;
        this.center = computedCenter;

        // border snapshot also happens here
        captureOriginalBorder(commonWorld);

        return true;
    }

    public void captureOriginalBorder(World w) {
        if (w == null) return;
        WorldBorder wb = w.getWorldBorder();
        this.originalBorderSize = wb.getSize();
        this.originalBorderCenter = wb.getCenter();
    }

    public void restoreOriginalBorder() {
        if (world == null) return;
        if (originalBorderSize <= 0 || originalBorderCenter == null) return;

        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(originalBorderCenter);
        wb.setSize(originalBorderSize);

        // reset so we don't accidentally restore twice
        originalBorderSize = -1;
        originalBorderCenter = null;
    }

    /* =========================================================
       display helpers
       ========================================================= */

    /**
     * Returns a colored comma-separated participant names string.
     */
    public String namesOfParticipants() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (!first) sb.append(", ");
            sb.append("§a").append(p.getName()).append("§f");
            first = false;
        }
        return sb.toString();
    }
}
