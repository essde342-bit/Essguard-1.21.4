package me.essde.essguard.checks;

import me.essde.essguard.EssGuard;
import me.essde.essguard.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

/**
 * Conservative Paper 1.21.4 movement heuristics.
 * This is evidence-based detection, not a packet-level physics simulator.
 */
public final class MovementCheck {
    private static final double WALK_PER_ATTRIBUTE = 2.16D;
    private final EssGuard plugin;

    public MovementCheck(EssGuard plugin) { this.plugin = plugin; }

    public void sample(Player p, int serverTick, long nowNs, boolean serverLag) {
        if (!p.isOnline() || p.isDead()) return;
        PlayerData d = plugin.getData(p);
        Location to = p.getLocation();
        if (!d.isInitialized()) { d.initialize(to, nowNs, serverTick); return; }
        Location from = d.getLastLocation();
        if (from == null || from.getWorld() != to.getWorld()) {
            d.initialize(to, nowNs, serverTick); return;
        }
        long elapsed = Math.max(0L, (nowNs - d.getLastSampleNs()) / 1_000_000L);
        int ticks = Math.max(1, (int)Math.round(elapsed / 50.0D));
        d.setLastSampleNs(nowNs); d.setSampleTick(serverTick);
        if (serverLag || elapsed > plugin.getConfig().getLong("safety.max-sample-gap-ms", 130L)
                || ticks > 2 || p.getPing() > plugin.getConfig().getInt("safety.max-ping-ms-for-movement", 450)
                || exempt(p) || plugin.isInGrace(p)) {
            reset(d, to, nowNs, serverTick); return;
        }

        double dx=to.getX()-from.getX(), dy=to.getY()-from.getY(), dz=to.getZ()-from.getZ();
        double horizontal=Math.hypot(dx,dz);
        boolean water=inWater(p,to);
        boolean ground=hasGroundSupport(p);
        if (ground) d.setAirborneTicks(0); else d.setAirborneTicks(d.getAirborneTicks()+ticks);
        if (ground) d.setSinceGroundTicks(0); else d.setSinceGroundTicks(d.getSinceGroundTicks()+ticks);

        if (plugin.getConfig().getBoolean("checks.speed.enabled",true))
            checkSpeed(p,d,from,to,horizontal,ground,ticks);
        if (plugin.getConfig().getBoolean("checks.fly.enabled",true))
            checkFly(p,d,dy,horizontal,ground,water,ticks);
        if (plugin.getConfig().getBoolean("checks.jesus.enabled",true))
            checkJesus(p,d,to,dy,horizontal,water);

        d.setLastDy(dy); d.setLastOnGround(ground); d.setLastLocation(to);
    }

    private void checkSpeed(Player p, PlayerData d, Location from, Location to,
                            double distance, boolean ground, int ticks) {
        if (speedExempt(p,from,to) || (!ground && d.getSinceGroundTicks()>12)) { clearSpeed(d); return; }
        double attribute=0.1D;
        if (p.getAttribute(Attribute.MOVEMENT_SPEED)!=null)
            attribute=p.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        double allowed=attribute*WALK_PER_ATTRIBUTE*(ground?1.0D:1.42D)*ticks
                + plugin.getConfig().getDouble("checks.speed.base-buffer",0.065D)*ticks;
        if (p.isSneaking()) allowed*=0.55D;
        if (hasSoulSpeed(p,from,to)) allowed*=1.25D;
        d.setSpeedWindowTicks(d.getSpeedWindowTicks()+ticks);
        d.setSpeedWindowDistance(d.getSpeedWindowDistance()+distance);
        d.setSpeedWindowAllowedDistance(d.getSpeedWindowAllowedDistance()+allowed);
        double ratio=distance/Math.max(0.001D,allowed);
        if (ratio>=plugin.getConfig().getDouble("checks.speed.severe-ratio",1.55D))
            d.setSevereSpeedTicks(d.getSevereSpeedTicks()+1);
        if (ratio>=plugin.getConfig().getDouble("checks.speed.mild-ratio",1.22D))
            d.setSpeedOverspeedTicks(d.getSpeedOverspeedTicks()+1);
        int window=plugin.getConfig().getInt("checks.speed.window-ticks",10);
        if (d.getSpeedWindowTicks()>=window) {
            double windowRatio=d.getSpeedWindowDistance()/Math.max(0.001D,d.getSpeedWindowAllowedDistance());
            boolean strong=windowRatio>=plugin.getConfig().getDouble("checks.speed.strong-window-ratio",1.42D)
                    && d.getSevereSpeedTicks()>=4;
            boolean sustained=windowRatio>=plugin.getConfig().getDouble("checks.speed.window-ratio",1.24D)
                    && d.getSpeedOverspeedTicks()>=7;
            if (strong||sustained) plugin.getViolations().flag(p,"Speed",strong?1.5D:0.8D,
                    String.format("ratio=%.3f samples=%d",windowRatio,d.getSpeedWindowTicks()));
            else plugin.getViolations().reward(p,"Speed",0.4D);
            clearSpeed(d);
        }
    }

