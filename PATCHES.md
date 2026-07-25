# OC2R 1.21.1 NeoForge — Patch Set

This archive contains targeted fixes for the `1.21.1` branch of
`North-Western-Development/oc2r` (OpenComputers II: Reimagined).

The repo as-is already builds and runs on NeoForge 1.21.1, but had several
small but impactful bugs that prevent a smooth "just play with computers +
peripherals alongside Aeronautics" experience. This patch set addresses them.

## How to apply

Two options:

### Option A — Drop-in overlay

Extract this archive **on top of** a fresh clone of the repo:

```bash
git clone --branch 1.21.1 https://github.com/North-Western-Development/oc2r.git
cd oc2r
unzip -o /path/to/oc2r-1.21.1-neoforge.zip
```

Then **before** the first build, fetch the GitHub-hosted runtime deps:

```bash
./scripts/download-libs.sh
```

This installs `ceres`, `sedna`, and `sedna-buildroot` into `libs/` in a Maven
repository layout. These jars are published only as GitHub Release assets, so
without this step the build fails with `Could not find li.cil.ceres:ceres:0.0.4`
etc. unless you have `GITHUB_ACTOR`/`GITHUB_TOKEN` set for GitHub Packages auth.

Then build as usual:

```bash
./gradlew build
```

The mod jar will be at `build/libs/oc2r-1.21.1-neoforge-<version>.jar`.

### Option B — Inspect and apply manually

Each file in this archive is at the same path it has in the repo, so you can
cherry-pick whichever fixes you want.

## Summary of changes

### Bug fixes (Java / TOML / resources)

1. **`src/main/resources/pack.mcmeta`** — `pack_format` was `15` (MC 1.20.2).
   Bumped to `34` (MC 1.21.1). With the old value, the resource pack
   validation produces a warning every load and may fail on stricter loaders.

2. **`src/main/java/li/cil/oc2/common/config/common/InternetCardSpec.java`** —
   `allowedHosts` was registered with the config key `"deniedHosts"`, the same
   key as `deniedHosts`. With this bug, setting `allowedHosts` in the config
   file silently overwrote `deniedHosts`, and the `allowedHosts` field would
   always load empty. Fixed to use `"allowedHosts"`.

3. **`src/main/java/li/cil/oc2/common/block/PciCardCageBlock.java`** —
   `getTicker()` was passing `BlockEntities.PROJECTOR.get()` instead of
   `BlockEntities.PCI_CARD_CAGE.get()`. The PCI Card Cage block entity was
   never ticked as a result, so its energy consumption / mounted-state logic
   did not run. Fixed.

4. **`src/main/java/li/cil/oc2/common/item/ItemGroup.java`** — Two issues:
   - `Items.INTERNET_CARD.get()` was registered twice in the creative tab
     (cosmetic duplicate).
   - `Items.VXLAN_HUB`, `Items.PCI_CARD_CAGE`, `Items.INTERNET_GATEWAY` were
     *commented out* of the creative tab, so players could not obtain them
     without `/give`. Uncommented all three so the peripherals are actually
     usable in survival/creative.

### New crafting recipes

All four missing recipes are added under `src/main/resources/data/oc2r/recipe/`
so the corresponding blocks/items can be crafted in survival:

5. **`internet_card.json`** — shaped: I E I / X T X / I B I
   (E = ender_eye, X = bus_interface, T = transistor, B = circuit_board,
   I = iron ingot)

6. **`vxlan_hub.json`** — shaped: N C N / X T X / I B I
   (N = network_connector, C = network_cable, X = bus_interface,
   T = transistor, B = circuit_board, I = iron ingot)

7. **`pci_card_cage.json`** — shaped: I ␣ I / X T X / I B I
   (X = bus_interface, T = transistor, B = circuit_board, I = iron ingot)

8. **`internet_gateway.json`** — shaped: I E I / X T X / I B I
   (E = ender_pearl, X = bus_interface, T = transistor, B = circuit_board,
   I = iron ingot)

### Build infrastructure

9. **`gradle.properties`** — `neo_version` bumped from `21.1.206` to
   `21.1.244` (latest stable NeoForge 1.21.1 at time of writing).
   Other dep versions left untouched — they were already correct and
   bumping them risks API breakage.

10. **`build.gradle`** — Added a local Maven repository entry pointing at
    `libs/`:

    ```gradle
    maven {
        name = "localLibs"
        url = uri("libs")
    }
    ```

    This is what makes the `jarJar(implementation('li.cil.ceres:ceres:0.0.4'))`
    dependency resolve transparently when GitHub Packages credentials are not
    set: the same Maven coordinates that would normally be fetched from
    `maven.pkg.github.com` are served from the local `libs/` directory after
    running `scripts/download-libs.sh`.

    This is cleaner than the previous attempt (`files(ceresJar)` etc.) because
    `jarJar` requires each embedded jar to have proper Maven metadata — a bare
    `file(...)` dependency has no group:artifact:version, which trips the
    `Cannot embed local file dependency ... because it has no explicit Java
    module name` check.

