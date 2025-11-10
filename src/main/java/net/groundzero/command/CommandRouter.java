package net.groundzero.command;

import org.bukkit.command.*;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Thin router that delegates to PlayerCommands (and AdminCommands later). */
public final class CommandRouter implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("start", "cancel", "test");
    private final PlayerCommands playerCmds = new PlayerCommands();
    private final AdminCommands adminCmds = new AdminCommands();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;

        switch (args[0].toLowerCase()) {
            case "start":  return playerCmds.handleStart(sender);
            case "cancel": return playerCmds.handleCancel(sender);
            case "test":  return adminCmds.handleTest(sender);
            default:       return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return StringUtil.copyPartialMatches(args[0], SUBS, new ArrayList<>());
        return List.of();
    }
}
