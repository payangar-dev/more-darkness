# More Darkness

Minecraft 1.21.11 mod (Fabric + NeoForge). Enhances darkness: moon phases affect night light, caves are pitch black.

## Build

```
./gradlew :common:build        # Shared code
./gradlew :fabric:build        # Fabric jar
./gradlew :neoforge:build      # NeoForge jar
./gradlew :fabric:runClient    # Test Fabric
./gradlew :neoforge:runClient  # Test NeoForge
```

## Architecture

MultiLoader-Template (jaredlll08) — common/fabric/neoforge subprojects, Java ServiceLoader for platform abstraction. 1.21.x still ships obfuscated: code is written against Mojang mappings + Parchment, Loom rewrites the mixin annotations to intermediary when it remaps the Fabric jar (`Fabric-Loom-Mixin-Remap-Type: static`, no refmap), NeoForge stays on Mojang names. Java 21.

Use the classic `fabric-loom` plugin id, not `net.fabricmc.fabric-loom`: the latter forces Loom's unobfuscated mode and rejects any `mappings` block ("Cannot configure layered mappings in a non-obfuscated environment").

- **common/** — Config (GSON POJO), darkness logic (lightmap uniform manipulation), shared mixin (MixinLightTexture), and the replacement core shader shipped as mod resources
- **fabric/** — Entry point (ClientModInitializer), YACL config screen, ModMenu integration
- **neoforge/** — Entry point (@Mod), YACL config screen, IConfigScreenFactory

ConfigScreenBuilder is duplicated in each loader module (same code, different YACL artifact, and the Fabric copy gets remapped to intermediary while the NeoForge one does not).

## Key classes

- `DarknessCalculator` — Shapes the uniforms of the GPU lightmap shader `core/lightmap.fsh`: scales `SkyFactor` with a moon-phase curve at night, scales `AmbientLightFactor` and `AmbientFloorFactor` by the configured cave darkness. Moon phase read via `EnvironmentAttributes.MOON_PHASE` from the camera's attribute probe. Gamma is left untouched: `notGamma(0) == 0`, so fully dark cells stay black at any gamma setting.
- `MixinLightTexture` — Three `@Redirect`s on the `Std140Builder.putFloat` calls in `LightTexture.updateLightTexture`, at ordinals matching the std140 block declaration order: 0 (`AmbientLightFactor`), 1 (`SkyFactor`), and 6 (`BrightnessFactor`, passed through unchanged but followed by the extra `AmbientFloorFactor` write). Redirecting the UBO writes rather than the values they come from keeps vanilla's end flash and boss overlay adjustments upstream of the darkening. The sky handler captures the enclosing `partialTicks` argument.
- `assets/minecraft/shaders/core/lightmap.fsh` — Replacement core shader, see below.
- `MoreDarknessConfig` — Plain POJO, GSON serialized to `config/more_darkness.json`.

## Lightmap history (why the code looks like this)

- ≤ 1.21.1: CPU 16x16 NativeImage manipulated pixel by pixel (old approach, branch 1.21.1).
- 1.21.2+: lightmap computed on GPU by `core/lightmap.fsh`; no CPU pixels anymore. `LightTexture` survives as the CPU side, uploading a std140 UBO once per dirty frame (this branch).
- 1.21.9+: sky light colour/factor and moon phase become data-driven `EnvironmentAttributes` read from the camera's probe; moon phase no longer affects the vanilla lightmap.
- 26.1+: `LightTexture` removed, split into `LightmapRenderState` (public mutable fields) + `LightmapRenderStateExtractor` (CPU extract) + `Lightmap` (GPU pass). Branches 26.1.2 and 26.2.

## Why this branch ships a core shader

The stock 1.21.x lightmap shader mixes in 4% grey twice, `color = mix(color, vec3(0.75), 0.04)`, once after the sky light term and once after the gamma bypass. That holds unlit cells at roughly 0.06 and is exactly what stops Overworld caves from going dark. It cannot be reached through the UBO, only through the shader, and 26.1 dropped it (which is why the 26.x branches need no shader). Without this, `caveDarkness` would only strip the Nether and End ambient lift and the mod would do nothing for Overworld caves.

So `common/` ships `assets/minecraft/shaders/core/lightmap.fsh`, a copy of vanilla's with three changes, each marked in the file:

1. An eighth float, `AmbientFloorFactor`, added to the UBO block. It costs nothing: the seven vanilla floats reach 28 bytes and the following `vec3` aligns to 32, so the extra float sits in padding that already existed and the block stays 64 bytes. Vanilla's own writes keep their offsets.
2. Both grey mixes are scaled by it. `1.0` reproduces vanilla exactly, which is what the mod sends whenever it is disabled or the dimension is excluded.
3. `notGamma` returns black for black instead of dividing by zero, now that a fully black cell is reachable.

Verified by temporarily putting `#error` in the file: the game reports `Couldn't compile fragment shader (minecraft:core/lightmap)` with our message, which proves the mod's resource pack really does win over the vanilla asset on 1.21.11.

Java and shader are independent: if another pack wins the override, the extra float is never read and the mod degrades to UBO-only darkening; the mixin never writes out of bounds either way.

## Compatibility

### Polytone (lightmap mods)

Polytone's custom-lightmap feature is only disabled from 26.1 onwards, where `LightTexture` no longer exists. Whether it is active on 1.21.11, and whether it would override our UBO writes, has not been checked on this branch.

### Shaders and other lightmap packs

Iris and shader packs replace the whole pipeline, so the mod has no effect under them. The `disableWithShaders` config option exists but is consumed nowhere (dead option, kept for config compat).

Anything else replacing `core/lightmap.fsh` (a resource pack, another darkness mod) conflicts by construction: whichever the resource manager resolves last wins, and if theirs wins the mod loses the floor removal. This is inherent to the approach, not a bug to fix.

## Reference sources

`.sources/` contains cloned repos (gitignored): grondag/darkness, True-Darkness-Refabricated, IMB11/Fog, MultiLoader-Template, distant-horizons-api-example. All are ≤1.21.1-era; for 26.x patterns see Darkness Engine (github.com/ndellagrotte/darkness-engine).

## Dependencies

- YACL 3.8.1+1.21.11 (fabric/neoforge artifacts; 3.8.2 exists on Modrinth only)
- ModMenu 17.0.0 (fabric, optional)
- Fabric API 0.141.5+1.21.11 / NeoForge 21.11.44
- Gradle 9.5.1, Loom 1.17.17, ModDevGradle 2.0.142, NeoForm 1.21.11-20251209.172050, Parchment 1.21.11:2025.12.20
- Java 21 toolchain, Gradle daemon on Java 25
