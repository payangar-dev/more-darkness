# More Darkness

Minecraft 1.21.1 mod (Fabric + NeoForge). Enhances darkness: moon phases affect night light, caves are pitch black, and since 2.0.0 the mod simulates the human eye (adaptation, scotopic vision, glare) and murky water.

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

MultiLoader-Template (jaredlll08) — common/fabric/neoforge subprojects, Java ServiceLoader for platform abstraction, Mojang+Parchment mappings.

- **common/** — Config (GSON POJO), darkness logic (lightmap manipulation), mixins shared by both loaders (MixinLightTexture, MixinGameRendererPerception, MixinLiquidBlockRendererSmoothLight, MixinFogRendererWater)
- **fabric/** — Entry point (ClientModInitializer), YACL config screen, ModMenu integration
- **neoforge/** — Entry point (@Mod), YACL config screen, IConfigScreenFactory

**YACL cannot go in common** — fabric artifact uses intermediary mappings, common uses Mojang. ConfigScreenBuilder is duplicated in each loader module (same code, different artifact).

## Key classes

- `DarknessCalculator` — Computes darkened lightmap (16x16 pixels). Moon phase via `world.getMoonBrightness()`, quadratic curve, configurable minimum brightness (default 0.05 = starlight). Since 2.0.0 it also widens the torch falloff and applies the eye-adaptation gain to the pixels before upload.
- `MoreDarknessConfig` — Plain POJO, GSON serialized to `config/more_darkness.json`. `eyeAdaptation` gates the whole perception system (keeps the torch falloff), `darkerWater` gates turbidity (keeps smooth fluid lighting). Tuned values are hardcoded by design: no sliders.

### Perception system (2.0.0)

- `EyeState` — Adaptation state in log2 EV: 3s blind onset, 12s dark / 1.2s light tau, squared rod-cone curve, clamped at fully-adapted. Driven by the average luminance of the rendered frame, metered inside the post pipeline: the scotopic chain ends in a downsampling pyramid over the pre-glare image, so the veil cannot feed itself. The far veil (`darkSightCrushFloor`) follows measured darkness, not adaptation.
- Post chains (pre-1.21.2 format: `assets/more_darkness/shaders/post/` + programs under `assets/minecraft/shaders/program/more_darkness_*`): `scotopic` (mesopic desaturation + metering pyramid), `glare` (Spencer-style dazzle, dual-filter bloom pyramid), `dark_sight` (spherical radius crush). Orchestrated by `PerceptionEffects`, hooked via `MixinGameRendererPerception`.
- Water: `MixinLiquidBlockRendererSmoothLight` + `FluidSmoothLight` (per-corner light + turbidity layers), `MixinFogRendererWater` (biome-graded underwater fog), `WaterTurbidity` (biome key matching, spatial + temporal blending).

## Compatibility

### NeoForge patches (learned from the 2.0.0 crash)

NeoForge patches an alpha parameter into the fluid renderer's vertex calls on 1.21.x (their fluid transparency extensions); the 1.21.x branches carry one `@WrapOperation` per overload with `require = 0` so exactly one matches per loader and future shape changes degrade silently instead of crashing. The 26.x `FluidRenderer` path is unpatched (verified). `:neoforge:runClient` needs the Kotlin for Forge maven (YACL transitive).

### Polytone (lightmap mods)

Polytone's `LightTextureMixin` (priority -201) cancels `updateLightTexture()` at HEAD when a custom lightmap is active (e.g. from Excalibur resource pack). Since the Distant Horizons flicker fix, our darkening runs inside `updateLightTexture()`, injected right before the `DynamicTexture.upload()` call, so every observer of the pixels (Distant Horizons mirrors them at RETURN) sees the darkened version and there is no second upload. Trade-off: when Polytone cancels the method for a custom lightmap, our darkening is skipped with it; that interaction has not been re-checked since the change.

### Visual Effects+ (shader)

VE+ ships a `lightmap.fsh` core shader, but this shader program doesn't exist in vanilla 1.21.1 (added in 1.21.2). No conflict in 1.21.1. VE+ particle-based fog (via Polytone custom particles) is a different system.

## Reference sources

`.sources/` contains cloned repos (gitignored): grondag/darkness, True-Darkness-Refabricated, IMB11/Fog, MultiLoader-Template, distant-horizons-api-example.

## Dependencies

- YACL 3.6.2+1.21 (fabric/neoforge artifacts)
- ModMenu 11.0.3 (fabric, optional)
- Fabric API 0.109.0+1.21.1 / NeoForge 21.1.80
