package net.groundzero.app;

import net.groundzero.game.*;
import net.groundzero.service.*;
import net.groundzero.util.*;
import org.bukkit.plugin.Plugin;

public final class Core {

    public static Plugin plugin;
    public static GameManager game;
    public static GameSession session;

    public static Schedulers schedulers;
    public static Notifier notifier;
    public static GameConfig gameConfig;

    public static GuiService guiService;
    public static PlayerService playerService;
    public static LoadoutService loadoutService;
    public static DamageService damageService;
    public static ScoreboardService scoreboardService;
    public static VoteService voteService;     // ← added

    private Core() {}

    public static void init(Plugin p) {

        // main game controller
        plugin = p;
        game = new GameManager();
        session = game.session();

        // oracle
        schedulers = new Schedulers(p);
        notifier = new Notifier();
        gameConfig = new GameConfig();

        // services
        voteService = new VoteService();
        guiService = new GuiService();
        playerService = new PlayerService();
        loadoutService = new LoadoutService();
        damageService = new DamageService();
        scoreboardService = new ScoreboardService();
    }
}
