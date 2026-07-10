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
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) return; // handled above

        World to = event.getTo().getWorld();
        if (to == null || to.getEnvironment() != World.Environment.THE_END) return;
        // Scope to the MAIN end only. Other THE_END-environment worlds (the
        // rw_end resource world) are entered by plugin teleports on purpose and
        // have their own access rules — gating by environment alone would block
        // /rw for every non-bypass player.
        String mainEndName = org.bukkit.Bukkit.getWorlds().get(0).getName() + "_the_end";
        if (!to.getName().equals(mainEndName)) return;
        if (event.getPlayer().getWorld().getEnvironment() == World.Environment.THE_END) return;

        if (!portalManager.isLocked()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("oakendgate.bypass")) return;

        event.setCancelled(true);
        messageManager.send(player, "portal-blocked",
                Placeholder.unparsed("countdown", portalManager.getFormattedCountdown()));
    }
}
