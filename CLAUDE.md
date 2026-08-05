# More Darkness

Minecraft 26.1.2 mod (Fabric + NeoForge). Enhances darkness: moon phases affect night light, caves are pitch black, and since 2.0.0 the mod simulates the human eye (adaptation, scotopic vision, glare) and murky water.

## Release

Tag `v<version>+<mc-version>` on the branch to release. Every release ships a hand-written `changelogs/<version>.md` (concise, user-facing, written by Claude): the versioned pre-push hook (`git config core.hooksPath .githooks`, once per clone) and the CI both refuse a release tag without it. Push release tags one per `git push` (GitHub triggers nothing beyond 3 tags in one push).

Before any release, playtest `:fabric:runClient` AND `:neoforge:runClient`: NeoForge patches vanilla rendering classes (the fluid renderer notably), so a mixin can crash there while Fabric is fine (the 2.0.0 NeoForge launch crash shipped because only Fabric was ever launched). Check the NeoForge-patched sources in the NeoFormRuntime cache (`sourcesWithNeoForge_*` / `patch_*` artifacts) when targeting vanilla rendering internals.

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

- `DarknessCalculator` — Mutates `LightmapRenderState` (the CPU-side inputs of the GPU lightmap shader `core/lightmap.fsh`): scales `skyFactor` with a moon-phase curve at night, widens the torch falloff, applies the eye-adaptation gain and floor, replaces `ambientColor`. Moon phase read via `EnvironmentAttributes.MOON_PHASE` from the camera's attribute probe. Gamma is left untouched: the shader's `notGamma(0) == 0`, so fully dark cells stay black at any gamma setting.
- `MixinLightmapRenderStateExtractor` — Single `@At("TAIL")` inject on `LightmapRenderStateExtractor.extract`; the early returns (not dirty, no level/player) skip it. Same hook as True Darkness by Tia and Darkness Engine on 26.x. On 26.1 the camera accessor is `GameRenderer.getMainCamera()`; 26.2 renamed it to `mainCamera()`.
- `MoreDarknessConfig` — Plain POJO, GSON serialized to `config/more_darkness.json`. `eyeAdaptation` gates the whole perception system (keeps the torch falloff), `darkerWater` gates turbidity (keeps smooth fluid lighting). Tuned values are hardcoded by design: no sliders.

### Perception system (2.0.0)

- `EyeState` — Adaptation state in log2 EV: 3s blind onset, 12s dark / 1.2s light tau, squared rod-cone curve, clamped at fully-adapted. Driven by the average luminance of the rendered frame (`ScreenMetering` reads back a 1x1 target from the `metering` post chain). The far veil (`darkSightCrushFloor`) follows measured darkness, not adaptation.
- Post chains (`assets/more_darkness/post_effect/`): `scotopic` (mesopic desaturation gated by rod engagement), `glare` (Spencer-style dazzle, dual-filter bloom pyramid), `dark_sight` (spherical radius crush, needs depth: attached inside LevelRenderer's frame graph via `MixinLevelRendererDarkSight`, the only spot where world depth is alive), `metering`. Per-frame uniforms go through `DynamicUniforms` (swaps the baked `PostPass.customUniforms` buffers).
- Water: `MixinFluidRendererSmoothLight` (per-corner light + turbidity layers via `@WrapOperation`, yields the surface to Big Water, inert under Sodium), `MixinWaterFogEnvironment` (biome-graded underwater fog), `WaterTurbidity` (biome key matching, spatial + temporal blending).

## Lightmap history (why the code looks like this)

- ≤ 1.21.1: CPU 16x16 NativeImage manipulated pixel by pixel (old approach, branch 1.21.1).
- 1.21.2+: lightmap computed on GPU by `core/lightmap.fsh`; no CPU pixels anymore.
- 26.1+: `LightTexture` removed, split into `LightmapRenderState` (public mutable fields) + `LightmapRenderStateExtractor` (CPU extract) + `Lightmap` (GPU pass, std140 UBO). Sky/ambient/block light colors are data-driven via `EnvironmentAttributes` and timeline datapacks; moon phase no longer affects vanilla lightmap.

## Compatibility

### NeoForge patches (learned from the 2.0.0 crash)

NeoForge patches an alpha parameter into the fluid renderer's vertex calls on 1.21.x (their fluid transparency extensions); the 1.21.x branches carry one `@WrapOperation` per overload with `require = 0` so exactly one matches per loader and future shape changes degrade silently instead of crashing. The 26.x `FluidRenderer` path is unpatched (verified). `:neoforge:runClient` needs the Kotlin for Forge maven (YACL transitive).

### Polytone (lightmap mods)

Polytone exists on 26.x but its custom-lightmap feature is disabled there (`LightmapsManager.maybeModifyLightTexture` returns early, "LightTexture was removed in 26.1"). Nothing cancels lightmap computation anymore. Our TAIL state mutation runs upstream of the GPU pass and composes with whatever Polytone reintroduces later, unless they bypass vanilla state entirely — re-check when they reimplement (their TODO says "add back as shader").

### Shaders

Core-shader replacement via resource packs is unsupported in 26.x (packs doing it broke). The 26.x darkness mods advertise Sodium compat with this same render-state hook. Iris/shader packs replace the whole pipeline; `disableWithShaders` config option exists but is currently consumed nowhere (dead option, kept for config compat).

## Reference sources

`.sources/` contains cloned repos (gitignored): grondag/darkness, True-Darkness-Refabricated, IMB11/Fog, MultiLoader-Template, distant-horizons-api-example. All are ≤1.21.1-era; for 26.x patterns see Darkness Engine (github.com/ndellagrotte/darkness-engine).

## Dependencies

- YACL 3.9.6+26.1 (fabric/neoforge artifacts)
- ModMenu 18.0.0 (fabric, optional)
- Fabric API 0.155.2+26.1.2 / NeoForge 26.1.2.86
- Gradle 9.5.1, Loom 1.17, ModDevGradle 2.0.142, NeoForm 26.1.2-1, Java 25
