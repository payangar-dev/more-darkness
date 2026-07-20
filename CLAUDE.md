# More Darkness

Minecraft 26.2 mod (Fabric + NeoForge). Enhances darkness: moon phases affect night light, caves are pitch black.

## Build

```
./gradlew :common:build        # Shared code
./gradlew :fabric:build        # Fabric jar
./gradlew :neoforge:build      # NeoForge jar
./gradlew :fabric:runClient    # Test Fabric
./gradlew :neoforge:runClient  # Test NeoForge
```

## Architecture

MultiLoader-Template (jaredlll08) — common/fabric/neoforge subprojects, Java ServiceLoader for platform abstraction. Since 26.1 the game ships unobfuscated (no mappings, no Parchment for 26.x), Java 25.

- **common/** — Config (GSON POJO), darkness logic (lightmap render state manipulation), shared mixin (MixinLightmapRenderStateExtractor)
- **fabric/** — Entry point (ClientModInitializer), YACL config screen, ModMenu integration
- **neoforge/** — Entry point (@Mod), YACL config screen, IConfigScreenFactory

ConfigScreenBuilder is duplicated in each loader module (same code, different YACL artifact). Historical reason was intermediary vs Mojang mappings; since 26.x Fabric also runs on Mojang names, so unifying it into common may now be possible (verify YACL artifacts first).

## Key classes

- `DarknessCalculator` — Mutates `LightmapRenderState` (the CPU-side inputs of the GPU lightmap shader `core/lightmap.fsh`): scales `skyFactor` with a moon-phase curve at night, replaces `ambientColor` with the configured cave ambient. Moon phase read via `EnvironmentAttributes.MOON_PHASE` from the camera's attribute probe. Gamma is left untouched: the shader's `notGamma(0) == 0`, so fully dark cells stay black at any gamma setting.
- `MixinLightmapRenderStateExtractor` — Single `@At("TAIL")` inject on `LightmapRenderStateExtractor.extract`; the early returns (not dirty, no level/player) skip it. Same hook as True Darkness by Tia and Darkness Engine on 26.x.
- `MoreDarknessConfig` — Plain POJO, GSON serialized to `config/more_darkness.json`.

## Lightmap history (why the code looks like this)

- ≤ 1.21.1: CPU 16x16 NativeImage manipulated pixel by pixel (old approach, branch 1.21.1).
- 1.21.2+: lightmap computed on GPU by `core/lightmap.fsh`; no CPU pixels anymore.
- 26.1+: `LightTexture` removed, split into `LightmapRenderState` (public mutable fields) + `LightmapRenderStateExtractor` (CPU extract) + `Lightmap` (GPU pass, std140 UBO). Sky/ambient/block light colors are data-driven via `EnvironmentAttributes` and timeline datapacks; moon phase no longer affects vanilla lightmap.

## Compatibility

### Polytone (lightmap mods)

Polytone 26.2 exists but its custom-lightmap feature is disabled on 26.x (`LightmapsManager.maybeModifyLightTexture` returns early, "LightTexture was removed in 26.1"). Nothing cancels lightmap computation anymore. Our TAIL state mutation runs upstream of the GPU pass and composes with whatever Polytone reintroduces later, unless they bypass vanilla state entirely — re-check when they reimplement (their TODO says "add back as shader").

### Shaders

Core-shader replacement via resource packs is unsupported in 26.2 (packs doing it broke). The 26.x darkness mods advertise Sodium compat with this same render-state hook. Iris/shader packs replace the whole pipeline; `disableWithShaders` config option exists but is currently consumed nowhere (dead option, kept for config compat).

## Reference sources

`.sources/` contains cloned repos (gitignored): grondag/darkness, True-Darkness-Refabricated, IMB11/Fog, MultiLoader-Template, distant-horizons-api-example. All are ≤1.21.1-era; for 26.x patterns see Darkness Engine (github.com/ndellagrotte/darkness-engine).

## Dependencies

- YACL 3.9.6+26.2 (fabric/neoforge artifacts)
- ModMenu 20.0.1 (fabric, optional)
- Fabric API 0.155.2+26.2 / NeoForge 26.2.0.25-beta (beta only for now)
- Gradle 9.5.1, Loom 1.17, ModDevGradle 2.0.142, NeoForm 26.2-2, Java 25
