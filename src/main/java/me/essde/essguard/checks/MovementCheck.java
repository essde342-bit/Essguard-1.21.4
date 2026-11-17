package me.essde.essguard.checks;

import me.essde.essguard.EssGuard;
import me.essde.essguard.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

/**
 * Conservative server-side movement checks (Speed / Fly / Jesus) for Paper 1.21.4, Java 21.
 * Uses attributes, multi-tick evidence windows and loaded-chunk-only world queries.
 * Heuristic checks are alert-only and require live server tuning.
 */
public final class MovementCheck {
    private static final double WALK_BLOCKS_PER_ATTR = 2.158D;
    private static final double AIR_FACTOR = 1.45D;
    private final EssGuard plugin;

    public MovementCheck(EssGuard plugin) { this.plugin = plugin; }

    public void sample(Player p, int serverTick, long nowNs, boolean serverLag) {
        if (!p.isOnline() || p.isDead()) return;
        FileConfiguration cfg = plugin.getConfig();
        boolean speedOn = cfg.getBoolean("checks.speed.enabled", true);
        boolean flyOn = cfg.getBoolean("checks.fly.enabled", true);
        boolean jesusOn = cfg.getBoolean("checks.jesus.enabled", true);
        if (!speedOn && !flyOn && !jesusOn) return;

        PlayerData pd = plugin.getData(p);
        Location current = p.getLocation();
        if (p.getPing() > cfg.getInt("safety.max-ping-ms-for-movement", 450)) {
            resetSoft(pd, current, nowNs, serverTick);
            return;
        }
        if (!pd.isInitialized()) {
            pd.initialize(current, nowNs, serverTick);
            return;
        }
        Location previous = pd.getLastLocation();
        if (previous == null || previous.getWorld() != current.getWorld()) {
            pd.initialize(current, nowNs, serverTick);
            return;
        }

        long elapsedMs = Math.max(0L, (nowNs - pd.getLastSampleNs()) / 1_000_000L);
        int ticks = (int) Math.max(1L, Math.round(elapsedMs / 50.0D));
        pd.setLastSampleNs(nowNs);
        pd.setSampleTick(serverTick);
        if (serverLag || elapsedMs > cfg.getLong("safety.max-sample-gap-ms", 130L) || ticks > 2) {
            resetSoft(pd, current, nowNs, serverTick);
            return;
        }
        if (plugin.isInGrace(p) || isGlobalExempt(p) || hasCustomScale(p)) {
            resetSoft(pd, current, nowNs, serverTick);
            return;
        }

        double dx = current.getX() - previous.getX();
        double dy = current.getY() - previous.getY();
        double dz = current.getZ() - previous.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        boolean inWater = isWaterContact(p, current);
        boolean onGround = hasGroundSupport(p);
        pd.recordSample(horizontal, dy, onGround, inWater);

        if (onGround && !pd.isLastOnGround()) {
            pd.setGroundTransitions(pd.getGroundTransitions() + 1);
            pd.setJumpTicks(0);
            pd.setSinceGroundTicks(0);
        } else if (!onGround) {
            pd.setSinceGroundTicks(pd.getSinceGroundTicks() + 1);
        }
        if (!onGround) pd.setAirborneTicks(pd.getAirborneTicks() + 1);
        else pd.setAirborneTicks(0);

        if (speedOn) speed(p, pd, previous, current, horizontal, onGround, ticks, cfg);
        else clearSpeed(pd);
        if (flyOn) fly(p, pd, previous, current, dy, horizontal, onGround, inWater, ticks, cfg);
        else clearFly(pd);
        if (jesusOn) jesus(p, pd, previous, current, dy, horizontal, inWater, cfg);
        else clearJesus(pd);

        pd.setLastDx(dx);
        pd.setLastDy(dy);
        pd.setLastDz(dz);
        pd.setLastHorizontal(horizontal);
        pd.setLastY(current.getY());
        pd.setLastVerticalVelocity(dy);
        pd.setLastOnGround(onGround);
        pd.setLastInWater(inWater);
        pd.setLastSwimming(p.isSwimming());
        pd.setPreviousLocation(previous);
        pd.setLastLocation(current);
    }

