package net.groundzero.service.game;

import net.groundzero.app.Core;
import net.groundzero.game.GameState;
import net.groundzero.ui.options.GameModeOption;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Voting-only service.
 * - holds vote counts
 * - updates GUI inventories (lore, retain-only, highlight)
 * - callbacks to GameManager for phase transitions
 */
public final class VoteService {

    private final Map<MapSizeOption, Integer> mapVotes   = new EnumMap<>(MapSizeOption.class);
    private final Map<IncomeOption, Integer>  incomeVotes = new EnumMap<>(IncomeOption.class);
    private final Map<GameModeOption, Integer> modeVotes  = new EnumMap<>(GameModeOption.class);

    private final Map<UUID, MapSizeOption>   votedMapSize = new HashMap<>();
    private final Map<UUID, IncomeOption>    votedIncome  = new HashMap<>();
    private final Map<UUID, GameModeOption>  votedMode    = new HashMap<>();

    private boolean acceptingVotes = false;

    private static final Random RNG = new Random();

    // Callbacks for each voting phase completion
    private Runnable onMapSizeComplete;
    private Runnable onIncomeComplete;
    private Runnable onGameModeComplete;

    public VoteService() {}

    public void reset() {
        acceptingVotes = false;
        onMapSizeComplete = null;
        onIncomeComplete = null;
        onGameModeComplete = null;
        votedMapSize.clear();
        votedIncome.clear();
        votedMode.clear();
        mapVotes.clear();
        incomeVotes.clear();
        modeVotes.clear();
    }

    /* =========================================================
       Phase starters - called by GameManager
       ========================================================= */

    public void startMapSizeVote(Runnable onComplete) {
        this.onMapSizeComplete = onComplete;
        Core.session.setState(GameState.VOTING_MAP_SIZE);
        acceptingVotes = true;

        votedMapSize.clear();
        mapVotes.clear();
        for (MapSizeOption opt : MapSizeOption.values()) {
            mapVotes.put(opt, 0);
        }

        Core.guiService.newMapSize();

        for (UUID id : Core.game.session().getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notifier.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.guiService.openMapSize(pp);
        }

        scheduleVoteEndNotices(7, "map size");
        Core.schedulers.runLater(this::finishMapSizeVotePhase, Core.gameConfig.voteCountdownTicks);
    }

    public void startIncomeVote(Runnable onComplete) {
        this.onIncomeComplete = onComplete;
        Core.session.setState(GameState.VOTING_INCOME_MULTIPLIER);
        acceptingVotes = true;

        votedIncome.clear();
        incomeVotes.clear();
        for (IncomeOption opt : IncomeOption.values()) {
            incomeVotes.put(opt, 0);
        }

        Core.guiService.newIncome();

        for (UUID id : Core.game.session().getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notifier.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.guiService.openIncome(pp);
        }

        scheduleVoteEndNotices(7, "income multiplier");
        Core.schedulers.runLater(this::finishIncomeVotePhase, Core.gameConfig.voteCountdownTicks);
    }

