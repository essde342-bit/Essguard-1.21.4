# EssGuard 1.21.4

A server-side anti-cheat plugin for Paper 1.21.4 (Java 21).

## Build

```bash
mvn clean package
```

The plugin JAR is written to `target/EssGuard-1.21.4.jar`.

## Status

Development build. Checks are conservative and alert-only by default. Do not enable automatic punishments until thresholds have been validated on your server.

## Checks

- Movement: Speed, Fly, Jesus, NoFall (incremental implementation)
- Combat: reach / hitbox observation
- Violation levels with decay and staff alerts

## Requirements

- Paper 1.21.4
- Java 21