    private void speed(Player p, PlayerData pd, Location from, Location to, double horizontal,
                       boolean onGround, int ticks, FileConfiguration cfg) {
        if (speedExempt(p, pd, from, to) || (!onGround && pd.getSinceGroundTicks() > 12)) {
            clearSpeed(pd);
            return;
        }
        boolean airLike = !onGround || !pd.isLastOnGround();
        double allowed = allowedPerTick(p, from, to, airLike, cfg) * ticks;
        pd.setSpeedWindowTicks(pd.getSpeedWindowTicks() + 1);
        pd.setSpeedWindowDistance(pd.getSpeedWindowDistance() + horizontal);
        pd.setSpeedWindowAllowedDistance(pd.getSpeedWindowAllowedDistance() + allowed);

        double ratio = horizontal / Math.max(0.001D, allowed);
        double severeRatio = cfg.getDouble("checks.speed.severe-ratio", 1.38D);
        double mildRatio = cfg.getDouble("checks.speed.mild-ratio", 1.16D);
        if (ratio >= severeRatio && horizontal >= cfg.getDouble("checks.speed.min-severe-step", 0.33D))
            pd.setSevereSpeedTicks(pd.getSevereSpeedTicks() + 1);
        else pd.setSevereSpeedTicks(Math.max(0, pd.getSevereSpeedTicks() - 1));
        if (ratio >= mildRatio) pd.setSpeedOverspeedTicks(pd.getSpeedOverspeedTicks() + 1);
        else pd.setSpeedOverspeedTicks(Math.max(0, pd.getSpeedOverspeedTicks() - 1));

        int window = cfg.getInt("checks.speed.window-ticks", 10);
        if (pd.getSpeedWindowTicks() >= window) {
            double allowedSum = Math.max(0.001D, pd.getSpeedWindowAllowedDistance());
            double ratioWindow = pd.getSpeedWindowDistance() / allowedSum;
            boolean sustained = ratioWindow >= cfg.getDouble("checks.speed.window-ratio", 1.18D)
                    && pd.getSpeedOverspeedTicks() >= cfg.getInt("checks.speed.min-mild-samples", 6);
            boolean strong = ratioWindow >= cfg.getDouble("checks.speed.strong-window-ratio", 1.30D)
                    && pd.getSevereSpeedTicks() >= cfg.getInt("checks.speed.min-severe-samples", 3);
            if (strong || sustained) {
                double points = strong ? 1.8D : 0.9D;
                plugin.getViolations().flag(p, "Speed", points,
                        String.format("window=%.3fx allowed/tick=%.3f severe=%d/%d",
                                ratioWindow, allowedSum / pd.getSpeedWindowTicks(),
                                pd.getSevereSpeedTicks(), pd.getSpeedWindowTicks()));
            } else plugin.getViolations().reward(p, "Speed", 0.50D);
            clearSpeed(pd);
        }
    }

    private double allowedPerTick(Player p, Location from, Location to, boolean airLike, FileConfiguration cfg) {
        AttributeInstance inst = p.getAttribute(Attribute.MOVEMENT_SPEED);
        double attr = inst == null ? 0.1D : inst.getValue();
        double base = attr * WALK_BLOCKS_PER_ATTR;
        if (p.isSneaking()) base *= 0.30D;
        if (p.isHandRaised() || p.isBlocking()) base *= 0.45D;
        if (isSoulSand(from) || isSoulSand(to)) base *= 0.40D;
        if (airLike) base *= AIR_FACTOR;
        return base + cfg.getDouble("checks.speed.base-buffer", 0.045D);
    }

