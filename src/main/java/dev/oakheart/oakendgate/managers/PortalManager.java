package dev.oakheart.oakendgate.managers;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oakendgate.OakEndGate;
import dev.oakheart.oakendgate.config.ConfigManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class PortalManager {

    private final OakEndGate plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Logger logger;
    private final List<PortalLocation> portalLocations = new ArrayList<>();

    private HologramHandler hologramHandler;
    private BukkitTask countdownTask;

    public PortalManager(OakEndGate plugin, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logger = plugin.getLogger();
    }

    public void setHologramHandler(HologramHandler handler) {
        this.hologramHandler = handler;
    }

    public void load() {
        portalLocations.clear();
        for (String locStr : configManager.getPortalLocationStrings()) {
            PortalLocation parsed = PortalLocation.fromString(locStr);
            if (parsed != null) {
                portalLocations.add(parsed);
            } else {
                logger.warning("Invalid portal location in config: " + locStr);
            }
        }

        if (!portalLocations.isEmpty()) {
            logger.info("Loaded " + portalLocations.size() + " portal location(s).");
        }
    }

    public void startCountdownTask() {
        stopCountdownTask();

        if (!isLocked()) {
            logger.info("The End is already unlocked.");
            return;
        }

        long intervalTicks = configManager.getHologramUpdateInterval() * 20L;
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCountdown, 20L, intervalTicks);
    }

    public void stopCountdownTask() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void tickCountdown() {
        if (!isLocked()) {
            performUnlock();
            return;
        }

        if (hologramHandler != null) {
            Duration remaining = getRemainingDuration();
            hologramHandler.updateCountdowns(remaining);
        }
    }

    public void performUnlock() {
        stopCountdownTask();

        // Remove holograms (or show unlocked message briefly)
        if (hologramHandler != null) {
            int displayTime = configManager.getHologramUnlockedDisplayTime();
            if (displayTime > 0) {
                hologramHandler.showUnlockedMessage();
                Bukkit.getScheduler().runTaskLater(plugin, () -> hologramHandler.removeAll(), displayTime * 20L);
            } else {
                hologramHandler.removeAll();
            }
        }

        // Broadcast announcement
        if (configManager.isAnnounceOnUnlock()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                messageManager.send(player, "end-unlocked");
            }
        }

        logger.info("The End has been unlocked!");
    }

    public void performLock() {
        // Recreate holograms at known portal locations
        if (hologramHandler != null) {
            hologramHandler.removeAll();
            for (PortalLocation portal : portalLocations) {
                Location loc = portal.toBukkitLocation();
                if (loc != null) {
                    hologramHandler.createHologram(portal.toHologramId(), loc);
                }
            }
            // Initial countdown update
            hologramHandler.updateCountdowns(getRemainingDuration());
        }

        startCountdownTask();
        logger.info("The End has been re-locked.");
    }

    // --- Lock state ---

    public boolean isLocked() {
        if (configManager.isManuallyUnlocked()) {
            return false;
        }
        return ZonedDateTime.now(configManager.getTimezone()).isBefore(configManager.getUnlockDateTime());
    }

    public Duration getRemainingDuration() {
        if (!isLocked()) return Duration.ZERO;
        return Duration.between(
                ZonedDateTime.now(configManager.getTimezone()),
                configManager.getUnlockDateTime()
        );
    }

    public String getFormattedCountdown() {
        return formatDuration(getRemainingDuration());
    }

    // --- Portal location management ---

    public List<PortalLocation> getPortalLocations() {
        return List.copyOf(portalLocations);
    }

    public boolean addPortal(PortalLocation portal) {
        // Check for duplicates (within 5 blocks)
        for (PortalLocation existing : portalLocations) {
            if (existing.isNear(portal, 5)) {
                return false;
            }
        }

        portalLocations.add(portal);
        savePortalLocations();

        // Spawn hologram if locked and holograms are enabled
        if (isLocked() && hologramHandler != null) {
            Location loc = portal.toBukkitLocation();
            if (loc != null) {
                hologramHandler.createHologram(portal.toHologramId(), loc);
                hologramHandler.updateCountdowns(getRemainingDuration());
            }
        }

        return true;
    }

    public boolean removePortal(int index) {
        if (index < 0 || index >= portalLocations.size()) {
            return false;
        }

        PortalLocation removed = portalLocations.remove(index);
        savePortalLocations();

        // Remove hologram
        if (hologramHandler != null) {
            hologramHandler.removeHologram(removed.toHologramId());
        }

        return true;
    }

    private void savePortalLocations() {
        List<String> locationStrings = new ArrayList<>();
        for (PortalLocation portal : portalLocations) {
            locationStrings.add(portal.toString());
        }
        configManager.setPortalLocations(locationStrings);
    }

    /**
     * Scans around a portal frame block to find the center of the End portal,
     * then registers it if not already known.
     */
    public PortalLocation detectPortalCenter(Block frameBlock) {
        World world = frameBlock.getWorld();
        int y = frameBlock.getY();
        int baseX = frameBlock.getX();
        int baseZ = frameBlock.getZ();

        int totalX = 0;
        int totalZ = 0;
        int count = 0;

        // Scan a 9x9 area at the same Y level for portal frames
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                Block check = world.getBlockAt(baseX + dx, y, baseZ + dz);
                if (check.getType() == Material.END_PORTAL_FRAME) {
                    totalX += check.getX();
                    totalZ += check.getZ();
                    count++;
                }
            }
        }

        // Need at least 8 frames to consider it a valid portal
        if (count < 8) {
            return null;
        }

        // Center is the average of all frame positions
        double centerX = (double) totalX / count + 0.5;
        double centerZ = (double) totalZ / count + 0.5;

        return new PortalLocation(world.getName(), centerX, y, centerZ);
    }

    // --- Shutdown ---

    public void shutdown() {
        stopCountdownTask();
        if (hologramHandler != null) {
            hologramHandler.removeAll();
        }
    }

    // --- Utility ---

    public static String formatDuration(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "0s";
        }

        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m ");
        if (days == 0 && hours == 0) result.append(seconds).append("s");

        return result.toString().trim();
    }

    // --- Inner record ---

    public record PortalLocation(String world, double x, int y, double z) {

        public static PortalLocation fromString(String str) {
            String[] parts = str.split(":");
            if (parts.length != 4) return null;
            try {
                return new PortalLocation(
                        parts[0],
                        Double.parseDouble(parts[1]),
                        Integer.parseInt(parts[2]),
                        Double.parseDouble(parts[3])
                );
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public String toString() {
            return world + ":" + x + ":" + y + ":" + z;
        }

        public String toHologramId() {
            return "oakendgate_" + world + "_" + (int) x + "_" + y + "_" + (int) z;
        }

        public Location toBukkitLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z);
        }

        public boolean isNear(PortalLocation other, double radius) {
            if (!world.equals(other.world)) return false;
            double dx = x - other.x;
            double dz = z - other.z;
            int dy = y - other.y;
            return (dx * dx + dy * dy + dz * dz) <= radius * radius;
        }

        public String toDisplayString() {
            return world + " (" + (int) x + ", " + y + ", " + (int) z + ")";
        }
    }
}
