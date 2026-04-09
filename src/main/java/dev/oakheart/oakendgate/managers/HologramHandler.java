package dev.oakheart.oakendgate.managers;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import dev.oakheart.oakendgate.config.ConfigManager;
import org.bukkit.Location;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class HologramHandler {

    private final ConfigManager configManager;
    private final Logger logger;
    private final Map<String, Hologram> holograms = new HashMap<>();

    public HologramHandler(ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
    }

    public void createHologram(String id, Location portalLocation) {
        if (!configManager.isHologramEnabled()) return;

        try {
            var manager = FancyHologramsPlugin.get().getHologramManager();

            // Remove existing hologram with same ID
            manager.getHologram(id).ifPresent(existing -> {
                manager.removeHologram(existing);
                holograms.remove(id);
            });

            Location holoLoc = portalLocation.clone().add(0, configManager.getHologramYOffset(), 0);
            TextHologramData data = new TextHologramData(id, holoLoc);
            data.setText(buildLines(configManager.getHologramLockedLines(), Duration.ZERO));
            data.setBillboard(configManager.getHologramBillboard());
            data.setPersistent(false);

            Hologram hologram = manager.create(data);
            manager.addHologram(hologram);
            holograms.put(id, hologram);
        } catch (Exception e) {
            logger.warning("Failed to create hologram '" + id + "': " + e.getMessage());
        }
    }

    public void updateCountdowns(Duration remaining) {
        if (holograms.isEmpty()) return;

        List<String> lines = buildLines(configManager.getHologramLockedLines(), remaining);

        for (Hologram hologram : holograms.values()) {
            try {
                if (hologram.getData() instanceof TextHologramData textData) {
                    textData.setText(lines);
                }
                hologram.queueUpdate();
            } catch (Exception e) {
                logger.warning("Failed to update hologram: " + e.getMessage());
            }
        }
    }

    public void showUnlockedMessage() {
        List<String> lines = configManager.getHologramUnlockedLines();

        for (Hologram hologram : holograms.values()) {
            try {
                if (hologram.getData() instanceof TextHologramData textData) {
                    textData.setText(lines);
                }
                hologram.queueUpdate();
            } catch (Exception e) {
                logger.warning("Failed to update hologram with unlock message: " + e.getMessage());
            }
        }
    }

    public void removeHologram(String id) {
        Hologram hologram = holograms.remove(id);
        if (hologram == null) return;

        try {
            var manager = FancyHologramsPlugin.get().getHologramManager();
            manager.removeHologram(hologram);
        } catch (Exception e) {
            logger.warning("Failed to remove hologram '" + id + "': " + e.getMessage());
        }
    }

    public void removeAll() {
        if (holograms.isEmpty()) return;

        try {
            var manager = FancyHologramsPlugin.get().getHologramManager();
            for (Hologram hologram : holograms.values()) {
                manager.removeHologram(hologram);
            }
        } catch (Exception e) {
            logger.warning("Failed to remove holograms: " + e.getMessage());
        }

        holograms.clear();
    }

    private List<String> buildLines(List<String> templates, Duration remaining) {
        long totalSeconds = remaining.getSeconds();
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        String countdown = PortalManager.formatDuration(remaining);

        List<String> result = new ArrayList<>(templates.size());
        for (String line : templates) {
            result.add(line
                    .replace("<countdown>", countdown)
                    .replace("<days>", String.valueOf(days))
                    .replace("<hours>", String.valueOf(hours))
                    .replace("<minutes>", String.valueOf(minutes))
                    .replace("<seconds>", String.valueOf(seconds))
            );
        }
        return result;
    }
}
