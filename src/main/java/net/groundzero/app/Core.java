package net.groundzero.app;

import net.groundzero.game.GameConfig;
import net.groundzero.game.GameManager;
import net.groundzero.service.GuiService;
import net.groundzero.service.PlayerService;
import net.groundzero.util.Notifier;
import net.groundzero.util.Schedulers;
import org.bukkit.plugin.Plugin;

public final class Core {
    private static Plugin plugin;
    public static Plugin plugin() { return plugin; }

    public static Schedulers schedulers;
    public static Notifier notify;
    public static GuiService ui;
    public static GameConfig config;

    public static GameManager game;
    public static PlayerService playerService;

    // § \u00A7

    private Core() {}

    public static void init(Plugin p) {
        plugin = p;

        schedulers = new Schedulers(p);
        notify     = new Notifier();
        ui         = new GuiService();
        config = new GameConfig();

        game = new GameManager(schedulers, notify, ui, config);
        playerService = new PlayerService();
    }

    public static void shutdown() {
        if (schedulers != null) schedulers.cancelAll();
        plugin = null;
    }
}
