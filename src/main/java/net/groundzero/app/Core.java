package net.groundzero.app;

import net.groundzero.game.GameConfig;
import net.groundzero.game.GameManager;
import net.groundzero.service.*;
import net.groundzero.util.Notifier;
import net.groundzero.util.Schedulers;
import org.bukkit.plugin.Plugin;

public final class Core {

    public static Plugin plugin;
    public static GameManager game;

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

        plugin = p;
        // main game controller
        game = new GameManager();

        schedulers = new Schedulers(p);
        notifier = new Notifier();
        gameConfig = new GameConfig();

        // voting logic (no args, will look up Core.game inside)
        voteService = new VoteService();
        guiService = new GuiService();
        playerService = new PlayerService();
        loadoutService = new LoadoutService();
        damageService = new DamageService();
        scoreboardService = new ScoreboardService();
    }
}
