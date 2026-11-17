package me.essde.essguard;

import org.bukkit.Location;

/**
 * Per-player state shared by movement and combat checks.
 * All mutation is performed on the server thread.
 */
public final class PlayerData {
    private Location lastLocation;
    private Location previousLocation;
    private long lastSampleNs;
    private int sampleTick;
    private boolean initialized;

    private boolean lastOnGround;
    private boolean lastInWater;
    private boolean lastSwimming;
    private int airborneTicks;
    private int sinceGroundTicks;
    private int jumpTicks;
    private int groundTransitions;

    private int speedWindowTicks;
    private int speedOverspeedTicks;
    private int severeSpeedTicks;
    private double speedWindowDistance;
    private double speedWindowAllowedDistance;

    private int hoverTicks;
    private int suspiciousVerticalTicks;
    private int impossibleRiseTicks;

    private int waterSurfaceTicks;
    private int jesusCandidateTicks;
    private int jesusStableTicks;

    private long lastVelocityMs;
    private long lastDamageMs;

    private double lastDx;
    private double lastDy;
    private double lastDz;
    private double lastHorizontal;
    private double lastY;
    private double lastVerticalVelocity;

    public void initialize(Location location, long nowNs, int tick) {
        lastLocation = location.clone();
        previousLocation = location.clone();
        lastSampleNs = nowNs;
        sampleTick = tick;
        initialized = true;
        lastOnGround = false;
        lastInWater = false;
        lastSwimming = false;
        lastDx = lastDy = lastDz = lastHorizontal = lastY = lastVerticalVelocity = 0.0D;
        resetMovementEvidence();
    }

    public void resetMovementEvidence() {
        airborneTicks = 0;
        sinceGroundTicks = 0;
        jumpTicks = 0;
        groundTransitions = 0;
        speedWindowTicks = 0;
        speedOverspeedTicks = 0;
        severeSpeedTicks = 0;
        speedWindowDistance = 0.0D;
        speedWindowAllowedDistance = 0.0D;
        hoverTicks = 0;
        suspiciousVerticalTicks = 0;
        impossibleRiseTicks = 0;
        waterSurfaceTicks = 0;
        jesusCandidateTicks = 0;
        jesusStableTicks = 0;
    }

    public void recordSample(double horizontal, double dy, boolean onGround, boolean inWater) {
        // Keep previous-tick ground/water state intact until the caller finishes its checks.
        lastHorizontal = horizontal;
        lastVerticalVelocity = dy;
    }

    public boolean isInitialized() { return initialized; }
    public Location getLastLocation() { return lastLocation; }
    public void setLastLocation(Location value) {
        lastLocation = value == null ? null : value.clone();
        if (value != null) initialized = true;
    }
    public Location getPreviousLocation() { return previousLocation; }
    public void setPreviousLocation(Location value) {
        previousLocation = value == null ? null : value.clone();
    }

    public long getLastSampleNs() { return lastSampleNs; }
    public void setLastSampleNs(long value) { lastSampleNs = value; }
    public int getSampleTick() { return sampleTick; }
    public void setSampleTick(int value) { sampleTick = value; }

    public boolean isLastOnGround() { return lastOnGround; }
    public void setLastOnGround(boolean value) { lastOnGround = value; }
    public boolean isLastInWater() { return lastInWater; }
    public void setLastInWater(boolean value) { lastInWater = value; }
    public boolean isLastSwimming() { return lastSwimming; }
    public void setLastSwimming(boolean value) { lastSwimming = value; }

    public int getAirborneTicks() { return airborneTicks; }
    public void setAirborneTicks(int value) { airborneTicks = Math.max(0, value); }
    public int getSinceGroundTicks() { return sinceGroundTicks; }
    public void setSinceGroundTicks(int value) { sinceGroundTicks = Math.max(0, value); }
    public int getJumpTicks() { return jumpTicks; }
    public void setJumpTicks(int value) { jumpTicks = Math.max(0, value); }
    public int getGroundTransitions() { return groundTransitions; }
    public void setGroundTransitions(int value) { groundTransitions = Math.max(0, value); }

    public int getSpeedWindowTicks() { return speedWindowTicks; }
    public void setSpeedWindowTicks(int value) { speedWindowTicks = Math.max(0, value); }
    public int getSpeedOverspeedTicks() { return speedOverspeedTicks; }
    public void setSpeedOverspeedTicks(int value) { speedOverspeedTicks = Math.max(0, value); }
    public int getSevereSpeedTicks() { return severeSpeedTicks; }
    public void setSevereSpeedTicks(int value) { severeSpeedTicks = Math.max(0, value); }
    public double getSpeedWindowDistance() { return speedWindowDistance; }
    public void setSpeedWindowDistance(double value) { speedWindowDistance = Math.max(0.0D, value); }
    public double getSpeedWindowAllowedDistance() { return speedWindowAllowedDistance; }
    public void setSpeedWindowAllowedDistance(double value) { speedWindowAllowedDistance = Math.max(0.0D, value); }

    public int getHoverTicks() { return hoverTicks; }
    public void setHoverTicks(int value) { hoverTicks = Math.max(0, value); }
    public int getSuspiciousVerticalTicks() { return suspiciousVerticalTicks; }
    public void setSuspiciousVerticalTicks(int value) { suspiciousVerticalTicks = Math.max(0, value); }
    public int getImpossibleRiseTicks() { return impossibleRiseTicks; }
    public void setImpossibleRiseTicks(int value) { impossibleRiseTicks = Math.max(0, value); }

    public int getWaterSurfaceTicks() { return waterSurfaceTicks; }
    public void setWaterSurfaceTicks(int value) { waterSurfaceTicks = Math.max(0, value); }
    public int getJesusCandidateTicks() { return jesusCandidateTicks; }
    public void setJesusCandidateTicks(int value) { jesusCandidateTicks = Math.max(0, value); }
    public int getJesusStableTicks() { return jesusStableTicks; }
    public void setJesusStableTicks(int value) { jesusStableTicks = Math.max(0, value); }

    public long getLastVelocityMs() { return lastVelocityMs; }
    public void setLastVelocityMs(long value) { lastVelocityMs = value; }
    public long getLastDamageMs() { return lastDamageMs; }
    public void setLastDamageMs(long value) { lastDamageMs = value; }

    public double getLastDx() { return lastDx; }
    public void setLastDx(double value) { lastDx = value; }
    public double getLastDy() { return lastDy; }
    public void setLastDy(double value) { lastDy = value; }
    public double getLastDz() { return lastDz; }
    public void setLastDz(double value) { lastDz = value; }
    public double getLastHorizontal() { return lastHorizontal; }
    public void setLastHorizontal(double value) { lastHorizontal = value; }
    public double getLastY() { return lastY; }
    public void setLastY(double value) { lastY = value; }
    public double getLastVerticalVelocity() { return lastVerticalVelocity; }
    public void setLastVerticalVelocity(double value) { lastVerticalVelocity = value; }
}