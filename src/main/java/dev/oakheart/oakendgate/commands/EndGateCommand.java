package dev.oakheart.oakendgate.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.oakheart.command.CommandRegistrar;
import dev.oakheart.message.MessageManager;
import dev.oakheart.oakendgate.OakEndGate;
import dev.oakheart.oakendgate.config.ConfigManager;
import dev.oakheart.oakendgate.managers.PortalManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class EndGateCommand {

    private final OakEndGate plugin;
    private final ConfigManager configManager;
    private final PortalManager portalManager;
    private final MessageManager messageManager;

    public EndGateCommand(OakEndGate plugin, ConfigManager configManager,
                          PortalManager portalManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.portalManager = portalManager;
        this.messageManager = messageManager;
    }

    public void register() {
        CommandRegistrar.register(plugin, buildCommand(), "Manage the End portal gate", List.of("endgate", "oeg"));
    }

    private LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("oakendgate")
                // Base command - show status
                .requires(src -> src.getSender().hasPermission("oakendgate.status"))
                .executes(ctx -> executeStatus(ctx.getSource().getSender()))

                // /oakendgate reload
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("oakendgate.reload"))
                        .executes(ctx -> executeReload(ctx.getSource().getSender())))

                // /oakendgate unlock
                .then(Commands.literal("unlock")
                        .requires(src -> src.getSender().hasPermission("oakendgate.unlock"))
                        .executes(ctx -> executeUnlock(ctx.getSource().getSender())))

                // /oakendgate lock
                .then(Commands.literal("lock")
                        .requires(src -> src.getSender().hasPermission("oakendgate.lock"))
                        .executes(ctx -> executeLock(ctx.getSource().getSender())))

                // /oakendgate portals
                .then(Commands.literal("portals")
                        .requires(src -> src.getSender().hasPermission("oakendgate.portals"))
                        .executes(ctx -> executePortalsList(ctx.getSource().getSender()))

                        // /oakendgate portals add
                        .then(Commands.literal("add")
                                .requires(src -> src.getSender() instanceof Player
                                        && src.getSender().hasPermission("oakendgate.portals"))
                                .executes(ctx -> executePortalAdd(ctx.getSource().getSender())))

                        // /oakendgate portals remove <index>
                        .then(Commands.literal("remove")
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int index = IntegerArgumentType.getInteger(ctx, "index");
                                            return executePortalRemove(ctx.getSource().getSender(), index);
                                        }))))

                .build();
    }

    private int executeStatus(CommandSender sender) {
        if (portalManager.isLocked()) {
            messageManager.sendCommand(sender, "status-locked",
                    Placeholder.unparsed("countdown", portalManager.getFormattedCountdown()));

            ZonedDateTime unlock = configManager.getUnlockDateTime();
            messageManager.sendCommand(sender, "status-locked-date",
                    Placeholder.unparsed("date", unlock.format(DateTimeFormatter.ISO_LOCAL_DATE)),
                    Placeholder.unparsed("time", unlock.format(DateTimeFormatter.ofPattern("HH:mm:ss"))),
                    Placeholder.unparsed("timezone", unlock.getZone().getId()));
        } else {
            messageManager.sendCommand(sender, "status-unlocked");
        }

        messageManager.sendCommand(sender, "status-portals",
                Placeholder.unparsed("portal_count", String.valueOf(portalManager.getPortalLocations().size())));

        return Command.SINGLE_SUCCESS;
    }

    private int executeReload(CommandSender sender) {
        try {
            if (plugin.reloadPlugin()) {
                messageManager.sendCommand(sender, "reload-success");
            } else {
                messageManager.sendCommand(sender, "reload-error",
                        Placeholder.unparsed("error", "Configuration validation failed"));
            }
        } catch (Exception e) {
            messageManager.sendCommand(sender, "reload-error",
                    Placeholder.unparsed("error", e.getMessage()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeUnlock(CommandSender sender) {
        if (!portalManager.isLocked()) {
            messageManager.sendCommand(sender, "unlock-already");
            return Command.SINGLE_SUCCESS;
        }

        configManager.setManuallyUnlocked(true);
        portalManager.performUnlock();
        messageManager.sendCommand(sender, "unlock-success");
        return Command.SINGLE_SUCCESS;
    }

    private int executeLock(CommandSender sender) {
        if (portalManager.isLocked()) {
            messageManager.sendCommand(sender, "lock-already");
            return Command.SINGLE_SUCCESS;
        }

        // Check if the configured unlock date has already passed
        ZonedDateTime now = ZonedDateTime.now(configManager.getTimezone());
        if (now.isAfter(configManager.getUnlockDateTime())) {
            messageManager.sendCommand(sender, "lock-past-date");
            return Command.SINGLE_SUCCESS;
        }

        configManager.setManuallyUnlocked(false);
        portalManager.performLock();
        messageManager.sendCommand(sender, "lock-success");
        return Command.SINGLE_SUCCESS;
    }

    private int executePortalsList(CommandSender sender) {
        List<PortalManager.PortalLocation> portals = portalManager.getPortalLocations();

        if (portals.isEmpty()) {
            messageManager.sendCommand(sender, "portals-empty");
            return Command.SINGLE_SUCCESS;
        }

        messageManager.sendCommand(sender, "portals-header");
        for (int i = 0; i < portals.size(); i++) {
            messageManager.sendCommand(sender, "portals-entry",
                    Placeholder.unparsed("index", String.valueOf(i + 1)),
                    Placeholder.unparsed("location", portals.get(i).toDisplayString()));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int executePortalAdd(CommandSender sender) {
        Player player = (Player) sender;

        // Scan around the player for an End portal frame
        Block target = player.getTargetBlockExact(10);
        Block portalFrame = null;

        if (target != null && target.getType() == Material.END_PORTAL_FRAME) {
            portalFrame = target;
        } else {
            // Scan nearby blocks for portal frames
            for (int dx = -5; dx <= 5 && portalFrame == null; dx++) {
                for (int dy = -3; dy <= 3 && portalFrame == null; dy++) {
                    for (int dz = -5; dz <= 5 && portalFrame == null; dz++) {
                        Block check = player.getLocation().getBlock().getRelative(dx, dy, dz);
                        if (check.getType() == Material.END_PORTAL_FRAME) {
                            portalFrame = check;
                        }
                    }
                }
            }
        }

        if (portalFrame == null) {
            messageManager.sendCommand(sender, "portal-not-found");
            return Command.SINGLE_SUCCESS;
        }

        PortalManager.PortalLocation center = portalManager.detectPortalCenter(portalFrame);
        if (center == null) {
            messageManager.sendCommand(sender, "portal-not-found");
            return Command.SINGLE_SUCCESS;
        }

        if (portalManager.addPortal(center)) {
            messageManager.sendCommand(sender, "portal-added",
                    Placeholder.unparsed("location", center.toDisplayString()));
        } else {
            messageManager.sendCommand(sender, "portal-already-registered");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int executePortalRemove(CommandSender sender, int index) {
        if (portalManager.removePortal(index - 1)) {
            messageManager.sendCommand(sender, "portal-removed",
                    Placeholder.unparsed("index", String.valueOf(index)));
        } else {
            messageManager.sendCommand(sender, "portal-invalid-index");
        }

        return Command.SINGLE_SUCCESS;
    }
}
