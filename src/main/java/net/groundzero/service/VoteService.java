package net.groundzero.service;

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
 * - drives phase transitions on Core.game
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

    public VoteService() {}

    /* =========================================================
       exposed from GameManager
       ========================================================= */

    public void startPreVoteCountdown(Runnable onDone) {
        startCountdownInternal(5, onDone);
    }

    public void startMapSizeVote() {
        Core.game.setState(GameState.VOTING_MAP_SIZE);
        acceptingVotes = true;

        votedMapSize.clear();
        mapVotes.clear();
        for (MapSizeOption opt : MapSizeOption.values()) {
            mapVotes.put(opt, 0);
        }

        // build (or rebuild) GUI first
        Core.guiService.newMapSize();

        // open for participants
        for (UUID id : Core.game.session().getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notifier.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.guiService.openMapSize(pp);
        }

        // broadcast end notices
        Core.schedulers.runLater(() -> Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for map size in §a3"
        ), 7 * 20L);

        Core.schedulers.runLater(() -> Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for map size in §a2"
        ), 8 * 20L);

        Core.schedulers.runLater(() -> Core.notifier.broadcast(
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

        Core.guiService.newIncome();

        for (UUID id : Core.game.session().getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notifier.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.guiService.openIncome(pp);
        }

        Core.schedulers.runLater(() -> Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for income multiplier in §a3"
        ), 7 * 20L);

        Core.schedulers.runLater(() -> Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for income multiplier in §a2"
        ), 8 * 20L);

        Core.schedulers.runLater(() -> Core.notifier.broadcast(
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

        // close previous
        Core.guiService.closeAllGZViews();
        Core.guiService.newGameMode();

        for (UUID id : Core.game.session().getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notifier.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.guiService.openGameMode(pp);
        }

        Core.schedulers.runLater(() -> Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for game mode in §a3"
        ), 7 * 20L);

        Core.schedulers.runLater(() -> Core.notifier.broadcast(
                Core.game.session().getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for game mode in §a2"
        ), 8 * 20L);

        Core.schedulers.runLater(() -> Core.notifier.broadcast(
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

        retainOnlyMapSize(ties);

        Core.schedulers.runLater(() -> {
            MapSizeOption chosen = pickRandom(ties);
            if (chosen != null) {
                highlightMapSizeSelected(chosen.label, chosen.slot);
                Core.game.session().setMapSize(chosen);
                Core.notifier.broadcast(
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

        retainOnlyIncome(ties);

        Core.schedulers.runLater(() -> {
            IncomeOption chosen = pickRandom(ties);
            if (chosen != null) {
                highlightIncomeSelected(chosen.label, chosen.slot);
                Core.game.session().setIncome(chosen);
                Core.notifier.broadcast(
                        Core.game.session().getParticipantsView(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        Notifier.PitchLevel.MID,
                        false,
                        "Income Multiplier selected : §a" + chosen.label
                );
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

        retainOnlyGameMode(ties);

        Core.schedulers.runLater(() -> {
            GameModeOption chosen = pickRandom(ties);
            if (chosen != null) {
                highlightGameModeSelected(chosen.label, chosen.slot);
                Core.game.session().setGameMode(chosen);
                Core.notifier.broadcast(
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

        // slot 26 = cancel
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

    private void startCountdownInternal(int seconds, Runnable onDone) {
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