    private void checkFly(Player p, PlayerData d, double dy, double horizontal,
                          boolean ground, boolean water, int ticks) {
        if (ground||water||flightExempt(p)||lavaContact(p.getLocation())||standingOnVehicle(p)) {
            clearFly(d); return;
        }
        if (ticks!=1) return;
        boolean hover=Math.abs(dy)<=plugin.getConfig().getDouble("checks.fly.hover-dy",0.018D);
        d.setHoverTicks(hover?d.getHoverTicks()+1:Math.max(0,d.getHoverTicks()-1));
        double expected=(d.getLastDy()-0.08D)*0.98D;
        boolean residual=Math.abs(dy-expected)>=plugin.getConfig().getDouble("checks.fly.physics-residual",0.22D);
        d.setSuspiciousVerticalTicks(residual?d.getSuspiciousVerticalTicks()+1:Math.max(0,d.getSuspiciousVerticalTicks()-1));
        boolean rise=dy>plugin.getConfig().getDouble("checks.fly.max-natural-rise-step",0.52D)
                && d.getLastDy()>-0.1D && !p.hasPotionEffect(PotionEffectType.JUMP_BOOST)
                && d.getAirborneTicks()<=6;
        d.setImpossibleRiseTicks(rise?d.getImpossibleRiseTicks()+1:Math.max(0,d.getImpossibleRiseTicks()-1));
        int hoverNeed=plugin.getConfig().getInt("checks.fly.hover-ticks",18);
        int residualNeed=plugin.getConfig().getInt("checks.fly.residual-ticks",8);
        int riseNeed=plugin.getConfig().getInt("checks.fly.rise-ticks",4);
        boolean hoverFlag=d.getHoverTicks()>=hoverNeed&&d.getAirborneTicks()>=hoverNeed;
        boolean physics=d.getSuspiciousVerticalTicks()>=residualNeed&&d.getAirborneTicks()>=residualNeed+2;
        boolean riseFlag=d.getImpossibleRiseTicks()>=riseNeed;
        boolean longHover=d.getHoverTicks()>=hoverNeed*2&&d.getAirborneTicks()>=hoverNeed*2;
        if ((hoverFlag&&physics)||(hoverFlag&&riseFlag)||(physics&&riseFlag)||longHover) {
            plugin.getViolations().flag(p,"Fly",1.4D,String.format("air=%d hover=%d residual=%d rise=%d h=%.3f",
                    d.getAirborneTicks(),d.getHoverTicks(),d.getSuspiciousVerticalTicks(),d.getImpossibleRiseTicks(),horizontal));
            d.setHoverTicks(Math.max(0,d.getHoverTicks()-5));
            d.setSuspiciousVerticalTicks(Math.max(0,d.getSuspiciousVerticalTicks()-2));
        } else plugin.getViolations().reward(p,"Fly",0.2D);
    }

