package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.game.GameState;
import net.groundzero.ui.options.GameModeOption;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Voting-only service.
 * Holds vote state and GUI refresh for each voting phase.
 */
public final class VoteService {

    private final Map<MapSizeOption, Integer> mapVotes   = new EnumMap<>(MapSizeOption.class);
    private final Map<IncomeOption, Integer>  incomeVotes = new EnumMap<>(IncomeOption.class);
    private final Map<GameModeOption, Integer> modeVotes  = new EnumMap<>(GameModeOption.class);

    private final Map<UUID, MapSizeOption> votedMapSize = new HashMap<>();
    private final Map<UUID, IncomeOption>  votedIncome  = new HashMap<>();
    private final Map<UUID, GameModeOption> votedMode   = new HashMap<>();

    private boolean acceptingVotes = false;

    private static final Random RNG = new Random();

    public VoteService() {}

    /* =========================================================
       exposed from GameManager
       ========================================================= */

    public void startPreVoteCountdown(Runnable onDone) {
        startCountdownInternal(5, onDone);
    }

    public void startMapSizeVote() {
        Core.game.setState(GameState.VOTING_MAP_SIZE); // keep state in GameManager
        acceptingVotes = true;

        votedMapSize.clear();
        mapVotes.clear();
        for (MapSizeOption opt : MapSizeOption.values()) {
            mapVotes.put(opt, 0);
        }

        Core.ui.newMapSize();

        for (UUID id : Core.game.session().getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notify.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.ui.openMapSize(pp);
        }

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for map size in §a3"
        ), 7 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for map size in §a2"
        ), 8 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for map size in §a1"
        ), 9 * 20L);

        Core.schedulers.runLater(this::finishMapSizeVotePhase, 10 * 20L);
    }

    public void startIncomeVote() {
        Core.game.setState(GameState.VOTING_INCOME_MULTIPLIER);
        acceptingVotes = true;

        votedIncome.clear();
        incomeVotes.clear();
        for (IncomeOption opt : IncomeOption.values()) {
            incomeVotes.put(opt, 0);
        }

        Core.ui.newIncome();

        for (UUID id : Core.game.session().getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notify.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.ui.openIncome(pp);
        }

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for income multiplier in §a3"
        ), 7 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for income multiplier in §a2"
        ), 8 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for income multiplier in §a1"
        ), 9 * 20L);

        Core.schedulers.runLater(this::finishIncomeVotePhase, 10 * 20L);
    }

    public void startGameModeVote() {
        Core.game.setState(GameState.VOTING_GAME_MODE);
        acceptingVotes = true;

        votedMode.clear();
        modeVotes.clear();
        for (GameModeOption opt : GameModeOption.values()) {
            modeVotes.put(opt, 0);
        }

        Core.ui.closeAllGZViews();
        Core.ui.newGameMode();

        for (UUID id : Core.game.session().getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notify.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.ui.openGameMode(pp);
        }

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for game mode in §a3"
        ), 7 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for game mode in §a2"
        ), 8 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for game mode in §a1"
        ), 9 * 20L);

        Core.schedulers.runLater(this::finishGameModeVotePhase, 10 * 20L);
    }

    public void startFinalCountdown(Runnable onDone) {
        startCountdownInternal(5, onDone);
    }

    /* =========================================================
       GUI clicks → vote
       ========================================================= */

    public void voteMapSize(UUID pid, MapSizeOption opt) {
        if (!isVotingMapSize() || !acceptingVotes || opt == null) return;

        MapSizeOption prev = votedMapSize.put(pid, opt);
        if (prev != null) {
            mapVotes.put(prev, Math.max(0, mapVotes.get(prev) - 1));
        }
        mapVotes.put(opt, mapVotes.get(opt) + 1);

        Core.ui.refreshMapSizeVotes(
                mapVotes.get(MapSizeOption.SIZE_50),
                mapVotes.get(MapSizeOption.SIZE_100),
                mapVotes.get(MapSizeOption.SIZE_200),
                mapVotes.get(MapSizeOption.SIZE_400)
        );

        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            Core.notify.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.HIGH);
        }
    }

    public void voteIncome(UUID pid, IncomeOption opt) {
        if (!isVotingIncome() || !acceptingVotes || opt == null) return;

        IncomeOption prev = votedIncome.put(pid, opt);
        if (prev != null) {
            incomeVotes.put(prev, Math.max(0, incomeVotes.get(prev) - 1));
        }
        incomeVotes.put(opt, incomeVotes.get(opt) + 1);

        Core.ui.refreshIncomeVotes(
                incomeVotes.get(IncomeOption.X0_5),
                incomeVotes.get(IncomeOption.X1_0),
                incomeVotes.get(IncomeOption.X2_0),
                incomeVotes.get(IncomeOption.X4_0)
        );

        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            Core.notify.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.HIGH);
        }
    }

    public void voteGameMode(UUID pid, GameModeOption opt) {
        if (!isVotingGameMode() || !acceptingVotes || opt == null) return;

        GameModeOption prev = votedMode.put(pid, opt);
        if (prev != null) {
            modeVotes.put(prev, Math.max(0, modeVotes.get(prev) - 1));
        }
        modeVotes.put(opt, modeVotes.get(opt) + 1);

        Core.ui.refreshGameModeVotes(
                modeVotes.get(GameModeOption.STANDARD)
        );

        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            Core.notify.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.HIGH);
        }
    }

    /* =========================================================
       status for GUI reopen
       ========================================================= */

    public boolean isVotingMapSize() {
        return Core.game.state() == GameState.VOTING_MAP_SIZE;
    }

    public boolean isVotingIncome() {
        return Core.game.state() == GameState.VOTING_INCOME_MULTIPLIER;
    }

    public boolean isVotingGameMode() {
        return Core.game.state() == GameState.VOTING_GAME_MODE;
    }

    /* =========================================================
       finishers
       ========================================================= */

    private void finishMapSizeVotePhase() {
        acceptingVotes = false;

        int max = 0;
        for (MapSizeOption o : MapSizeOption.values()) {
            max = Math.max(max, mapVotes.getOrDefault(o, 0));
        }

        List<MapSizeOption> ties = new ArrayList<>();
        for (MapSizeOption o : MapSizeOption.values()) {
            if (mapVotes.getOrDefault(o, 0) == max) {
                ties.add(o);
            }
        }

        Core.ui.retainOnlyMapSize(ties);

        Core.schedulers.runLater(() -> {
            MapSizeOption chosen = pickRandom(ties);
            if (chosen != null) {
                Core.ui.highlightMapSizeSelected(chosen.label, chosen.slot);
                Core.game.session().setMapSize(chosen);
                Core.notify.broadcast(
                        Core.game.session().getParticipantsView(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        Notifier.PitchLevel.MID,
                        false,
                        "Map size selected : §a" + chosen.label
                );
            }
            Core.schedulers.runLater(Core.game::gotoVotingIncome, 3 * 20L);
        }, 2 * 20L);
    }

    private void finishIncomeVotePhase() {
        acceptingVotes = false;

        int max = 0;
        for (IncomeOption o : IncomeOption.values()) {
            max = Math.max(max, incomeVotes.getOrDefault(o, 0));
        }

        List<IncomeOption> ties = new ArrayList<>();
        for (IncomeOption o : IncomeOption.values()) {
            if (incomeVotes.getOrDefault(o, 0) == max) {
                ties.add(o);
            }
        }

        Core.ui.retainOnlyIncome(ties);

        Core.schedulers.runLater(() -> {
            IncomeOption chosen = pickRandom(ties);
            if (chosen != null) {
                Core.ui.highlightIncomeSelected(chosen.label, chosen.slot);
                Core.game.session().setIncome(chosen);
                Core.notify.broadcast(
                        Core.game.session().getParticipantsView(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        Notifier.PitchLevel.MID,
                        false,
                        "Income Multiplier selected : §a" + chosen.label
                );

                // per-player income reapply
                Core.game.applyIncomeOptionToParticipants(chosen);
            }
            Core.schedulers.runLater(Core.game::gotoVotingGameMode, 3 * 20L);
        }, 2 * 20L);
    }

    private void finishGameModeVotePhase() {
        acceptingVotes = false;

        int max = 0;
        for (GameModeOption o : GameModeOption.values()) {
            max = Math.max(max, modeVotes.getOrDefault(o, 0));
        }

        List<GameModeOption> ties = new ArrayList<>();
        for (GameModeOption o : GameModeOption.values()) {
            if (modeVotes.getOrDefault(o, 0) == max) {
                ties.add(o);
            }
        }

        Core.ui.retainOnlyGameMode(ties);

        Core.schedulers.runLater(() -> {
            GameModeOption chosen = pickRandom(ties);
            if (chosen != null) {
                Core.ui.highlightGameModeSelected(chosen.label, chosen.slot);
                Core.game.session().setGameMode(chosen);
                Core.notify.broadcast(
                        Core.game.session().getParticipantsView(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        Notifier.PitchLevel.MID,
                        false,
                        "Game Mode selected : §a" + chosen.label
                );
            }
            Core.schedulers.runLater(Core.game::gotoCountdownBeforeStart, 3 * 20L);
        }, 2 * 20L);
    }

    /* =========================================================
       utils
       ========================================================= */

    private void startCountdownInternal(int seconds, Runnable onDone) {
        if (seconds <= 0) {
            Core.schedulers.runLater(onDone, 1L);
            return;
        }

        Core.notify.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_PLING,
                Notifier.PitchLevel.MID,
                false,
                "GroundZero starting in " + seconds
        );

        Core.schedulers.runLater(() -> startCountdownInternal(seconds - 1, onDone), 20L);
    }

    private <T> T pickRandom(List<T> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(RNG.nextInt(list.size()));
    }
}
