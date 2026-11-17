package me.essde.essguard.checks;

import me.essde.essguard.EssGuard;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.BoundingBox;

public final class CombatCheck implements Listener {
    private final EssGuard plugin;
    public CombatCheck(EssGuard plugin){this.plugin=plugin;}
    @EventHandler(ignoreCancelled=true)
    public void onDamage(EntityDamageByEntityEvent event){
        if(!(event.getEntity() instanceof Player target))return;
        Player attacker=null;
        if(event.getDamager() instanceof Player p)attacker=p;
        else if(event.getDamager() instanceof Projectile projectile){
            ProjectileSource shooter=projectile.getShooter();
            if(shooter instanceof Player p)attacker=p;
        }
        if(attacker==null||attacker.equals(target)||attacker.hasPermission("essguard.bypass")
                ||attacker.getGameMode()==org.bukkit.GameMode.CREATIVE||attacker.isInsideVehicle())return;
        BoundingBox box=target.getBoundingBox().expand(0.10D);
        var eye=attacker.getEyeLocation().toVector();
        double x=Math.max(box.getMinX(),Math.min(eye.getX(),box.getMaxX()));
        double y=Math.max(box.getMinY(),Math.min(eye.getY(),box.getMaxY()));
        double z=Math.max(box.getMinZ(),Math.min(eye.getZ(),box.getMaxZ()));
        double distance=eye.distance(new org.bukkit.util.Vector(x,y,z));
        double allowance=Math.min(0.45D,Math.max(0,attacker.getPing())/1000.0D*0.35D);
        double limit=3.15D+allowance;
        if(distance>limit)plugin.getViolations().flag(attacker,"Reach",0.8D,
                String.format("distance=%.3f limit=%.3f target=%s",distance,limit,target.getName()));
        else plugin.getViolations().reward(attacker,"Reach",0.15D);
    }
}