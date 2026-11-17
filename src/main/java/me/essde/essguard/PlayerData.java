package me.essde.essguard;

import org.bukkit.Location;

public final class PlayerData {
    private Location lastLocation;
    private long lastSampleNs;
    private int sampleTick;
    private boolean initialized;
    private boolean lastOnGround;
    private int airborneTicks, sinceGroundTicks;
    private int speedWindowTicks, speedOverspeedTicks, severeSpeedTicks;
    private double speedWindowDistance, speedWindowAllowedDistance;
    private int hoverTicks, suspiciousVerticalTicks, impossibleRiseTicks;
    private int jesusCandidateTicks, jesusStableTicks;
    private long lastVelocityMs, lastDamageMs;
    private double lastDy;

    public void initialize(Location location, long nowNs, int tick) {
        lastLocation = location.clone(); lastSampleNs = nowNs; sampleTick = tick; initialized = true;
        resetMovementEvidence();
    }
    public void resetMovementEvidence() {
        airborneTicks=0; sinceGroundTicks=0; speedWindowTicks=0; speedOverspeedTicks=0; severeSpeedTicks=0;
        speedWindowDistance=0; speedWindowAllowedDistance=0; hoverTicks=0; suspiciousVerticalTicks=0;
        impossibleRiseTicks=0; jesusCandidateTicks=0; jesusStableTicks=0;
    }
    public boolean isInitialized(){return initialized;}
    public Location getLastLocation(){return lastLocation;}
    public void setLastLocation(Location v){lastLocation=v.clone();}
    public long getLastSampleNs(){return lastSampleNs;}
    public void setLastSampleNs(long v){lastSampleNs=v;}
    public int getSampleTick(){return sampleTick;}
    public void setSampleTick(int v){sampleTick=v;}
    public boolean isLastOnGround(){return lastOnGround;}
    public void setLastOnGround(boolean v){lastOnGround=v;}
    public int getAirborneTicks(){return airborneTicks;} public void setAirborneTicks(int v){airborneTicks=v;}
    public int getSinceGroundTicks(){return sinceGroundTicks;} public void setSinceGroundTicks(int v){sinceGroundTicks=v;}
    public int getSpeedWindowTicks(){return speedWindowTicks;} public void setSpeedWindowTicks(int v){speedWindowTicks=v;}
    public int getSpeedOverspeedTicks(){return speedOverspeedTicks;} public void setSpeedOverspeedTicks(int v){speedOverspeedTicks=v;}
    public int getSevereSpeedTicks(){return severeSpeedTicks;} public void setSevereSpeedTicks(int v){severeSpeedTicks=v;}
    public double getSpeedWindowDistance(){return speedWindowDistance;} public void setSpeedWindowDistance(double v){speedWindowDistance=v;}
    public double getSpeedWindowAllowedDistance(){return speedWindowAllowedDistance;} public void setSpeedWindowAllowedDistance(double v){speedWindowAllowedDistance=v;}
    public int getHoverTicks(){return hoverTicks;} public void setHoverTicks(int v){hoverTicks=v;}
    public int getSuspiciousVerticalTicks(){return suspiciousVerticalTicks;} public void setSuspiciousVerticalTicks(int v){suspiciousVerticalTicks=v;}
    public int getImpossibleRiseTicks(){return impossibleRiseTicks;} public void setImpossibleRiseTicks(int v){impossibleRiseTicks=v;}
    public int getJesusCandidateTicks(){return jesusCandidateTicks;} public void setJesusCandidateTicks(int v){jesusCandidateTicks=v;}
    public int getJesusStableTicks(){return jesusStableTicks;} public void setJesusStableTicks(int v){jesusStableTicks=v;}
    public long getLastVelocityMs(){return lastVelocityMs;} public void setLastVelocityMs(long v){lastVelocityMs=v;}
    public long getLastDamageMs(){return lastDamageMs;} public void setLastDamageMs(long v){lastDamageMs=v;}
    public double getLastDy(){return lastDy;} public void setLastDy(double v){lastDy=v;}
}
