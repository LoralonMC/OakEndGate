package dev.oakheart.oakendgate.config;

import dev.oakheart.oakendgate.OakEndGate;
import org.bukkit.entity.Display;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ConfigManager {

    private static final String DEFAULT_DATE = "2026-06-01";
    private static final String DEFAULT_TIME = "18:00:00";
    private static final String DEFAULT_TIMEZONE = "UTC";
    private static final int DEFAULT_UPDATE_INTERVAL = 30;
    private static final double DEFAULT_Y_OFFSET = 2.0;

    private final OakEndGate plugin;
    private final Logger logger;
    private final File configFile;
    private dev.oakheart.config.ConfigManager config;

    // Cached config values
    private ZonedDateTime unlockDateTime;
    private boolean hologramEnabled;
    private int hologramUpdateInterval;
    private double hologramYOffset;
    private Display.Billboard hologramBillboard;
    private List<String> hologramLockedLines;
    private List<String> hologramUnlockedLines;
    private int hologramUnlockedDisplayTime;
    private boolean announceOnUnlock;
    private boolean manuallyUnlocked;
    private List<String> portalLocationStrings;

    public ConfigManager(OakEndGate plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        try {
            config = dev.oakheart.config.ConfigManager.load(configFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }

        mergeDefaults();

        if (!validate(config)) {
            throw new RuntimeException("Configuration validation failed");
        }

        cacheValues();
    }

    public boolean reload() {
        try {
            config.reload();
        } catch (IOException e) {
            logger.warning("Failed to reload config.yml: " + e.getMessage());
            return false;
        }

        if (!validate(config)) {
            logger.warning("Configuration reload failed validation. Keeping previous configuration.");
            return false;
        }

        cacheValues();
        logger.info("Configuration reloaded successfully.");
        return true;
    }

    private void mergeDefaults() {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream != null) {
                var defaults = dev.oakheart.config.ConfigManager.fromStream(stream);
                if (config.mergeDefaults(defaults)) {
                    config.save();
                    logger.info("Config updated with new default values.");
                }
            }
        } catch (IOException e) {
            logger.warning("Could not save config defaults: " + e.getMessage());
        }
    }

    private boolean validate(dev.oakheart.config.ConfigManager configToValidate) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Validate unlock date
        String dateStr = configToValidate.getString("unlock.date", DEFAULT_DATE);
        try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            errors.add("Invalid 'unlock.date': '" + dateStr + "' (expected YYYY-MM-DD format)");
        }

        // Validate unlock time
        String timeStr = configToValidate.getString("unlock.time", DEFAULT_TIME);
        try {
            LocalTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException e) {
            errors.add("Invalid 'unlock.time': '" + timeStr + "' (expected HH:mm:ss format)");
        }

        // Validate timezone
        String timezone = configToValidate.getString("unlock.timezone", DEFAULT_TIMEZONE);
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            errors.add("Invalid 'unlock.timezone': '" + timezone + "' (use standard timezone ID like UTC, America/New_York, etc.)");
        }

        // Validate hologram update interval
        int updateInterval = configToValidate.getInt("hologram.update-interval", DEFAULT_UPDATE_INTERVAL);
        if (updateInterval < 1) {
            warnings.add("'hologram.update-interval' must be at least 1 second, using default: " + DEFAULT_UPDATE_INTERVAL);
        }

        // Validate billboard mode
        String billboard = configToValidate.getString("hologram.billboard", "CENTER");
        try {
            Display.Billboard.valueOf(billboard.toUpperCase());
        } catch (IllegalArgumentException e) {
            warnings.add("Invalid 'hologram.billboard': '" + billboard + "', using CENTER");
        }

        // Validate unlocked display time
        int displayTime = configToValidate.getInt("hologram.unlocked-display-time", 10);
        if (displayTime < 0) {
            warnings.add("'hologram.unlocked-display-time' cannot be negative, using 0");
        }

        if (!errors.isEmpty()) {
            logger.severe("=== Configuration Errors ===");
            for (String error : errors) {
                logger.severe("  - " + error);
            }
            logger.severe("============================");
        }

        if (!warnings.isEmpty()) {
            logger.warning("=== Configuration Warnings ===");
            for (String warning : warnings) {
                logger.warning("  - " + warning);
            }
            logger.warning("==============================");
        }

        return errors.isEmpty();
    }

    private void cacheValues() {
        // Parse unlock date/time
        LocalDate date = parseDate(config.getString("unlock.date", DEFAULT_DATE));
        LocalTime time = parseTime(config.getString("unlock.time", DEFAULT_TIME));
        ZoneId zone = parseTimezone(config.getString("unlock.timezone", DEFAULT_TIMEZONE));
        unlockDateTime = ZonedDateTime.of(date, time, zone);

        // Hologram settings
        hologramEnabled = config.getBoolean("hologram.enabled", true);
        hologramUpdateInterval = Math.max(1, config.getInt("hologram.update-interval", DEFAULT_UPDATE_INTERVAL));
        hologramYOffset = config.getDouble("hologram.y-offset", DEFAULT_Y_OFFSET);
        hologramBillboard = parseBillboard(config.getString("hologram.billboard", "CENTER"));
        hologramLockedLines = config.getStringList("hologram.locked-lines");
        hologramUnlockedLines = config.getStringList("hologram.unlocked-lines");
        hologramUnlockedDisplayTime = Math.max(0, config.getInt("hologram.unlocked-display-time", 10));

        // Announcements
        announceOnUnlock = config.getBoolean("announce.on-unlock", true);

        // State
        manuallyUnlocked = config.getBoolean("manually-unlocked", false);

        // Portal locations
        portalLocationStrings = config.getStringList("portals");
    }

    private LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            logger.warning("Invalid date '" + dateStr + "', using default: " + DEFAULT_DATE);
            return LocalDate.parse(DEFAULT_DATE);
        }
    }

    private LocalTime parseTime(String timeStr) {
        try {
            return LocalTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException e) {
            logger.warning("Invalid time '" + timeStr + "', using default: " + DEFAULT_TIME);
            return LocalTime.parse(DEFAULT_TIME);
        }
    }

    private ZoneId parseTimezone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            logger.warning("Invalid timezone '" + timezone + "', using default: " + DEFAULT_TIMEZONE);
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    private Display.Billboard parseBillboard(String value) {
        try {
            return Display.Billboard.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Display.Billboard.CENTER;
        }
    }

    // --- Runtime config writes ---

    public void setManuallyUnlocked(boolean value) {
        this.manuallyUnlocked = value;
        config.set("manually-unlocked", value);
        try {
            config.save();
        } catch (IOException e) {
            logger.warning("Failed to save manually-unlocked state: " + e.getMessage());
        }
    }

    public void setPortalLocations(List<String> locations) {
        this.portalLocationStrings = new ArrayList<>(locations);
        config.remove("portals");
        config.set("portals", locations);
        try {
            config.save();
        } catch (IOException e) {
            logger.warning("Failed to save portal locations: " + e.getMessage());
        }
    }

    // --- Getters ---

    public dev.oakheart.config.ConfigManager getConfig() {
        return config;
    }

    public ZonedDateTime getUnlockDateTime() {
        return unlockDateTime;
    }

    public ZoneId getTimezone() {
        return unlockDateTime.getZone();
    }

    public boolean isHologramEnabled() {
        return hologramEnabled;
    }

    public int getHologramUpdateInterval() {
        return hologramUpdateInterval;
    }

    public double getHologramYOffset() {
        return hologramYOffset;
    }

    public Display.Billboard getHologramBillboard() {
        return hologramBillboard;
    }

    public List<String> getHologramLockedLines() {
        return hologramLockedLines;
    }

    public List<String> getHologramUnlockedLines() {
        return hologramUnlockedLines;
    }

    public int getHologramUnlockedDisplayTime() {
        return hologramUnlockedDisplayTime;
    }

    public boolean isAnnounceOnUnlock() {
        return announceOnUnlock;
    }

    public boolean isManuallyUnlocked() {
        return manuallyUnlocked;
    }

    public List<String> getPortalLocationStrings() {
        return portalLocationStrings;
    }
}
