package net.groundzero.app;

import net.groundzero.command.CommandRouter;
import net.groundzero.listener.combat.CombatListener;
import net.groundzero.listener.player.ItemInteractionListener;
import net.groundzero.listener.player.PlayerLifecycleListener;
import net.groundzero.listener.ui.GuiClickListener;
import net.groundzero.listener.world.WorldProtectionListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class GroundZero extends JavaPlugin {

    @Override
    public void onEnable() {
        // 1) Wire services/managers
        Core.init(this);

        // 2) Register commands (thin router)
        getCommand("groundzero").setExecutor(new CommandRouter());
        getCommand("groundzero").setTabCompleter(new CommandRouter());

        // 3) Register listeners (thin, delegate to services/game)
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerLifecycleListener(), this);
        pm.registerEvents(new ItemInteractionListener(), this);
        pm.registerEvents(new GuiClickListener(), this);
        pm.registerEvents(new CombatListener(), this);
        pm.registerEvents(new WorldProtectionListener(), this);

        getLogger().info("GroundZero enabled");
    }

    @Override
    public void onDisable() {
        // Ensure every scheduled task is cancelled and state cleaned
        Core.game.cancelAll(); // TODO: cleanup all, force shutdown
        getLogger().info("GroundZero disabled");
    }
}