    public void startGameModeVote(Runnable onComplete) {
        this.onGameModeComplete = onComplete;
        Core.session.setState(GameState.VOTING_GAME_MODE);
        acceptingVotes = true;

        votedMode.clear();
        modeVotes.clear();
        for (GameModeOption opt : GameModeOption.values()) {
            modeVotes.put(opt, 0);
        }

        Core.guiService.closeAllGZViews();
        Core.guiService.newGameMode();

        for (UUID id : Core.game.session().getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notifier.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.guiService.openGameMode(pp);
        }

        scheduleVoteEndNotices(7, "game mode");
        Core.schedulers.runLater(this::finishGameModeVotePhase, Core.gameConfig.voteCountdownTicks);
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

        refreshMapSizeVotes(
                mapVotes.get(MapSizeOption.SIZE_50),
                mapVotes.get(MapSizeOption.SIZE_100),
                mapVotes.get(MapSizeOption.SIZE_200),
                mapVotes.get(MapSizeOption.SIZE_400)
        );

        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            Core.notifier.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.HIGH);
        }
    }

    public void voteIncome(UUID pid, IncomeOption opt) {
        if (!isVotingIncome() || !acceptingVotes || opt == null) return;

        IncomeOption prev = votedIncome.put(pid, opt);
        if (prev != null) {
            incomeVotes.put(prev, Math.max(0, incomeVotes.get(prev) - 1));
        }
        incomeVotes.put(opt, incomeVotes.get(opt) + 1);

        refreshIncomeVotes(
                incomeVotes.get(IncomeOption.X0_5),
                incomeVotes.get(IncomeOption.X1_0),
                incomeVotes.get(IncomeOption.X2_0),
                incomeVotes.get(IncomeOption.X4_0)
        );

        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            Core.notifier.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.HIGH);
        }
    }

    public void voteGameMode(UUID pid, GameModeOption opt) {
        if (!isVotingGameMode() || !acceptingVotes || opt == null) return;

        GameModeOption prev = votedMode.put(pid, opt);
        if (prev != null) {
            modeVotes.put(prev, Math.max(0, modeVotes.get(prev) - 1));
        }
        modeVotes.put(opt, modeVotes.get(opt) + 1);

        refreshGameModeVotes(
                modeVotes.get(GameModeOption.STANDARD)
        );

        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            Core.notifier.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.HIGH);
        }
    }

    /* =========================================================
       status for GUI reopen
       ========================================================= */

    public boolean isVotingMapSize() {
        return Core.session.state() == GameState.VOTING_MAP_SIZE;
    }

    public boolean isVotingIncome() {
        return Core.session.state() == GameState.VOTING_INCOME_MULTIPLIER;
    }

    public boolean isVotingGameMode() {
        return Core.session.state() == GameState.VOTING_GAME_MODE;
    }

    /* =========================================================
       finishers - invoke callback after selecting winner
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

        retainOnlyMapSize(ties);

        Core.schedulers.runLater(() -> {
            MapSizeOption chosen = pickRandom(ties);
            if (chosen != null) {
                highlightMapSizeSelected(chosen.label, chosen.slot);
                Core.session.setMapSize(chosen);
                Core.notifier.broadcast(
                        Core.session.getParticipantsView(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        Notifier.PitchLevel.MID,
                        false,
                        "Map size selected : §a" + chosen.label
                );
            }

            // Callback to GameManager for next phase
            if (onMapSizeComplete != null) {
                Core.schedulers.runLater(onMapSizeComplete, Core.gameConfig.votePhasePauseTicks);
            }
        }, Core.gameConfig.voteRevealDelayTicks);
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

        retainOnlyIncome(ties);

        Core.schedulers.runLater(() -> {
            IncomeOption chosen = pickRandom(ties);
            if (chosen != null) {
                highlightIncomeSelected(chosen.label, chosen.slot);
                Core.session.setIncome(chosen);
                Core.notifier.broadcast(
                        Core.session.getParticipantsView(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        Notifier.PitchLevel.MID,
                        false,
                        "Income Multiplier selected : §a" + chosen.label
                );
            }

            // Callback to GameManager for next phase
            if (onIncomeComplete != null) {
                Core.schedulers.runLater(onIncomeComplete, Core.gameConfig.votePhasePauseTicks);
            }
        }, Core.gameConfig.voteRevealDelayTicks);
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

        retainOnlyGameMode(ties);

        Core.schedulers.runLater(() -> {
            GameModeOption chosen = pickRandom(ties);
            if (chosen != null) {
                highlightGameModeSelected(chosen.label, chosen.slot);
                Core.session.setGameMode(chosen);
                Core.notifier.broadcast(
                        Core.session.getParticipantsView(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        Notifier.PitchLevel.MID,
                        false,
                        "Game Mode selected : §a" + chosen.label
                );
            }

            // Callback to GameManager for final countdown
            if (onGameModeComplete != null) {
                Core.schedulers.runLater(onGameModeComplete, Core.gameConfig.votePhasePauseTicks);
            }
        }, Core.gameConfig.voteRevealDelayTicks);
    }

    /* =========================================================
       internal: refresh / retain / highlight
       ========================================================= */

    private void refreshMapSizeVotes(int size50, int size100, int size200, int size400) {
        Inventory inv = Core.guiService.getMapSizeInventory();
        if (inv == null) return;
        setVotes(inv, MapSizeOption.SIZE_50.slot, MapSizeOption.SIZE_50.label, size50);
        setVotes(inv, MapSizeOption.SIZE_100.slot, MapSizeOption.SIZE_100.label, size100);
        setVotes(inv, MapSizeOption.SIZE_200.slot, MapSizeOption.SIZE_200.label, size200);
        setVotes(inv, MapSizeOption.SIZE_400.slot, MapSizeOption.SIZE_400.label, size400);
    }

    private void refreshIncomeVotes(int x05, int x10, int x20, int x40) {
        Inventory inv = Core.guiService.getIncomeInventory();
        if (inv == null) return;
        setVotes(inv, IncomeOption.X0_5.slot, IncomeOption.X0_5.label, x05);
        setVotes(inv, IncomeOption.X1_0.slot, IncomeOption.X1_0.label, x10);
        setVotes(inv, IncomeOption.X2_0.slot, IncomeOption.X2_0.label, x20);
        setVotes(inv, IncomeOption.X4_0.slot, IncomeOption.X4_0.label, x40);
    }

    private void refreshGameModeVotes(int standard) {
        Inventory inv = Core.guiService.getGameModeInventory();
        if (inv == null) return;
        setVotes(inv, GameModeOption.STANDARD.slot, GameModeOption.STANDARD.label, standard);
    }

    private void retainOnlyMapSize(List<MapSizeOption> keep) {
        Inventory inv = Core.guiService.getMapSizeInventory();
        if (inv == null) return;

        Set<Integer> keepSlots = new HashSet<>();
        for (MapSizeOption o : keep) {
            keepSlots.add(o.slot);
        }

        for (MapSizeOption opt : MapSizeOption.values()) {
            if (!keepSlots.contains(opt.slot)) {
                inv.setItem(opt.slot, null);
            }
        }

        inv.setItem(26, cancelItem());
        Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.UI_BUTTON_CLICK,
                Notifier.PitchLevel.MID,
                false,
                "Finalizing map size vote..."
        );
    }

    private void retainOnlyIncome(List<IncomeOption> keep) {
        Inventory inv = Core.guiService.getIncomeInventory();
        if (inv == null) return;

        Set<Integer> keepSlots = new HashSet<>();
        for (IncomeOption o : keep) {
            keepSlots.add(o.slot);
        }

        for (IncomeOption opt : IncomeOption.values()) {
            if (!keepSlots.contains(opt.slot)) {
                inv.setItem(opt.slot, null);
            }
        }

        inv.setItem(26, cancelItem());
        Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.UI_BUTTON_CLICK,
                Notifier.PitchLevel.MID,
                false,
                "Finalizing income vote..."
        );
    }

    private void retainOnlyGameMode(List<GameModeOption> keep) {
        Inventory inv = Core.guiService.getGameModeInventory();
        if (inv == null) return;

        Set<Integer> keepSlots = new HashSet<>();
        for (GameModeOption o : keep) {
            keepSlots.add(o.slot);
        }

        for (GameModeOption opt : GameModeOption.values()) {
            if (!keepSlots.contains(opt.slot)) {
                inv.setItem(opt.slot, null);
            }
        }

        inv.setItem(26, cancelItem());
        Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.UI_BUTTON_CLICK,
                Notifier.PitchLevel.MID,
                false,
                "Finalizing game mode vote..."
        );
    }

    private void highlightMapSizeSelected(String label, int slot) {
        Inventory inv = Core.guiService.getMapSizeInventory();
        if (inv == null) return;

        for (MapSizeOption opt : MapSizeOption.values()) {
            if (opt.slot != slot) {
                inv.setItem(opt.slot, null);
            }
        }
        inv.setItem(26, cancelItem());
        highlightOption(inv, slot, "§d" + label);
    }

    private void highlightIncomeSelected(String label, int slot) {
        Inventory inv = Core.guiService.getIncomeInventory();
        if (inv == null) return;

        for (IncomeOption opt : IncomeOption.values()) {
            if (opt.slot != slot) {
                inv.setItem(opt.slot, null);
            }
        }
        inv.setItem(26, cancelItem());
        highlightOption(inv, slot, "§d" + label);
    }

    private void highlightGameModeSelected(String label, int slot) {
        Inventory inv = Core.guiService.getGameModeInventory();
        if (inv == null) return;

        for (GameModeOption opt : GameModeOption.values()) {
            if (opt.slot != slot) {
                inv.setItem(opt.slot, null);
            }
        }
        inv.setItem(26, cancelItem());
        highlightOption(inv, slot, "§d" + label);
    }

    private void highlightOption(Inventory inv, int slot, String name) {
        ItemStack it = inv.getItem(slot);
        if (it == null) return;
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        inv.setItem(slot, it);
    }

    /* =========================================================
       low-level gui lore helpers
       ========================================================= */

    private void setVotes(Inventory inv, int slot, String label, int count) {
        ItemStack it = inv.getItem(slot);
        if (it == null) return;

        ItemMeta meta = it.getItemMeta();
        meta.setLore(votesLore(label, count));
        it.setItemMeta(meta);

        inv.setItem(slot, it);
    }

    private List<String> votesLore(String label, int count) {
        String click = "§fClick to vote §b" + label;
        if (count <= 0) {
            return Arrays.asList(
                    "",
                    click,
                    "§fVotes : §a- §f(§e0§f)"
            );
        }
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) bar.append(' ');
            bar.append('■');
        }
        return Arrays.asList(
                "",
                click,
                "§fVotes : §a" + bar + " §f(§e" + count + "§f)"
        );
    }

    private ItemStack cancelItem() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§cClose");
        meta.setLore(Arrays.asList(
                "",
                "§cCAUTION §f: This cancels the whole voting process"
        ));
        it.setItemMeta(meta);
        return it;
    }

    /* =========================================================
       utils
       ========================================================= */

    /**
     * Schedule "ending vote in 3/2/1" messages at standard intervals
     */
    private void scheduleVoteEndNotices(int startSeconds, String voteName) {
        Core.schedulers.runLater(() -> Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for " + voteName + " in §a3"
        ), startSeconds * 20L);

        Core.schedulers.runLater(() -> Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for " + voteName + " in §a2"
        ), (startSeconds + 1) * 20L);

        Core.schedulers.runLater(() -> Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for " + voteName + " in §a1"
        ), (startSeconds + 2) * 20L);
    }

    public void startCountdownInternal(int seconds, Runnable onDone) {
        if (seconds <= 0) {
            Core.schedulers.runLater(onDone, 1L);
            return;
        }

        Core.notifier.broadcast(
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