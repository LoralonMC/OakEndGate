package dev.oakheart.oakendgate.listeners;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oakendgate.managers.PortalManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

public class PortalListener implements Listener {

    private final PortalManager portalManager;
    private final MessageManager messageManager;

    public PortalListener(PortalManager portalManager, MessageManager messageManager) {
        this.portalManager = portalManager;
        this.messageManager = messageManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.END_PORTAL_FRAME) return;

        // Check if player is holding an Eye of Ender
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_EYE) return;

        // Check if the frame already has an eye
        EndPortalFrame frame = (EndPortalFrame) block.getBlockData();
        if (frame.hasEye()) return;

        if (!portalManager.isLocked()) return;

        // Auto-detect and register portal location (even for bypassed players)
        PortalManager.PortalLocation center = portalManager.detectPortalCenter(block);
        if (center != null) {
            portalManager.addPortal(center);
        }

        // Allow bypass
        Player player = event.getPlayer();
        if (player.hasPermission("oakendgate.bypass")) return;

        // Block the eye placement
        event.setCancelled(true);

        messageManager.send(player, "portal-blocked",
                Placeholder.unparsed("countdown", portalManager.getFormattedCountdown()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;

        // Only block entering the End, not leaving it
        if (event.getPlayer().getWorld().getEnvironment() == World.Environment.THE_END) return;

        if (!portalManager.isLocked()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("oakendgate.bypass")) return;

        event.setCancelled(true);
        messageManager.send(player, "portal-blocked",
                Placeholder.unparsed("countdown", portalManager.getFormattedCountdown()));
    }

    /**
     * Backstop for every way into the End that is not a walked-through portal:
     * PlayerPortalEvent only covers cause END_PORTAL, but a thrown ender pearl
     * through a lit portal arrives as an ENDER_PEARL teleport, vehicles carry
     * passengers via entity portal handling, and third-party plugins teleport
     * with UNKNOWN/PLUGIN causes. A portal can be lit while locked (a bypass
     * holder placing the 12th eye lights it for everyone), so the world-level
     * gate is what actually enforces the lock.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // END_PORTAL is gated here too. PlayerPortalEvent has its own handler list, so a walked-in
        // portal never reaches this method twice; but a player riding a vehicle through the portal
        // gets no PlayerPortalEvent at all, only this teleport with cause END_PORTAL (Paper's
        // Entity.teleportCrossDimension teleports each passenger with the vehicle's transition).
        // Skipping that cause let riders into a locked End (Plugin Review 2026-11-01).
        World to = event.getTo().getWorld();
        if (!isMainEnd(to)) return;
        if (event.getPlayer().getWorld().getEnvironment() == World.Environment.THE_END) return;

        if (!portalManager.isLocked()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("oakendgate.bypass")) return;

        event.setCancelled(true);
        messageManager.send(player, "portal-blocked",
                Placeholder.unparsed("countdown", portalManager.getFormattedCountdown()));
    }

    /**
     * Stops a vehicle at a locked portal when it carries a player who may not enter, so the vehicle
     * does not cross without them (the passenger teleport above would refuse the rider, and their
     * horse or boat would end up in the End alone).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(org.bukkit.event.entity.EntityPortalEvent event) {
        if (!portalManager.isLocked()) return;
        org.bukkit.entity.Entity vehicle = event.getEntity();
        if (vehicle.getWorld().getEnvironment() == World.Environment.THE_END) return;
        org.bukkit.Location to = event.getTo();
        boolean intoMainEnd = to != null ? isMainEnd(to.getWorld())
                : event.getPortalType() == org.bukkit.PortalType.ENDER
                        && vehicle.getWorld().equals(org.bukkit.Bukkit.getWorlds().get(0));
        if (!intoMainEnd) return;
        Player blocked = firstBlockedRider(vehicle);
        if (blocked == null) return;
        event.setCancelled(true);
        messageManager.send(blocked, "portal-blocked",
                Placeholder.unparsed("countdown", portalManager.getFormattedCountdown()));
    }

    private static Player firstBlockedRider(org.bukkit.entity.Entity vehicle) {
        for (org.bukkit.entity.Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player && !player.hasPermission("oakendgate.bypass")) {
                return player;
            }
            Player nested = firstBlockedRider(passenger);
            if (nested != null) return nested;
        }
        return null;
    }

    /**
     * The main End only. Other THE_END-environment worlds (the rw_end resource world) are entered by
     * plugin teleports on purpose and have their own access rules; gating by environment alone
     * would block /rw for every non-bypass player.
     */
    private static boolean isMainEnd(World world) {
        if (world == null || world.getEnvironment() != World.Environment.THE_END) return false;
        return world.getName().equals(org.bukkit.Bukkit.getWorlds().get(0).getName() + "_the_end");
    }
}
