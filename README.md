# EssGuard 1.21.4

Server-side anti-cheat for **Paper 1.21.4 / Java 21**.

## Current implementation

- Movement heuristics: **Speed**, **Fly**, and **Jesus**.
- Movement sampling accounts for movement attributes, gravity/jump attributes, variable sample gaps, ping, server tick delays, external velocity and damage grace periods.
- Loaded-chunk-only block queries for movement collision and environment checks; unknown/unloaded terrain is not treated as cheat evidence.
- Conservative collision-shape support checks and exemptions for common movement-altering terrain, effects, vehicles, and custom entity scale.
- Combat observation: conservative **melee Reach** estimate for direct player-vs-player hits. Projectile hits are deliberately excluded because projectile travel distance is not melee reach evidence.
- Violation levels, decay, staff alerts, /essguard reload, and /essguard info <player>.

## Build

Run: mvn clean package

Output: target/EssGuard-1.21.4.jar.

## Important

This is an early development build, not a production-certified anti-cheat. The checks are heuristic, not a packet-level movement simulator, and require live testing with normal players, different ping, effects, terrain, vehicles, knockback, and server load. Alerts are not proof for punishment. This build does not automatically kick or ban players.