    private void fly(Player p, PlayerData pd, Location from, Location to, double dy, double horizontal,
                     boolean onGround, boolean inWater, int ticks, FileConfiguration cfg) {
        if (inWater || onGround || flightExempt(p, pd, from, to) || isLavaContact(to)) {
            clearFly(pd);
            return;
        }
        if (ticks != 1) return;
        if (hasSolidAbove(p)) {
            pd.setHoverTicks(Math.max(0, pd.getHoverTicks() - 1));
            pd.setSuspiciousVerticalTicks(Math.max(0, pd.getSuspiciousVerticalTicks() - 1));
            pd.setImpossibleRiseTicks(Math.max(0, pd.getImpossibleRiseTicks() - 1));
            return;
        }

        int air = pd.getAirborneTicks();
        boolean lowVerticalMotion = Math.abs(dy) <= cfg.getDouble("checks.fly.hover-dy", 0.017D);
        if (lowVerticalMotion) pd.setHoverTicks(pd.getHoverTicks() + 1);
        else pd.setHoverTicks(Math.max(0, pd.getHoverTicks() - 1));

        double gravity = attributeValue(p, Attribute.GRAVITY, 0.08D);
        double expected = (pd.getLastDy() - gravity) * 0.98D;
        double residual = Math.abs(dy - expected);
        boolean residualLarge = residual >= cfg.getDouble("checks.fly.physics-residual", 0.16D);
        if (air >= 4 && residualLarge) pd.setSuspiciousVerticalTicks(pd.getSuspiciousVerticalTicks() + 1);
        else pd.setSuspiciousVerticalTicks(Math.max(0, pd.getSuspiciousVerticalTicks() - 1));

        double jump = attributeValue(p, Attribute.JUMP_STRENGTH, 0.42D);
        double maxRise = Math.max(cfg.getDouble("checks.fly.max-natural-rise-step", 0.48D), jump + 0.06D);
        boolean impossibleRise = dy > maxRise && pd.getLastDy() > -0.1D
                && !hasJumpBoost(p) && air <= 6;
        if (impossibleRise) pd.setImpossibleRiseTicks(pd.getImpossibleRiseTicks() + 1);
        else pd.setImpossibleRiseTicks(Math.max(0, pd.getImpossibleRiseTicks() - 1));

        int hoverNeed = cfg.getInt("checks.fly.hover-ticks", 14);
        int residualNeed = cfg.getInt("checks.fly.residual-ticks", 6);
        int riseNeed = cfg.getInt("checks.fly.rise-ticks", 3);
        boolean hover = pd.getHoverTicks() >= hoverNeed && air >= hoverNeed;
        boolean physics = pd.getSuspiciousVerticalTicks() >= residualNeed && air >= residualNeed + 2;
        boolean rise = pd.getImpossibleRiseTicks() >= riseNeed;
        boolean highConfidence = (hover && physics) || (hover && rise) || (physics && rise);
        boolean extreme = rise && pd.getImpossibleRiseTicks() >= riseNeed + 2;
        boolean longHover = pd.getHoverTicks() >= hoverNeed * 2 && air >= hoverNeed * 2;

        if (highConfidence || extreme || longHover) {
            if (standingOnEntity(p)) {
                clearFly(pd);
                return;
            }
            double points = highConfidence || longHover ? 1.7D : 1.2D;
            plugin.getViolations().flag(p, "Fly", points,
                    String.format("air=%d hover=%d residual=%d rise=%d dy=%.3f h=%.3f", air,
                            pd.getHoverTicks(), pd.getSuspiciousVerticalTicks(),
                            pd.getImpossibleRiseTicks(), dy, horizontal));
            pd.setHoverTicks(Math.max(0, pd.getHoverTicks() - 5));
            pd.setSuspiciousVerticalTicks(Math.max(0, pd.getSuspiciousVerticalTicks() - 2));
            pd.setImpossibleRiseTicks(Math.max(0, pd.getImpossibleRiseTicks() - 1));
        } else plugin.getViolations().reward(p, "Fly", 0.35D);
    }

