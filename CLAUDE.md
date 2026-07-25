# More Darkness

Minecraft 1.21.11 mod (Fabric + NeoForge). Enhances darkness: moon phases affect night light, caves lose their ambient floor.

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

- **common/** — Config (GSON POJO), darkness logic (lightmap uniform manipulation), shared mixin (MixinLightTexture)
- **fabric/** — Entry point (ClientModInitializer), YACL config screen, ModMenu integration
- **neoforge/** — Entry point (@Mod), YACL config screen, IConfigScreenFactory

ConfigScreenBuilder is duplicated in each loader module (same code, different YACL artifact, and the Fabric copy gets remapped to intermediary while the NeoForge one does not).

## Key classes

- `DarknessCalculator` — Shapes the uniforms of the GPU lightmap shader `core/lightmap.fsh`: scales `SkyFactor` with a moon-phase curve at night, scales `AmbientLightFactor` by the configured cave darkness. Moon phase read via `EnvironmentAttributes.MOON_PHASE` from the camera's attribute probe. Gamma is left untouched: the shader's `notGamma(0) == 0`, so fully dark cells stay black at any gamma setting.
- `MixinLightTexture` — Two `@Redirect`s on the `Std140Builder.putFloat` calls in `LightTexture.updateLightTexture`, ordinal 0 (`AmbientLightFactor`) and ordinal 1 (`SkyFactor`), matching the std140 block declaration order. Redirecting the UBO writes rather than the values they come from keeps vanilla's end flash and boss overlay adjustments upstream of the darkening. The sky handler captures the enclosing `partialTicks` argument.
- `MoreDarknessConfig` — Plain POJO, GSON serialized to `config/more_darkness.json`.

## Lightmap history (why the code looks like this)

- ≤ 1.21.1: CPU 16x16 NativeImage manipulated pixel by pixel (old approach, branch 1.21.1).
- 1.21.2+: lightmap computed on GPU by `core/lightmap.fsh`; no CPU pixels anymore. `LightTexture` survives as the CPU side, uploading a std140 UBO once per dirty frame (this branch).
- 1.21.9+: sky light colour/factor and moon phase become data-driven `EnvironmentAttributes` read from the camera's probe; moon phase no longer affects the vanilla lightmap.
- 26.1+: `LightTexture` removed, split into `LightmapRenderState` (public mutable fields) + `LightmapRenderStateExtractor` (CPU extract) + `Lightmap` (GPU pass). Branches 26.1.2 and 26.2.

## Darkness ceiling on 1.21.x

The 1.21.x lightmap shader ends each pass with `color = mix(color, vec3(0.75), 0.04)`, applied once before the gamma bypass and once after. That is a hardcoded ~3% grey floor no mod can reach past through the UBO, and 26.1 dropped it. Consequences on this branch:

- Overworld caves are already at the floor in vanilla (`ambient_light` is 0 there), so `caveDarkness` changes nothing for them. It only strips the Nether (0.1) and End (0.25) ambient lift.
- Moon-phase night darkening is unaffected: it rides on `SkyFactor`, well above the floor.
- Truly pitch black caves would need the core shader replaced, which is a different mechanism entirely.

## Compatibility

### Polytone (lightmap mods)

Polytone's custom-lightmap feature is only disabled from 26.1 onwards, where `LightTexture` no longer exists. Whether it is active on 1.21.11, and whether it would override our UBO writes, has not been checked on this branch.

### Shaders

Iris and shader packs replace the whole pipeline, so the mod has no effect under them. The `disableWithShaders` config option exists but is consumed nowhere (dead option, kept for config compat).

## Reference sources

`.sources/` contains cloned repos (gitignored): grondag/darkness, True-Darkness-Refabricated, IMB11/Fog, MultiLoader-Template, distant-horizons-api-example. All are ≤1.21.1-era; for 26.x patterns see Darkness Engine (github.com/ndellagrotte/darkness-engine).

## Dependencies

- YACL 3.8.1+1.21.11 (fabric/neoforge artifacts; 3.8.2 exists on Modrinth only)
- ModMenu 17.0.0 (fabric, optional)
- Fabric API 0.141.5+1.21.11 / NeoForge 21.11.44
- Gradle 9.5.1, Loom 1.17.17, ModDevGradle 2.0.142, NeoForm 1.21.11-20251209.172050, Parchment 1.21.11:2025.12.20
- Java 21 toolchain, Gradle daemon on Java 25
