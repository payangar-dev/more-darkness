# More Darkness

Minecraft 1.21.1 mod (Fabric + NeoForge). Enhances darkness: moon phases affect night light, caves are pitch black.

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

- **common/** — Config (GSON POJO), darkness logic (lightmap manipulation), mixins shared by both loaders (MixinLightTexture, MixinGameRenderer)
- **fabric/** — Entry point (ClientModInitializer), YACL config screen, ModMenu integration
- **neoforge/** — Entry point (@Mod), YACL config screen, IConfigScreenFactory

**YACL cannot go in common** — fabric artifact uses intermediary mappings, common uses Mojang. ConfigScreenBuilder is duplicated in each loader module (same code, different artifact).

## Key classes

- `DarknessCalculator` — Computes darkened lightmap (16x16 pixels). Moon phase via `world.getMoonBrightness()`, quadratic curve, configurable minimum brightness (default 0.05 = starlight).
- `MoreDarknessConfig` — Plain POJO, GSON serialized to `config/more_darkness.json`.

## Compatibility

### Polytone (lightmap mods)

Polytone's `LightTextureMixin` (priority -201) cancels `updateLightTexture()` at HEAD when a custom lightmap is active (e.g. from Excalibur resource pack). To avoid being skipped, our lightmap darkening injects AFTER the `updateLightTexture()` call in `GameRenderer.renderLevel()` — not inside `updateLightTexture()` itself. This works regardless of which mod computed the lightmap.

Flow: `MixinGameRenderer` captures dirty flag at HEAD → vanilla/Polytone computes lightmap → our AFTER injection darkens pixels and re-uploads (16x16 = 1KB, negligible cost).

### Visual Effects+ (shader)

VE+ ships a `lightmap.fsh` core shader, but this shader program doesn't exist in vanilla 1.21.1 (added in 1.21.2). No conflict in 1.21.1. VE+ particle-based fog (via Polytone custom particles) is a different system.

## Reference sources

`.sources/` contains cloned repos (gitignored): grondag/darkness, True-Darkness-Refabricated, IMB11/Fog, MultiLoader-Template, distant-horizons-api-example.

## Dependencies

- YACL 3.6.2+1.21 (fabric/neoforge artifacts)
- ModMenu 11.0.3 (fabric, optional)
- Fabric API 0.109.0+1.21.1 / NeoForge 21.1.80