    private void checkJesus(Player p, PlayerData d, Location loc, double dy,
                            double horizontal, boolean water) {
        if (water||jesusExempt(p)||!waterBelow(loc)||hasGroundSupport(p)) {
            d.setJesusCandidateTicks(Math.max(0,d.getJesusCandidateTicks()-2));
            d.setJesusStableTicks(Math.max(0,d.getJesusStableTicks()-1)); return;
        }
        boolean candidate=Math.abs(dy)<=plugin.getConfig().getDouble("checks.jesus.max-dy",0.025D)
                && horizontal>=plugin.getConfig().getDouble("checks.jesus.min-horizontal",0.08D)
                && loc.getBlock().isPassable();
        d.setJesusCandidateTicks(candidate?d.getJesusCandidateTicks()+1:Math.max(0,d.getJesusCandidateTicks()-2));
        d.setJesusStableTicks(candidate?d.getJesusStableTicks()+1:Math.max(0,d.getJesusStableTicks()-1));
        int need=plugin.getConfig().getInt("checks.jesus.surface-ticks",16);
        if(d.getJesusCandidateTicks()>=need&&d.getJesusStableTicks()>=need-2) {
            if(!standingOnVehicle(p)) plugin.getViolations().flag(p,"Jesus",1.3D,
                    "water-surface ticks="+d.getJesusCandidateTicks());
            d.setJesusCandidateTicks(0); d.setJesusStableTicks(0);
        } else plugin.getViolations().reward(p,"Jesus",0.15D);
    }

