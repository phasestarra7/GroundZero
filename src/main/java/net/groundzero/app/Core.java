package net.groundzero.app;

import net.groundzero.game.*;
import net.groundzero.item.ItemRegistry;
import net.groundzero.service.combat.CombatIdleService;
import net.groundzero.service.combat.CombatOutcomeService;
import net.groundzero.service.combat.DamageService;
import net.groundzero.service.combat.ProjectileService;
import net.groundzero.service.game.GameRuntimeService;
import net.groundzero.service.game.ScoreboardService;
import net.groundzero.service.game.VoteService;
import net.groundzero.service.item.LoadoutService;
import net.groundzero.service.player.PlayerGameStateService;
import net.groundzero.service.player.PlayerService;
import net.groundzero.service.tick.TickBus;
import net.groundzero.service.ui.GuiService;
import net.groundzero.util.*;
import org.bukkit.plugin.Plugin;

public final class Core {

    public static Plugin plugin;
    public static GameManager game;
    public static GameSession session;

    public static Schedulers schedulers;
    public static Notifier notifier;
    public static GameConfig gameConfig;

    // Central player state manager (NEW)
    public static PlayerGameStateService playerStates;

    public static GuiService guiService;
    public static PlayerService playerService;
    public static LoadoutService loadoutService;
    public static DamageService damageService;
    public static ScoreboardService scoreboardService;
    public static VoteService voteService;
    public static GameRuntimeService gameRuntimeService;
    public static CombatOutcomeService combatOutcomeService;
    public static CombatIdleService combatIdleService;
    public static ProjectileService projectileService;

    public static ItemRegistry itemRegistry;

    public static TickBus tickBus;

    private Core() {}

    public static void init(Plugin p) {

        // main game controller
        plugin = p;
        game = new GameManager();
        session = game.session();

        // util
        schedulers = new Schedulers(p);
        notifier = new Notifier();
        gameConfig = new GameConfig();

        // Central player state (NEW - must init before services that use it)
        playerStates = new PlayerGameStateService();

        // services
        voteService = new VoteService();
        guiService = new GuiService();
        playerService = new PlayerService();
        loadoutService = new LoadoutService();//TODO
        damageService = new DamageService();
        scoreboardService = new ScoreboardService();
        gameRuntimeService = new GameRuntimeService();
        combatOutcomeService = new CombatOutcomeService();
        combatIdleService = new CombatIdleService();
        projectileService = new ProjectileService();

        itemRegistry = new ItemRegistry();
        itemRegistry.init();

        tickBus = new TickBus();
    }
}