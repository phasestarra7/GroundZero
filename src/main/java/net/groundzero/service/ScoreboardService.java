package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.game.GameSession;
import net.groundzero.ui.options.IncomeOption;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Scoreboard service for GroundZero.
 *
 * - showGameBoard(session): build scoreboard for all participants at start
 * - refreshFromSession(session, playerId, ticksLeft): instant refresh (shop etc.)
 * - clearAll(): reset to main scoreboard (called from cancelAll/endGame)
 */
public class ScoreboardService {

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Map<String, Team>> boardTeams = new HashMap<>();

    /* =========================================================
       PUBLIC API
       ========================================================= */

    public void ensure(Player p) {
        if (p == null) return;
        if (boards.containsKey(p.getUniqueId())) return;

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        Scoreboard sb = mgr.getNewScoreboard();
        Objective obj = sb.registerNewObjective("gz", "dummy", "§f[ §bGroundZero §f]");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        Map<String, Team> teams = new HashMap<>();
        boardTeams.put(p.getUniqueId(), teams);

        // 8..0 layout
        addStatic(sb, obj, 8, "§1");
        addTeam(sb, obj, teams, "row_player", "§fPlayer §f: ", "", 7, "§2");
        addTeam(sb, obj, teams, "row_time",   "§fTime Left §f: ", "", 6, "§3");
        addTeam(sb, obj, teams, "row_coord",  "§fCoords §f: ", "", 5, "§4");
        addStatic(sb, obj, 4, "§5");
        addTeam(sb, obj, teams, "row_plasma", "§bPlasma §f: ", "", 3, "§6");
        addTeam(sb, obj, teams, "row_income", "§bIncome §f: ", "", 2, "§7");
        addStatic(sb, obj, 1, "§8");
        addTeam(sb, obj, teams, "row_score",  "§dScore §f: ", "", 0, "§9");

        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
    }

    /**
     * Called when the match starts.
     */
    public void showGameBoard(GameSession session) {
        if (session == null) return;
        int ticksLeft = Core.gameConfig.matchDurationTicks;

        for (UUID id : session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            ensure(p);

            double plasma = session.getPlasmaMap().getOrDefault(id, Core.gameConfig.basePlasma);
            double income = getPerPlayerIncome(session, id);
            double score  = session.getScoreMap().getOrDefault(id, Core.gameConfig.baseScore);

            updatePlayer(p, plasma, income, score, ticksLeft);
        }
    }

    /**
     * Instant refresh for a single player.
     * Use from shop / inventory / income changed.
     */
    public void refreshFromSession(GameSession session, UUID playerId, int ticksLeft) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null || !p.isOnline()) return;
        ensure(p);

        double plasma = Core.gameConfig.basePlasma;
        double income = Core.gameConfig.baseIncomePerSecond;
        double score  = Core.gameConfig.baseScore;

        if (session != null) {
            plasma = session.getPlasmaMap().getOrDefault(playerId, Core.gameConfig.basePlasma);
            income = getPerPlayerIncome(session, playerId);
            score  = session.getScoreMap().getOrDefault(playerId, Core.gameConfig.baseScore);
        }

        updatePlayer(p, plasma, income, score, ticksLeft);
    }

    /**
     * Called from cancelAll / endGame.
     */
    public void clearAll() {
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        Scoreboard main = (mgr != null ? mgr.getMainScoreboard() : null);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = boards.get(p.getUniqueId());
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

            if (main != null) {
                p.setScoreboard(main);
            } else if (mgr != null) {
                p.setScoreboard(mgr.getNewScoreboard());
            }
        }

        boards.clear();
        boardTeams.clear();
    }

    /* =========================================================
       INTERNAL
       ========================================================= */

    private void updatePlayer(Player p,
                              double plasmaVal,
                              double incomePerSec,
                              double scoreVal,
                              int ticksLeft) {
        Scoreboard sb = boards.get(p.getUniqueId());
        Map<String, Team> teams = boardTeams.get(p.getUniqueId());
        if (sb == null || teams == null) return;

        // player
        teams.get("row_player").setSuffix("§a" + p.getName());

        // time
        teams.get("row_time").setSuffix("§a" + formatTimeFromTicks(ticksLeft));

        // coords
        Location loc = p.getLocation();
        String coord = String.format("x: %.2f y: %.2f z: %.2f", loc.getX(), loc.getY(), loc.getZ());
        teams.get("row_coord").setSuffix("§a" + coord);

        // plasma
        teams.get("row_plasma").setSuffix("§e" + formatDouble(plasmaVal));

        // income
        teams.get("row_income").setSuffix("§e+" + formatDouble(incomePerSec) + "/s");

        // score
        teams.get("row_score").setSuffix("§6" + formatDouble(scoreVal));
    }

    private void addTeam(Scoreboard sb, Objective obj,
                         Map<String, Team> teams,
                         String teamName,
                         String prefix, String initialSuffix,
                         int score, String entryKey) {
        Team t = sb.registerNewTeam(teamName);
        t.setPrefix(prefix);
        t.setSuffix(initialSuffix);
        t.addEntry(entryKey);
        teams.put(teamName, t);
        obj.getScore(entryKey).setScore(score);
    }

    private void addStatic(Scoreboard sb, Objective obj, int score, String entryKey) {
        obj.getScore(entryKey).setScore(score);
    }

    private String formatTimeFromTicks(int ticks) {
        int totalSec = ticks / 20;
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format("%02d:%02d", m, s);
    }

    private String formatDouble(double v) {
        return String.format("%.2f", v);
    }

    /**
     * income = per-player override if present,
     * otherwise = global base * session income multiplier
     */
    public double getPerPlayerIncome(GameSession session, UUID id) {
        if (session == null) return Core.gameConfig.baseIncomePerSecond;
        // per-player override
        Double perPlayer = session.getIncomeMap().get(id);
        if (perPlayer != null) return perPlayer;

        // session-wide multiplier
        IncomeOption opt = session.income();
        double mul = (opt != null ? opt.multiplier : 1.0);
        return Core.gameConfig.baseIncomePerSecond * mul;
    }
}