    private boolean exempt(Player p) {
        return p.hasPermission(plugin.getConfig().getString("general.bypass-permission","essguard.bypass"))
                || p.getGameMode()==GameMode.CREATIVE || p.getGameMode()==GameMode.SPECTATOR;
    }
    private boolean speedExempt(Player p,Location a,Location b) {
        return p.isFlying()||p.isGliding()||p.isInsideVehicle()||p.isSwimming()||p.isInWater()
                ||p.isRiptiding()||climbable(a.getBlock().getType())||climbable(b.getBlock().getType())
                ||specialSurface(a)||specialSurface(b)||nearWeb(b);
    }
    private boolean flightExempt(Player p) {
        return p.isFlying()||p.isGliding()||p.isInsideVehicle()||p.isSwimming()||p.isInWater()
                ||p.isRiptiding()||p.hasPotionEffect(PotionEffectType.LEVITATION)
                ||p.hasPotionEffect(PotionEffectType.SLOW_FALLING)||climbable(p.getLocation().getBlock().getType())
                ||nearWeb(p.getLocation());
    }
    private boolean jesusExempt(Player p) {
        return p.isFlying()||p.isGliding()||p.isInsideVehicle()||p.isRiptiding()||nearWeb(p.getLocation());
    }
    private boolean hasGroundSupport(Player p) {
        BoundingBox box=p.getBoundingBox(); World w=p.getWorld();
        int y=(int)Math.floor(box.getMinY()-0.08D);
        if(y<w.getMinHeight()||y>=w.getMaxHeight()) return false;
        for(int x=(int)Math.floor(box.getMinX());x<=(int)Math.floor(box.getMaxX());x++)
            for(int z=(int)Math.floor(box.getMinZ());z<=(int)Math.floor(box.getMaxZ());z++) {
                if(!w.isChunkLoaded(x>>4,z>>4)) return true;
                Block b=w.getBlockAt(x,y,z);
                if(b.isPassable()||isWater(b)) continue;
                VoxelShape shape=b.getCollisionShape();
                for(BoundingBox part:shape.getBoundingBoxes()) {
                    double top=y+part.getMaxY();
                    if(box.getMinY()-top>=-0.04D&&box.getMinY()-top<=0.12D
                            &&box.overlaps(new BoundingBox(x+part.getMinX(),top-0.01D,z+part.getMinZ(),
                            x+part.getMaxX(),top+0.01D,z+part.getMaxZ()))) return true;
                }
            }
        return false;
    }
    private boolean waterBelow(Location l) {
        World w=l.getWorld(); int y=(int)Math.floor(l.getY()-0.05D);
        if(y<w.getMinHeight()||y>=w.getMaxHeight()||!w.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4))return false;
        return isWater(w.getBlockAt(l.getBlockX(),y,l.getBlockZ()));
    }
    private boolean inWater(Player p,Location l) {
        if(p.isInWater()||p.isSwimming())return true;
        World w=l.getWorld(); int x=l.getBlockX(),z=l.getBlockZ(),y=l.getBlockY();
        return blockWater(w,x,y,z)||blockWater(w,x,y+1,z);
    }
    private boolean blockWater(World w,int x,int y,int z) {
        if(y<w.getMinHeight()||y>=w.getMaxHeight()||!w.isChunkLoaded(x>>4,z>>4))return false;
        return isWater(w.getBlockAt(x,y,z));
    }
    private boolean isWater(Block b) {
        Material m=b.getType();
        if(m==Material.WATER||m==Material.BUBBLE_COLUMN||m==Material.KELP||m==Material.KELP_PLANT
                ||m==Material.SEAGRASS||m==Material.TALL_SEAGRASS)return true;
        return b.getBlockData() instanceof Waterlogged wl&&wl.isWaterlogged();
    }
    private boolean lavaContact(Location l) {
        World w=l.getWorld(); int x=l.getBlockX(),z=l.getBlockZ(),y=l.getBlockY();
        for(int yy=y-1;yy<=y+1;yy++) if(yy>=w.getMinHeight()&&yy<w.getMaxHeight()
                &&w.isChunkLoaded(x>>4,z>>4)&&w.getBlockAt(x,yy,z).getType()==Material.LAVA)return true;
        return false;
    }
    private boolean specialSurface(Location l) {
        Material m=l.getBlock().getType(),below=l.clone().subtract(0,1,0).getBlock().getType();
        return m==Material.ICE||m==Material.PACKED_ICE||m==Material.BLUE_ICE||m==Material.FROSTED_ICE
                ||below==Material.ICE||below==Material.PACKED_ICE||below==Material.BLUE_ICE
                ||below==Material.SLIME_BLOCK||below==Material.HONEY_BLOCK||m==Material.POWDER_SNOW
                ||m==Material.SWEET_BERRY_BUSH||below==Material.SWEET_BERRY_BUSH;
    }
    private boolean climbable(Material m) {
        return m==Material.LADDER||m==Material.VINE||m==Material.SCAFFOLDING
                ||m==Material.WEEPING_VINES||m==Material.WEEPING_VINES_PLANT
                ||m==Material.TWISTING_VINES||m==Material.TWISTING_VINES_PLANT;
    }
    private boolean nearWeb(Location l) {
        World w=l.getWorld(); int x=l.getBlockX(),y=l.getBlockY(),z=l.getBlockZ();
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
            int yy=y+dy;if(yy>=w.getMinHeight()&&yy<w.getMaxHeight()&&w.isChunkLoaded((x+dx)>>4,(z+dz)>>4)
                    &&w.getBlockAt(x+dx,yy,z+dz).getType()==Material.COBWEB)return true;
        } return false;
    }
    private boolean hasSoulSpeed(Player p,Location a,Location b) {
        ItemStack boots=p.getInventory().getBoots();
        if(boots==null||!boots.containsEnchantment(Enchantment.SOUL_SPEED))return false;
        return a.clone().subtract(0,0.1D,0).getBlock().getType()==Material.SOUL_SAND
                ||a.clone().subtract(0,0.1D,0).getBlock().getType()==Material.SOUL_SOIL
                ||b.clone().subtract(0,0.1D,0).getBlock().getType()==Material.SOUL_SAND
                ||b.clone().subtract(0,0.1D,0).getBlock().getType()==Material.SOUL_SOIL;
    }
    private boolean standingOnVehicle(Player p) {
        for(Entity e:p.getNearbyEntities(1.0D,1.5D,1.0D)) if(e instanceof Boat||e instanceof Minecart)return true;
        return false;
    }
    private void clearSpeed(PlayerData d) {
        d.setSpeedWindowTicks(0);d.setSpeedWindowDistance(0);d.setSpeedWindowAllowedDistance(0);
        d.setSpeedOverspeedTicks(0);d.setSevereSpeedTicks(0);
    }
    private void clearFly(PlayerData d) {
        d.setHoverTicks(0);d.setSuspiciousVerticalTicks(0);d.setImpossibleRiseTicks(0);d.setAirborneTicks(0);
    }
    private void reset(PlayerData d,Location l,long ns,int tick) {
        d.resetMovementEvidence();d.setLastLocation(l);d.setLastSampleNs(ns);d.setSampleTick(tick);
        d.setLastDy(0);d.setLastOnGround(false);
    }
}
