package net.groundzero.app;

import net.groundzero.game.GameConfig;
import net.groundzero.game.GameManager;
import net.groundzero.service.*;
import net.groundzero.util.Notifier;
import net.groundzero.util.Schedulers;
import org.bukkit.plugin.Plugin;

/**
 * Global access hub for GroundZero.
 * We centralize creation here so other classes can simply use Core.xxx
 * instead of passing dependencies around.
 */
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

    private Core() {}

    public static void init(Plugin p) {
        plugin = p;
        schedulers = new Schedulers(p);
        notify = new Notifier();
        ui = new GuiService();
        config = new GameConfig();

        // GameManager now pulls from Core.* directly
        game = new GameManager();

        playerService = new PlayerService();
        loadoutService = new LoadoutService();
        damageService = new DamageService();
        scoreboardService = new ScoreboardService();
    }

    public static void shutdown() {
        // optional cleanup
    }
}
