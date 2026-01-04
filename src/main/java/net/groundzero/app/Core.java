package net.groundzero.app;

import net.groundzero.game.GameConfig;
import net.groundzero.game.GameManager;
import net.groundzero.game.GameSession;
import net.groundzero.item.ItemRegistry;
import net.groundzero.service.GameService;
import net.groundzero.service.combat.*;
import net.groundzero.service.game.ActionBarService;
import net.groundzero.service.game.GameRuntimeService;
import net.groundzero.service.game.ScoreboardService;
import net.groundzero.service.game.VoteService;
import net.groundzero.service.item.LoadoutService;
import net.groundzero.service.model.ProjectileModelService;
import net.groundzero.service.player.PlayerGameStateService;
import net.groundzero.service.player.PlayerService;
import net.groundzero.service.shop.ShopService;
import net.groundzero.service.tick.TickBus;
import net.groundzero.service.ui.GuiService;
import net.groundzero.util.Notifier;
import net.groundzero.util.Schedulers;
import net.groundzero.service.effect.PlayerEffectService;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

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
    public static ShopService shopService;
    public static PlayerService playerService;
    public static LoadoutService loadoutService;
    public static DamageService damageService;
    public static ScoreboardService scoreboardService;
    public static VoteService voteService;
    public static GameRuntimeService gameRuntimeService;
    public static CombatOutcomeService combatOutcomeService;
    public static CombatIdleService combatIdleService;
    public static ProjectileService projectileService;
    public static TntService tntService;
    public static PoisonService poisonService;
    public static ActionBarService actionBarService;
    public static PlayerEffectService playerEffectService;
    public static CooldownService cooldownService;
    public static ReloadService reloadService;
    public static RecoilService recoilService;
    public static ProjectileModelService projectileModelService;
    public static AutoFireService autoFireService;

    public static ItemRegistry itemRegistry;

    public static TickBus tickBus;
    public static List<GameService> gameServices = new ArrayList<>();

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
        shopService = new ShopService();
        playerService = new PlayerService();
        loadoutService = new LoadoutService();
        damageService = new DamageService();
        scoreboardService = new ScoreboardService();
        gameRuntimeService = new GameRuntimeService();
        combatOutcomeService = new CombatOutcomeService();
        combatIdleService = new CombatIdleService();
        projectileService = new ProjectileService();
        tntService = new TntService();
        poisonService = new PoisonService();
        actionBarService = new ActionBarService();
        playerEffectService = new PlayerEffectService();
        cooldownService = new CooldownService();
        reloadService = new ReloadService();
        recoilService = new RecoilService();
        projectileModelService = new ProjectileModelService();
        autoFireService = new AutoFireService();

        itemRegistry = new ItemRegistry();
        itemRegistry.init();

        tickBus = new TickBus();

        // Register game services for lifecycle management
        gameServices.add(playerStates);
        gameServices.add(voteService);
        gameServices.add(guiService);
        gameServices.add(shopService);
        gameServices.add(playerService);
        gameServices.add(loadoutService);
        gameServices.add(damageService);
        gameServices.add(scoreboardService);
        gameServices.add(gameRuntimeService);
        gameServices.add(combatIdleService);
        gameServices.add(tntService);
        gameServices.add(poisonService);
        gameServices.add(actionBarService);
        gameServices.add(playerEffectService);
        gameServices.add(cooldownService);
        gameServices.add(reloadService);
        gameServices.add(recoilService);
        gameServices.add(projectileModelService);
        gameServices.add(autoFireService);
    }
}