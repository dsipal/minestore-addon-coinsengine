# MineStore-CoinsEngine Addon

Addon for [MineStoreRecode](https://github.com/ChromMob/MineStoreRecode) that uses a [CoinsEngine](https://nightexpressdev.com/coinsengine/) currency for all virtual currency transactions. No changes to the base MineStore plugin are required.

## Repository layout

This repo is **addon-only**: no fork of MineStore, no rebasing—only the files needed to build the addon.

```
minestore-addon-coinsengine/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
├── src/main/java/me/chrommob/minestore/coinsengine/
│   ├── CoinsEngineAddon.java
│   └── provider/CoinsEngineEconomyProvider.java
├── src/main/resources/addon.yml
├── .gitignore
└── README.md
```

## Build

### From this repo (addon-only)

From the **repository root** of `minestore-addon-coinsengine`:

```bash
./gradlew shadowJar
```

Output: `build/libs/MineStore-CoinsEngine-1.0.0.jar`.

When `../api` exists (for example in the MineStore fork), this addon compiles against that local API.
When `../api` does not exist (standalone repo), the build dynamically fetches [MineStoreRecode](https://github.com/ChromMob/MineStoreRecode) from GitHub and builds only the `MineStore-API` module.

Optional version pinning for standalone mode:

- Branch (default): `./gradlew shadowJar -PminestoreRef=master`
- Commit SHA: `./gradlew shadowJar -PminestoreRef=587bb5b10c7c1ae931d954b441f6f9f80a835055`

### While in the MineStore fork

If this addon is included as a subproject in the full fork, from the **repository root** of the fork:

```bash
./gradlew :MineStore-CoinsEngine-Standalone:shadowJar
```

Or use the original addon module (same addon, different source folder):

```bash
./gradlew :MineStore-CoinsEngine:shadowJar
```

When `../api` exists (you’re in the fork), the build uses local MineStore projects.

### After migrating to an addon-only repo

1. Create a new repo and copy the **contents** of this repo (`minestore-addon-coinsengine`) to the new repo **root** (so `build.gradle`, `src/`, etc. are at the root).
2. Add a Gradle wrapper: run `gradle wrapper` (or copy `gradlew` and `gradle/wrapper/` from another project).
3. Build: `./gradlew shadowJar`

The build detects there is no `api` project and automatically fetches MineStoreRecode from GitHub, then compiles against the generated API jar.

## Configuration (server)

After the first run, edit the addon config (e.g. `plugins/MineStore/addons/MineStore-CoinsEngine/config`):

```yaml
# CoinsEngine currency ID. Must match a currency in CoinsEngine. Default: points
currency_id: points

# Addon-only workaround for MineStore's stale DB balance sync bug.
# Mirrors live CoinsEngine balances into MineStore's playerdata table.
balance_mirror_enabled: true
balance_mirror_interval_seconds: 15
```

## Migrating from the full fork to addon-only

To stop maintaining the full MineStore fork and keep only this addon:

1. **Create a new Git repo** (or an orphan branch).
2. Copy the **contents** of this repo to the **root** of that repo (so the new repo has `build.gradle`, `settings.gradle`, `src/`, etc. at the top level).
3. Add a Gradle wrapper: `gradle wrapper`.
4. Commit and push. The new repo has no upstream MineStore to rebase; the addon compiles against MineStore from JitPack or mavenLocal.

You end up with a small, single-purpose repo that only contains the CoinsEngine addon.

## Server installation

Copy the built jar from:

- `build/libs/MineStore-CoinsEngine-1.0.0.jar`

to your server:

- `plugins/MineStore/addons/`

Then restart the server (or fully reload MineStore addons if your environment supports it).
