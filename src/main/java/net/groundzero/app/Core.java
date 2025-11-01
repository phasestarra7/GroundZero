package net.groundzero.app;

import net.groundzero.game.GameConfig;
import net.groundzero.game.GameManager;
import net.groundzero.service.*;
import net.groundzero.util.Notifier;
import net.groundzero.util.Schedulers;
import org.bukkit.plugin.Plugin;

public final class Core {

    public static Plugin plugin;
    public static Schedulers schedulers;
    public static Notifier notify;

    public static GuiService ui;
    public static GameConfig config;
    public static GameManager game;

    public static PlayerService playerService;
    public static LoadoutService loadoutService;
    public static DamageService damageService;
    public static ScoreboardService scoreboardService;
    public static VoteService votes;     // ← added

    private Core() {}

    public static void init(Plugin p) {
        plugin = p;
        schedulers = new Schedulers(p);
        notify = new Notifier();
        ui = new GuiService();
        config = new GameConfig();

        // main game controller
        game = new GameManager();

        // voting logic (no args, will look up Core.game inside)
        votes = new VoteService();

        playerService = new PlayerService();
        loadoutService = new LoadoutService();
        damageService = new DamageService();
        scoreboardService = new ScoreboardService();
    }
}
