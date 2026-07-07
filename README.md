# PlayerDropChance

A **server-side-only** Fabric mod for Minecraft **26.1.2**. On player death,
every item stack — main inventory, armor slots, and offhand — is rolled
independently. Each stack has a configurable chance (default **5%**) to drop
on the ground like vanilla death normally does. Everything that does *not*
drop is safely held and returned to the player the moment they respawn.

Experience, death messages, and all other vanilla mechanics are untouched.
Clients do **not** need this mod installed — it only changes server logic.

## How it works

- `PlayerDeathMixin` intercepts `Player#dropAllDeathLoot` right before
  vanilla would drop everything. It rolls each non-empty slot against
  `dropChance`, drops the "unlucky" stacks immediately (identical to a
  normal vanilla drop), and hands the rest to `DropStorage`.
- `DropStorage` keeps the kept stacks both in memory and in a small NBT
  file per pending player (under `config/playerdropchance_pending/`), so
  they are not lost even if the server restarts while the player is
  disconnected between dying and respawning.
- On respawn (`ServerPlayerEvents.AFTER_RESPAWN`), the kept stacks are added
  back to the player's inventory. If the inventory is full, any leftover is
  dropped at the player's feet instead of being discarded — items are never
  silently deleted.
- A `ServerPlayConnectionEvents.JOIN` safety net double-checks for any
  still-pending items on login, in case a respawn event was ever missed.

## Configuration

`config/playerdropchance.json` (created automatically on first run):

```json
{
  "dropChance": 0.05
}
```

`dropChance` is a value from `0.0` to `1.0` — the probability that any
single item stack drops on death. Values outside that range are clamped
and a warning is logged. Edit the file and restart the server (or your
config-reload command, if you add one) to apply changes.

## ⚠️ Important version note: Java 25, not Java 21

Minecraft 26.1.x is Mojang's first **unobfuscated** release, and Fabric's
own porting guide states it **requires Java 25 at minimum** for the Gradle
JVM and as the mod's compile target — this is a hard requirement of the
toolchain itself, not a preference of this project. `gradle.properties` /
`build.gradle` are set up for Java 25 accordingly. If you specifically need
a Java 21 runtime, you would need to target an older Minecraft version
(1.21.x) with the classic obfuscated/Yarn toolchain instead — that is a
different, larger set of changes than adjusting a single version number.

## Building

Requirements: **JDK 25**, internet access (Gradle needs to download
Minecraft, Fabric Loader, and Fabric API).

```bash
gradle wrapper --gradle-version 9.4   # one-time: generates ./gradlew for this repo
./gradlew build
```

The built jar will be at `build/libs/playerdropchance-1.0.0.jar`.

> **Note:** this repository does not ship a pre-built `gradle-wrapper.jar`
> binary. The command above generates it locally using a system-installed
> Gradle. The included GitHub Actions workflow avoids this by installing
> Gradle itself in CI and running `gradle build` directly — it will build
> the jar automatically on every push and upload it as a workflow artifact.

## Installation

Drop the built jar into your **dedicated server's** `mods/` folder, along
with:
- [Fabric Loader](https://fabricmc.net/use/) `0.19.3`
- [Fabric API](https://modrinth.com/mod/fabric-api) `0.154.0+26.1.2`

No client-side installation is required.

## Project layout

```
PlayerDropChance/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── .github/workflows/build.yml
├── src/main/java/com/example/playerdropchance/
│   ├── PlayerDropChance.java      (mod initializer, respawn restoration)
│   ├── ModConfig.java             (config/playerdropchance.json handling)
│   ├── DropStorage.java           (pending-item persistence)
│   └── mixin/PlayerDeathMixin.java (death drop interception)
└── src/main/resources/
    ├── fabric.mod.json
    └── playerdropchance.mixins.json
```
