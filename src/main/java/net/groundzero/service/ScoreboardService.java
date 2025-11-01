package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.game.GameSession;
import net.groundzero.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Scoreboard service:
 * - build per-player boards
 * - refresh values from session
 * - run game tick (plasma += income/20, time--)
 * - clear on cancel/end
 */
public class ScoreboardService {

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Map<String, Team>> boardTeams = new HashMap<>();

    public ScoreboardService() {}

    /* ===================== public API ===================== */

    /** Show game board for every participant */
    public void showGameBoard(GameSession session) {
        for (UUID id : session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            ensureBoard(p);
        }
    }

    /** Start per-tick update for this session */
    public void startGameTick(GameSession session) {
        Core.schedulers.runTimer(() -> {
            if (session == null) return;
            if (session.state() != GameState.RUNNING) return;

            int left = session.remainingTicks();
            if (left > 0) {
                session.setRemainingTicks(left - 1);
            } else {
                // time over
                Core.game.endGame();
                return;
            }

            for (UUID id : session.getParticipantsView()) {
                // 1) income per second (may be 10.00, 10.01, ...)
                double incomePerSec = getPerPlayerIncome(session, id);
                double incomePerTick = incomePerSec / 20.0;

                // 2) add to plasma
                double currentPlasma = session.getPlasmaMap().getOrDefault(id, Core.gameConfig.basePlasma);
                double nextPlasma = currentPlasma + incomePerTick;
                session.getPlasmaMap().put(id, nextPlasma);

                // 3) push to board
                refreshFromSession(session, id, session.remainingTicks());
            }

        }, 1L, 1L);
    }

    /** Clear all boards and return players to main board */
    public void clearAll() {
        Bukkit.getScheduler().runTask(Core.plugin, () -> {
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            Scoreboard main = (mgr != null ? mgr.getMainScoreboard() : null);

            for (Player p : Bukkit.getOnlinePlayers()) {
                Scoreboard sb = p.getScoreboard();
                if (sb != null) {
                    Objective obj = sb.getObjective("gz");
                    if (obj != null) {
                        try { obj.unregister(); } catch (Exception ignored) {}
                    }
                    for (Team t : sb.getTeams()) {
                        String n = t.getName();
                        if (n != null && (n.equals("gz") || n.startsWith("row_"))) {
                            try { t.unregister(); } catch (Exception ignored) {}
                        }
                    }
                }
                if (main != null) p.setScoreboard(main);
                else if (mgr != null) p.setScoreboard(mgr.getNewScoreboard());
            }

            boards.clear();
            boardTeams.clear();
        });
    }

    /* ===================== per-player income accessor ===================== */

    public double getPerPlayerIncome(GameSession session, UUID id) {
        if (session == null || id == null) return Core.gameConfig.baseIncomePerSecond;
        return session.getIncomeMap().getOrDefault(id, Core.gameConfig.baseIncomePerSecond);
    }

    /* ===================== refresh logic ===================== */

    public void refreshFromSession(GameSession session, UUID id, int ticksLeft) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;

        ensureBoard(p);

        Scoreboard sb = boards.get(p.getUniqueId());
        Map<String, Team> teams = boardTeams.get(p.getUniqueId());
        if (sb == null || teams == null) return;

        // name
        String name = p.getName();

        // coords
        Location loc = p.getLocation();
        String coords = String.format("x: %.2f y: %.2f z: %.2f",
                loc.getX(), loc.getY(), loc.getZ());

        // time
        String timeLeft = formatTimeFromTicks(ticksLeft);

        // plasma
        double plasmaVal = session.getPlasmaMap().getOrDefault(id, Core.gameConfig.basePlasma);
        String plasmaText = String.format("%.2f", plasmaVal);

        // income
        double incomeVal = session.getIncomeMap().getOrDefault(id, Core.gameConfig.baseIncomePerSecond);
        String incomeText = "+" + String.format("%.2f", incomeVal) + "/s";

        // score
        double scoreVal = session.getScoreMap().getOrDefault(id, Core.gameConfig.baseScore);
        String scoreText = String.format("%.2f", scoreVal);

        teams.get("row_player").setSuffix("§a" + name);
        teams.get("row_time").setSuffix("§a" + timeLeft);
        teams.get("row_coord").setSuffix("§a" + coords);
        teams.get("row_plasma").setSuffix("§e" + plasmaText);
        teams.get("row_income").setSuffix("§e" + incomeText);
        teams.get("row_score").setSuffix("§6" + scoreText);
    }

    /* ===================== board ensure/build ===================== */

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

        // layout
        addStaticBlankLine(sb, obj, 8, "§1");
        addTeamLine(sb, obj, teams, "row_player", "§fPlayer §f: ", "", 7, "§2");
        addTeamLine(sb, obj, teams, "row_time",   "§fTime Left §f: ", "", 6, "§3");
        addTeamLine(sb, obj, teams, "row_coord",  "§fCoords §f: ", "", 5, "§4");
        addStaticBlankLine(sb, obj, 4, "§5");
        addTeamLine(sb, obj, teams, "row_plasma", "§bPlasma §f: ", "", 3, "§6");
        addTeamLine(sb, obj, teams, "row_income", "§bIncome §f: ", "", 2, "§7");
        addStaticBlankLine(sb, obj, 1, "§8");
        addTeamLine(sb, obj, teams, "row_score",  "§dScore §f: ", "", 0, "§9");

        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
    }

    private void addTeamLine(Scoreboard sb, Objective obj,
                             Map<String, Team> teams,
                             String teamName, String label, String initial,
                             int score, String entryKey) {
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
        int totalSec = ticks / 20;
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format("%02d:%02d", m, s);
    }
}
