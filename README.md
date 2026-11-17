# EssGuard 1.21.4

Server-side anti-cheat for **Paper 1.21.4 / Java 21**.

## Current implementation
- Movement heuristics: Speed, Fly, Jesus.
- Combat observation: conservative reach-distance check for direct player hits and player-fired projectiles.
- Violation levels, decay, staff alerts, `/essguard reload`, `/essguard info <player>`.
- Alert-only: no automatic kicks or bans.

## Build
```bash
mvn clean package
```
Output: `target/EssGuard-1.21.4.jar`.

## Important
This is an early development build, not a production-certified anti-cheat. Movement and reach checks are heuristic and require live testing with normal players, different ping, effects, terrain, vehicles, and server load. Keep thresholds conservative and do not use alerts as proof for punishment.
