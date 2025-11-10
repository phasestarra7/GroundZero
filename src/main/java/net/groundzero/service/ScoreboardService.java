package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.game.GameSession;
import net.groundzero.game.GameState;
import net.groundzero.service.tick.TickBus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Scoreboard renderer (UI-only).
 * - Subscribes to TickBus to refresh visuals.
 * - Does NOT mutate time/plasma/income/score, nor end the game.
 */
public class ScoreboardService implements TickBus.Tickable {

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Map<String, Team>> boardTeams = new HashMap<>();

    // keep as final and set to 1 now; changeable later
    private static final int UI_UPDATE_PERIOD_TICKS = 1;
    private int lastUiUpdateTick = 0;

    private GameSession session;

    public void start(GameSession session) {
        this.session = session;
        this.lastUiUpdateTick = 0;
        showGameBoard(session);
        Core.tickBus.register(this);
    }

    public void stop() {
        Core.tickBus.unregister(this);
        this.session = null;
        clearAllBoardsAndRestoreMain();
    }

    // on tick impl
    @Override
    public void onTick(int currentTick) {
        if (session == null) return;
        if (Core.session.state() != GameState.RUNNING) return;

        if (currentTick - lastUiUpdateTick < UI_UPDATE_PERIOD_TICKS) return;
        lastUiUpdateTick = currentTick;

        int ticksLeft = session.remainingTicks();

        for (UUID id : session.getParticipantsView()) {
            refreshFromSession(session, id, ticksLeft);
        }
    }

    public void showGameBoard(GameSession session) {
        for (UUID id : session.getParticipantsView()) {
            ensureBoard(Bukkit.getPlayer(id));
        }
    }

    public void clearAllBoardsAndRestoreMain() {

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        Scoreboard main = (mgr != null ? mgr.getMainScoreboard() : null);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = p.getScoreboard();
            if (sb != null) {
                Objective obj = sb.getObjective("gz");
                if (obj != null) try { obj.unregister(); } catch (Exception ignored) {}

                for (Team t : sb.getTeams()) {
                    String n = t.getName();
                    if (n != null && (n.equals("gz") || n.startsWith("row_"))) {
                        try { t.unregister(); } catch (Exception ignored) {}
                    }
                }
            }
            if (main != null) p.setScoreboard(main);
        }
        boards.clear();
        boardTeams.clear();
    }

    /* ---------- render helpers (layout unchanged) ---------- */

    public void refreshFromSession(GameSession session, UUID id, int ticksLeft) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;

        ensureBoard(p);

        Scoreboard sb = boards.get(p.getUniqueId());
        Map<String, Team> teams = boardTeams.get(p.getUniqueId());
        if (sb == null || teams == null) return;

        String name = p.getName();
        Location loc = p.getLocation();
        String coords = String.format("x: %.2f y: %.2f z: %.2f", loc.getX(), loc.getY(), loc.getZ());
        String timeLeft = formatTimeFromTicks(ticksLeft);

        double plasmaVal = session.getPlasmaMap().getOrDefault(id, Core.gameConfig.basePlasma);
        String plasmaText = String.format("%.2f", plasmaVal);

        double incomeVal = session.getIncomeMap().getOrDefault(id, Core.gameConfig.baseIncomePerSecond);
        String incomeText = "+" + String.format("%.2f", incomeVal) + "/s";

        double scoreVal = session.getScoreMap().getOrDefault(id, Core.gameConfig.baseScore);
        String scoreText = String.format("%.2f", scoreVal);

        teams.get("row_player").setSuffix("§a" + name);
        teams.get("row_time").setSuffix("§a" + timeLeft);
        teams.get("row_coord").setSuffix("§a" + coords);
        teams.get("row_plasma").setSuffix("§e" + plasmaText);
        teams.get("row_income").setSuffix("§e" + incomeText);
        teams.get("row_score").setSuffix("§6" + scoreText);
    }

    private void ensureBoard(Player p) {
        if (p == null) return;
        if (boards.containsKey(p.getUniqueId())) return;

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        Scoreboard sb = mgr.getNewScoreboard();
        Objective obj = sb.registerNewObjective("gz", "dummy", "§f[ §bGroundZero §f]");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        Map<String, Team> teams = new HashMap<>();
        boardTeams.put(p.getUniqueId(), teams);

        addStaticBlankLine(sb, obj, 8, "§1");
        addTeamLine(sb, obj, teams, "row_player", "§fPlayer §f: ", "", 7, "§2");
        addTeamLine(sb, obj, teams, "row_time",   "§fTime Left §f: ", "", 6, "§3");
        addTeamLine(sb, obj, teams, "row_coord",  "§fCoords §f: ",    "", 5, "§4");
        addStaticBlankLine(sb, obj, 4, "§5");
        addTeamLine(sb, obj, teams, "row_plasma", "§bPlasma §f: ",   "", 3, "§6");
        addTeamLine(sb, obj, teams, "row_income", "§bIncome §f: ",   "", 2, "§7");
        addStaticBlankLine(sb, obj, 1, "§8");
        addTeamLine(sb, obj, teams, "row_score",  "§dScore §f: ",    "", 0, "§9");

        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
    }

    private void addTeamLine(Scoreboard sb, Objective obj, Map<String, Team> teams,
                             String teamName, String label, String initial, int score, String entryKey) {
        Team team = sb.registerNewTeam(teamName);
        team.setPrefix(label);
        team.setSuffix(initial);
        team.addEntry(entryKey);
        teams.put(teamName, team);
        obj.getScore(entryKey).setScore(score);
    }

    private void addStaticBlankLine(Scoreboard sb, Objective obj, int score, String entryKey) {
        obj.getScore(entryKey).setScore(score);
    }

    private String formatTimeFromTicks(int ticks) {
        int totalSec = Math.max(0, ticks / 20);
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format("%02d:%02d", m, s);
    }
}