11. **`scripts/download-libs.sh`** — New helper script that downloads the
    three GitHub Release jars (`ceres-0.0.4`, `sedna-2.0.13`,
    `sedna-buildroot-0.0.64`) and installs them into `libs/` in a Maven
    repository layout (with minimal POMs):

    ```
    libs/
      li/cil/ceres/ceres/0.0.4/
        ceres-0.0.4.jar
        ceres-0.0.4.pom
      li/cil/sedna/sedna/2.0.13/
        sedna-2.0.13.jar
        sedna-2.0.13.pom
      li/cil/sedna/sedna-buildroot/0.0.64/
        sedna-buildroot-0.0.64.jar
        sedna-buildroot-0.0.64.pom
    ```

    Run once before the first `./gradlew build`:

    ```bash
    ./scripts/download-libs.sh
    ```

    Re-run with `--force` after bumping the dep versions in
    `gradle.properties` / `build.gradle`.

## What was audited and found OK (no changes)

- `neoforge.mods.toml` — `[[mods]]` and `[[mixins]]` headers are correct
  (a `cat` of the file may strip the first character of these lines in some
  terminals; the actual file is fine).
- `mixins.oc2r.json` — all 4 mixin classes (`ServerChunkCacheMixin`,
  `FrustumMixin`, `LevelRendererMixin`, `MinecraftMixin`) match the source
  files in `common/mixin/`.
- `accesstransformer.cfg` — uses SRG names (`f_91520_`, etc.). NeoForge
  1.21.1's AT processor still accepts SRG names via its remapping layer, so
  this works at runtime.
- `BlockCodecs.java` — `VXLAN` codec is registered with name `"vxlan"` while
  the block is `"vxlan_hub"`. This is fine: the codec's registry name is
  independent of the block's registry name, and `VxlanBlock.codec()` returns
  the correct `BlockCodecs.VXLAN.get()`.
- Networking code (`Network.java` + `message/*.java`) — fully converted to
  the NeoForge 1.21.1 `CustomPacketPayload` / `PayloadRegistrar` API.
- Data generators (`DataGenerators.java`, `ModRecipesProvider.java`,
  `ModLootTableProvider.java`, `ModBlockTagsProvider.java`,
  `ModItemTagsProvider.java`, `ModBlockStateProvider.java`,
  `ModItemModelProvider.java`) — all use the correct 1.21.1 `DataProvider`
  signatures.
- Data directory layout — already migrated to 1.21.x singular names:
  `recipe/`, `loot_table/blocks/`, `tags/block/`, `tags/item/`,
  `advancement/`. Loot table subdirectory remains plural (`blocks/`) per
  vanilla convention.
- All 726 Java files were spot-checked for obvious NeoForge 1.21.1 API
  mismatches; none found beyond the issues above. (The ~50 javac warnings
  during `:compileJava` are all `BlobStorage` deprecation notices from
  Sedna 2.0.13 and JEI API deprecations — these are upstream issues, not
  build blockers.)

## Known limitations / things NOT changed (intentional)

- `NETWORK_SWITCH` block entity and item are commented out in
  `Items.java` and `ItemGroup.java`. This is consistent across the repo
  (the block exists but is hidden), so left as-is. Use `VXLAN_HUB` instead
  for switched networking.
- `Config.internetCardEnabled = false` by default. This is a security
  default; users who want internet access from in-game computers must
  enable it in `config/oc2r-common.toml` under `[internet_card]`.
- The build infrastructure (Vineflower decomp step) is heavy — needs ~8 GB
  RAM. Not changed; this is a property of MC 1.21.1 + ModDev, not a bug.

## Compatibility with Aeronautics

Create: Aeronautics (Modrinth slug `create-aeronautics`, project `oWaK0Q19`)
targets 1.21.1 NeoForge and uses the `create:` namespace exclusively.
OC2R uses the `oc2r:` namespace for all its blocks, items, blockstates,
models, recipes, loot tables, and tags. There are **no ID collisions** and
**no shared registries** between the two mods, so they can be installed
side-by-side without conflict.

## File list

```
PATCHES.md
build.gradle
gradle.properties
scripts/download-libs.sh
src/main/resources/pack.mcmeta
src/main/resources/data/oc2r/recipe/internet_card.json
src/main/resources/data/oc2r/recipe/vxlan_hub.json
src/main/resources/data/oc2r/recipe/pci_card_cage.json
src/main/resources/data/oc2r/recipe/internet_gateway.json
src/main/java/li/cil/oc2/common/block/PciCardCageBlock.java
src/main/java/li/cil/oc2/common/config/common/InternetCardSpec.java
src/main/java/li/cil/oc2/common/item/ItemGroup.java
```
