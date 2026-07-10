package dev.oakheart.oakendgate;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oakendgate.commands.EndGateCommand;
import dev.oakheart.oakendgate.config.ConfigManager;
import dev.oakheart.oakendgate.listeners.PortalListener;
import dev.oakheart.oakendgate.managers.HologramHandler;
import dev.oakheart.oakendgate.managers.PortalManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class OakEndGate extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private PortalManager portalManager;
    private HologramHandler hologramHandler;

    @Override
    public void onEnable() {
        try {
            initializeComponents();
            registerListeners();
            registerCommands();
            initializeMetrics();

            getLogger().info("OakEndGate has been enabled!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable OakEndGate", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (portalManager != null) {
            portalManager.shutdown();
        }
        getLogger().info("OakEndGate has been disabled!");
    }

    private void initializeComponents() {
        configManager = new ConfigManager(this);
        configManager.load();

        messageManager = new MessageManager(this, getLogger());
        messageManager.load();

        portalManager = new PortalManager(this, configManager, messageManager);
        portalManager.load();

        // Initialize FancyHolograms integration if available
        if (configManager.isHologramEnabled()
                && Bukkit.getPluginManager().getPlugin("FancyHolograms") != null) {
            hologramHandler = new HologramHandler(configManager, getLogger());
            portalManager.setHologramHandler(hologramHandler);

            // Create holograms at known portal locations if locked
            if (portalManager.isLocked()) {
                for (PortalManager.PortalLocation portal : portalManager.getPortalLocations()) {
                    Location loc = portal.toBukkitLocation();
                    if (loc != null) {
                        hologramHandler.createHologram(portal.toHologramId(), loc);
                    }
                }
            }

            getLogger().info("FancyHolograms integration enabled!");
        } else if (configManager.isHologramEnabled()) {
            getLogger().info("FancyHolograms not found - hologram display disabled.");
        }

        // Start countdown task if locked
        if (portalManager.isLocked()) {
            portalManager.startCountdownTask();
        } else {
            // Loud, not info: a configured date in the past usually means the
            // config was wiped/regenerated (e.g. a data reset) — the gate is
            // silently open, which on this server is a months-early End unlock.
            getLogger().warning("The End is already unlocked (configured unlock date is in the past)."
                    + " If this is unintended, fix unlock.date in config.yml and restart.");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new PortalListener(portalManager, messageManager), this);
    }

    private void registerCommands() {
        new EndGateCommand(this, configManager, portalManager, messageManager).register();
    }

    private void initializeMetrics() {
        new org.bstats.bukkit.Metrics(this, 30684);
    }

    public boolean reloadPlugin() {
        if (!configManager.reload()) {
            return false;
        }

        messageManager.reload();

        // Rebuild portal manager state
        portalManager.shutdown();
        portalManager.load();

        // Reinitialize holograms if FancyHolograms is available
        if (configManager.isHologramEnabled()
                && Bukkit.getPluginManager().getPlugin("FancyHolograms") != null) {
            if (hologramHandler == null) {
                hologramHandler = new HologramHandler(configManager, getLogger());
            }
            portalManager.setHologramHandler(hologramHandler);

            if (portalManager.isLocked()) {
                for (PortalManager.PortalLocation portal : portalManager.getPortalLocations()) {
                    Location loc = portal.toBukkitLocation();
                    if (loc != null) {
                        hologramHandler.createHologram(portal.toHologramId(), loc);
                    }
                }
            }
        } else {
            if (hologramHandler != null) {
                hologramHandler.removeAll();
                hologramHandler = null;
            }
            portalManager.setHologramHandler(null);
        }

        // Restart countdown if locked
        if (portalManager.isLocked()) {
            portalManager.startCountdownTask();
        }

        return true;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public PortalManager getPortalManager() {
        return portalManager;
    }
}