    private void jesus(Player p, PlayerData pd, Location from, Location to, double dy, double horizontal,
                       boolean inWater, FileConfiguration cfg) {
        if (jesusExempt(p, pd, from, to) || inWater) {
            clearJesus(pd);
            return;
        }
        World w = to.getWorld();
        int x = to.getBlockX(), z = to.getBlockZ();
        Block below = blockAt(w, x, (int) Math.floor(to.getY() - 0.05D), z);
        Block feet = blockAt(w, x, to.getBlockY(), z);
        if (below == null || feet == null) {
            clearJesus(pd);
            return;
        }
        boolean waterBelow = isPureWater(below.getType());
        boolean feetPassable = feet.isPassable() && !isWater(feet);
        boolean moving = horizontal >= cfg.getDouble("checks.jesus.min-horizontal", 0.075D);
        boolean stableY = Math.abs(dy) <= cfg.getDouble("checks.jesus.max-dy", 0.025D);
        boolean noSolidSupport = !hasGroundSupport(p);
        if (waterBelow && feetPassable && moving && stableY && noSolidSupport)
            pd.setJesusCandidateTicks(pd.getJesusCandidateTicks() + 1);
        else pd.setJesusCandidateTicks(Math.max(0, pd.getJesusCandidateTicks() - 2));
        if (waterBelow && stableY && horizontal > 0.04D)
            pd.setJesusStableTicks(pd.getJesusStableTicks() + 1);
        else pd.setJesusStableTicks(Math.max(0, pd.getJesusStableTicks() - 1));

        int need = cfg.getInt("checks.jesus.surface-ticks", 14);
        boolean candidate = pd.getJesusCandidateTicks() >= need;
        boolean corroborated = pd.getJesusStableTicks() >= Math.max(need - 2, 8);
        if (candidate && corroborated) {
            if (standingOnEntity(p)) {
                clearJesus(pd);
                return;
            }
            plugin.getViolations().flag(p, "Jesus", 1.5D,
                    String.format("surface=%d stable=%d h=%.3f",
                            pd.getJesusCandidateTicks(), pd.getJesusStableTicks(), horizontal));
            pd.setJesusCandidateTicks(0);
            pd.setJesusStableTicks(0);
        } else plugin.getViolations().reward(p, "Jesus", 0.25D);
    }

    private boolean speedExempt(Player p, PlayerData pd, Location from, Location to) {
        if (isGlobalExempt(p) || p.isFlying() || p.getAllowFlight() || p.isGliding()
                || p.isInsideVehicle() || p.isSwimming() || p.isInWater() || p.isRiptiding()
                || recentExternalMotion(pd) || isClimbable(from) || isClimbable(to)
                || isSpecialSurface(from, to) || isNearWeb(to)) return true;
        return hasSoulSpeedOnSurface(p, from, to);
    }

    private boolean flightExempt(Player p, PlayerData pd, Location from, Location to) {
        if (isGlobalExempt(p) || p.isFlying() || p.getAllowFlight() || p.isGliding()
                || p.isInsideVehicle() || p.isInWater() || p.isSwimming() || p.isRiptiding()
                || p.hasPotionEffect(PotionEffectType.LEVITATION)
                || p.hasPotionEffect(PotionEffectType.SLOW_FALLING) || recentExternalMotion(pd)
                || isClimbable(from) || isClimbable(to) || isSpecialSurface(from, to)) return true;
        return isNearWeb(to);
    }

    private boolean jesusExempt(Player p, PlayerData pd, Location from, Location to) {
        return isGlobalExempt(p) || p.isFlying() || p.getAllowFlight() || p.isInsideVehicle()
                || p.isGliding() || p.isRiptiding() || recentExternalMotion(pd)
                || isSpecialSurface(from, to) || isNearWeb(to);
    }

