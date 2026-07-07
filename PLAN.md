# EndTerraForged v0.1.6+ Implementation Plan

> Created 2026-07-04 after RTF preset-system research.
> User confirmed scope: button hook-in first, then End-specific terrain fields, then global preset system.

## Plan summary

Three-step incremental rollout, one PR per step, each step independently testable + releasable.

### Step 1 (this PR — v0.1.6-preview): Bug fix + MixinPresetEditor hook-in

**Bug fix (blocking):** v0.1.5-preview crashes at server-tick start with:

```
InvalidInjectionException: @At("HEAD") selector @Inject handler before super()
invocation must be static in injector net/minecraft/world/level/levelgen/
RandomState::endTerraForged$initCapture
target: <init>(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)V
```

Root cause: `MixinRandomState.endTerraForged$initCapture` is an instance method
(`private void ...`) injected via `@At("HEAD")` on `<init>`. The Mixin processor
injects `<init>` HEAD bytecode BEFORE the `aload_0; invokespecial Object.<init>`
sequence, i.e. before `super()` has been called. At that point `this` is
uninitialised, so the handler must be `static`. v0.1.5 reached the main menu
because `<init>` isn't exercised until a world loads; it crashed the moment the
user clicked Create New World and the server tick started ChunkMap → ServerLevel.

Fix: change the `@At("HEAD")` to `@At(value = "INVOKE",
target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;")`.
Vanilla `RandomState.<init>` calls `NoiseRouter.mapAll(...)` once (verified via
`javap -c`) AFTER `super()` has run and AFTER it has fetched the `noiseRouter`
from `NoiseGeneratorSettings`. Injecting just before that call:
- runs after super() → instance handler valid
- runs before `router.mapAll` → the `@Unique` fields are visible to the
  `EndDensityVisitor` that `MixinNoiseChunk.@Redirect mapAll` will install

**Feature: MixinPresetEditor** — register a `PresetEditor` for
`WorldPresets.NORMAL` so the existing "Customize" button on the World tab of
`CreateWorldScreen` opens `EndPresetEditorScreen`. Pattern: same as RTF's
`MixinPresetEditor` — `@Redirect` on `PresetEditor.<clinit>`'s `Map.of(k1,v1,k2,v2)`
to convert it to a `HashMap` that additionally contains the EndTerraForged entry.

Both fixes in one PR because the bug fix is required before any feature work
can be tested in-game.

### Step 2 (next PR — v0.1.7-preview): EndTerrainSettings field expansion

Add a sub-record `EndTerrainSettings` to `EndPreset` covering the End-relevant
tuning knobs that match the user's "mountain heights etc." ask:
- `globalVerticalScale` (master vertical multiplier)
- `globalHorizontalScale` (master horizontal multiplier)
- `islandRadius` (continent base radius in cells)
- `islandHeight` (peak mountain height, blocks)
- `islandFalloff` (0..1, controls steepness from peak to coast)
- `highlandsWeight` (frequency of highlands ring)
- `midlandsWeight` (frequency of midlands ring)
- `barrensWeight` (frequency of barrens ring)
- `floatingIslandsDensity` (0..1, island count per chunk area)
- `floatingIslandsHeight` (max height of floating islands, blocks)

Plus a new sub-editor screen mirroring `ErosionConfigEditorScreen`. The
existing `EndPresetEditorScreen` gains a "Terrain..." button that opens the new
sub-screen.

### Step 3 (later PR — v0.1.8-preview): Global preset system

Adopt RTF's global preset directory model:
- `<config>/endterraforged/presets/<name>.json` per preset
- `EndPresetManager.list/load/save/delete/copy`
- `ConfigUtil` `@ExpectPlatform` for cross-loader config-dir resolution
- Built-in "Default" preset (in-memory, not deletable)
- New `SelectEndPresetPage` screen (preset list + create/copy/delete/import)
- Replace the "Done" button on `EndPresetEditorScreen` with a "Save as..."
  flow that prompts for a name and writes to the global directory.

Keep `EndPresetStorage` for per-world persistence — when a world is loaded
the per-world file takes precedence over the global preset (so worlds stay
reproducible across preset changes).

### Deferred (not yet scheduled)

- Port RTF's `LinkedPageScreen` / `BisectedPage` / `WidgetList` GUI framework
  when field count outgrows a single screen.
- Live noise preview (`NativeImage`-based) — expensive, skip for now.
- End-specific climate/river/structure settings (mostly overworld-only).
