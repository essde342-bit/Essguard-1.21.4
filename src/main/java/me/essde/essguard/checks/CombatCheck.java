package me.essde.essguard.checks;

import me.essde.essguard.EssGuard;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Conservative melee reach observer.
 * Projectile travel distance is deliberately excluded; it is not melee reach evidence.
 */
public final class CombatCheck implements Listener {
    private final EssGuard plugin;

    public CombatCheck(EssGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        // Only direct melee hits can be used for a reach-distance estimate.
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (attacker.equals(target) || attacker.isDead() || target.isDead()) return;
        if (attacker.getWorld() != target.getWorld()) return;
        if (attacker.hasPermission(plugin.getConfig().getString("general.bypass-permission", "essguard.bypass"))
                || attacker.getGameMode() == GameMode.CREATIVE
                || attacker.getGameMode() == GameMode.SPECTATOR
                || attacker.isInsideVehicle() || attacker.isFlying() || attacker.isGliding()) return;

        BoundingBox box = target.getBoundingBox().expand(0.10D);
        Vector eye = attacker.getEyeLocation().toVector();
        double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        double distance = eye.distance(new Vector(x, y, z));

        double pingAllowance = Math.min(0.45D, Math.max(0, attacker.getPing()) / 1000.0D * 0.35D);
        double limit = 3.15D + pingAllowance;
        if (distance > limit) {
            plugin.getViolations().flag(attacker, "Reach", 0.8D,
                    String.format("distance=%.3f limit=%.3f target=%s", distance, limit, target.getName()));
        } else {
            plugin.getViolations().reward(attacker, "Reach", 0.15D);
        }
    }
}