    private boolean isGlobalExempt(Player p) {
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    private boolean hasCustomScale(Player p) {
        return Math.abs(attributeValue(p, Attribute.SCALE, 1.0D) - 1.0D) > 0.001D;
    }

    private double attributeValue(Player p, Attribute attribute, double fallback) {
        AttributeInstance inst = p.getAttribute(attribute);
        return inst == null ? fallback : inst.getValue();
    }

    private boolean recentExternalMotion(PlayerData pd) {
        FileConfiguration cfg = plugin.getConfig();
        long now = System.currentTimeMillis();
        return now - pd.getLastVelocityMs() < cfg.getLong("safety.velocity-grace-ms", 950L)
                || now - pd.getLastDamageMs() < cfg.getLong("safety.damage-grace-ms", 600L);
    }

    /** Never fetches a block from an unloaded chunk. */
    private Block blockAt(World w, int x, int y, int z) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight() || !w.isChunkLoaded(x >> 4, z >> 4)) return null;
        return w.getBlockAt(x, y, z);
    }

    private Material typeAt(World w, int x, int y, int z) {
        Block b = blockAt(w, x, y, z);
        return b == null ? Material.AIR : b.getType();
    }

    private boolean hasGroundSupport(Player p) {
        BoundingBox box = p.getBoundingBox();
        World w = p.getWorld();
        double minY = box.getMinY();
        int x0 = (int) Math.floor(box.getMinX()), x1 = (int) Math.floor(box.getMaxX());
        int z0 = (int) Math.floor(box.getMinZ()), z1 = (int) Math.floor(box.getMaxZ());
        int yTop = (int) Math.floor(minY - 0.1D);
        for (int y = yTop; y >= yTop - 1; y--) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    Block b = blockAt(w, x, y, z);
                    if (b == null) return true; // unknown terrain is not evidence of cheating
                    Material m = b.getType();
                    if (m.isAir() || isWater(b) || Tag.CLIMBABLE.isTagged(m) || m == Material.COBWEB) continue;
                    VoxelShape shape = b.getCollisionShape();
                    for (BoundingBox bb : shape.getBoundingBoxes()) {
                        double top = y + bb.getMaxY();
                        double gap = minY - top;
                        if (gap < -0.04D || gap > 0.12D) continue;
                        boolean xo = box.getMaxX() > x + bb.getMinX() + 0.02D
                                && box.getMinX() < x + bb.getMaxX() - 0.02D;
                        boolean zo = box.getMaxZ() > z + bb.getMinZ() + 0.02D
                                && box.getMinZ() < z + bb.getMaxZ() - 0.02D;
                        if (xo && zo) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasSolidAbove(Player p) {
        BoundingBox box = p.getBoundingBox();
        World w = p.getWorld();
        int y = (int) Math.floor(box.getMaxY() + 0.15D);
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                Block b = blockAt(w, x, y, z);
                if (b != null && !b.isPassable()) return true;
            }
        }
        return false;
    }

    private boolean standingOnEntity(Player p) {
        BoundingBox player = p.getBoundingBox();
        for (Entity e : p.getNearbyEntities(1.2D, 1.6D, 1.2D)) {
            if (!(e instanceof Boat || e instanceof Minecart || e instanceof Shulker)) continue;
            BoundingBox entity = e.getBoundingBox();
            double gap = player.getMinY() - entity.getMaxY();
            if (gap >= -0.08D && gap <= 0.18D
                    && player.getMaxX() > entity.getMinX() && player.getMinX() < entity.getMaxX()
                    && player.getMaxZ() > entity.getMinZ() && player.getMinZ() < entity.getMaxZ()) return true;
        }
        return false;
    }

    private boolean isSpecialSurface(Location from, Location to) {
        World w = to.getWorld();
        Material a = typeAt(w, from.getBlockX(), from.getBlockY(), from.getBlockZ());
        Material b = typeAt(w, to.getBlockX(), to.getBlockY(), to.getBlockZ());
        Material belowA = typeAt(w, from.getBlockX(), from.getBlockY() - 1, from.getBlockZ());
        Material belowB = typeAt(w, to.getBlockX(), to.getBlockY() - 1, to.getBlockZ());
        return isIce(a) || isIce(b) || isIce(belowA) || isIce(belowB)
                || belowA == Material.SLIME_BLOCK || belowB == Material.SLIME_BLOCK
                || belowA == Material.HONEY_BLOCK || belowB == Material.HONEY_BLOCK
                || a == Material.HONEY_BLOCK || b == Material.HONEY_BLOCK
                || a == Material.POWDER_SNOW || b == Material.POWDER_SNOW
                || a == Material.SWEET_BERRY_BUSH || b == Material.SWEET_BERRY_BUSH
                || belowA == Material.SWEET_BERRY_BUSH || belowB == Material.SWEET_BERRY_BUSH
                || Tag.BEDS.isTagged(belowA) || Tag.BEDS.isTagged(belowB)
                || belowA == Material.MOVING_PISTON || belowB == Material.MOVING_PISTON
                || a == Material.MOVING_PISTON || b == Material.MOVING_PISTON
                || a == Material.BUBBLE_COLUMN || b == Material.BUBBLE_COLUMN
                || belowA == Material.BIG_DRIPLEAF || belowB == Material.BIG_DRIPLEAF
                || belowA == Material.TRIAL_SPAWNER || belowB == Material.TRIAL_SPAWNER
                || belowA == Material.VAULT || belowB == Material.VAULT;
    }

    private boolean hasSoulSpeedOnSurface(Player p, Location from, Location to) {
        ItemStack boots = p.getInventory().getBoots();
        if (boots == null || !boots.containsEnchantment(Enchantment.SOUL_SPEED)) return false;
        World w = to.getWorld();
        Material f = typeAt(w, from.getBlockX(), (int) Math.floor(from.getY() - 0.1D), from.getBlockZ());
        Material t = typeAt(w, to.getBlockX(), (int) Math.floor(to.getY() - 0.1D), to.getBlockZ());
        return f == Material.SOUL_SAND || f == Material.SOUL_SOIL || t == Material.SOUL_SAND || t == Material.SOUL_SOIL;
    }

    private boolean isNearWeb(Location to) {
        World w = to.getWorld();
        int bx = to.getBlockX(), by = to.getBlockY(), bz = to.getBlockZ();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++)
            if (typeAt(w, bx + x, by + y, bz + z) == Material.COBWEB) return true;
        return false;
    }

    private boolean isClimbable(Location l) {
        return Tag.CLIMBABLE.isTagged(typeAt(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ()));
    }

    private boolean isIce(Material m) {
        return m == Material.ICE || m == Material.PACKED_ICE || m == Material.BLUE_ICE || m == Material.FROSTED_ICE;
    }

    private boolean isSoulSand(Location l) {
        return typeAt(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ()) == Material.SOUL_SAND;
    }

    private boolean isPureWater(Material m) {
        return m == Material.WATER || m == Material.BUBBLE_COLUMN;
    }

    private boolean isWater(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        if (isPureWater(m) || m == Material.KELP || m == Material.KELP_PLANT
                || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS) return true;
        BlockData data = b.getBlockData();
        return data instanceof Waterlogged wl && wl.isWaterlogged();
    }

    private boolean isWaterContact(Player p, Location loc) {
        if (p.isInWater() || p.isSwimming()) return true;
        World w = loc.getWorld();
        int x = loc.getBlockX(), z = loc.getBlockZ(), y = loc.getBlockY();
        return isWater(blockAt(w, x, y, z)) || isWater(blockAt(w, x, y + 1, z));
    }

    private boolean isLavaContact(Location loc) {
        World w = loc.getWorld();
        int x = loc.getBlockX(), z = loc.getBlockZ(), y = loc.getBlockY();
        return typeAt(w, x, y, z) == Material.LAVA || typeAt(w, x, y + 1, z) == Material.LAVA
                || typeAt(w, x, y - 1, z) == Material.LAVA;
    }

    private boolean hasJumpBoost(Player p) {
        return p.hasPotionEffect(PotionEffectType.JUMP_BOOST);
    }

    private void clearSpeed(PlayerData pd) {
        pd.setSpeedWindowTicks(0);
        pd.setSpeedOverspeedTicks(0);
        pd.setSpeedWindowDistance(0.0D);
        pd.setSpeedWindowAllowedDistance(0.0D);
        pd.setSevereSpeedTicks(0);
    }

    private void clearFly(PlayerData pd) {
        pd.setAirborneTicks(0);
        pd.setHoverTicks(0);
        pd.setSuspiciousVerticalTicks(0);
        pd.setImpossibleRiseTicks(0);
    }

    private void clearJesus(PlayerData pd) {
        pd.setWaterSurfaceTicks(0);
        pd.setJesusCandidateTicks(0);
        pd.setJesusStableTicks(0);
    }

    private void resetSoft(PlayerData pd, Location current, long nowNs, int serverTick) {
        pd.resetMovementEvidence();
        pd.setPreviousLocation(current);
        pd.setLastLocation(current);
        pd.setLastSampleNs(nowNs);
        pd.setSampleTick(serverTick);
        pd.setLastDx(0.0D);
        pd.setLastDy(0.0D);
        pd.setLastDz(0.0D);
        pd.setLastHorizontal(0.0D);
        pd.setLastY(current.getY());
        pd.setLastVerticalVelocity(0.0D);
        pd.setLastOnGround(false);
        pd.setLastInWater(false);
        pd.setLastSwimming(false);
    }
}