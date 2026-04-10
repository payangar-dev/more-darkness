# More Darkness

Minecraft 1.21.1 mod (Fabric + NeoForge). Enhances darkness: moon phases affect night light, caves are pitch black, depth fog underground.

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

- **common/** — Config (GSON POJO), darkness logic (lightmap manipulation), fog logic (Y-based), mixins shared by both loaders (MixinLightTexture, MixinGameRenderer)
- **fabric/** — Entry point (ClientModInitializer), fog mixin (MixinFogRenderer), YACL config screen, ModMenu integration
- **neoforge/** — Entry point (@Mod), fog via ViewportEvent events, YACL config screen, IConfigScreenFactory

**YACL cannot go in common** — fabric artifact uses intermediary mappings, common uses Mojang. ConfigScreenBuilder is duplicated in each loader module (same code, different artifact).

## Key classes

- `DarknessCalculator` — Computes darkened lightmap (16x16 pixels). Moon phase via `world.getMoonBrightness()`, quadratic curve, configurable minimum brightness (default 0.05 = starlight).
- `DepthFogCalculator` — Y-position based fog. Smoothstep from sea level to min build height. Overworld only.
- `MoreDarknessConfig` — Plain POJO, GSON serialized to `config/more_darkness.json`.

## Reference sources

`.sources/` contains cloned repos (gitignored): grondag/darkness, True-Darkness-Refabricated, IMB11/Fog, MultiLoader-Template.

## Dependencies

- YACL 3.6.2+1.21 (fabric/neoforge artifacts)
- ModMenu 11.0.3 (fabric, optional)
- Fabric API 0.109.0+1.21.1 / NeoForge 21.1.80
