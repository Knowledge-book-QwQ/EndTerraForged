# P5 3D Hydrology Architecture Plan

> Document status: current planning specification; no production runtime is implemented.
> Last updated: 2026-07-25.
> Scope: `format_version=5` surface hydrology, river/lake profiles, immutable artifacts and runtime sampling.
> Surface erosion selection remains owned by [`P4_7_EROSION_ALGORITHM_RESEARCH.md`](P4_7_EROSION_ALGORITHM_RESEARCH.md).

## 1. Objective

P5 builds the first authoritative End hydrology system after the `format_version=4` macro surface has passed
visual, volume, performance and compatibility gates. It consumes the accepted final macro terrain instead of
requiring terrain generation to manufacture a continent-centre head gradient.

The highest invariant is:

```text
routing potential != authoritative bed/water profile != visible terrain corridor
```

Continent centre and ownership may select deterministic domains and cache keys, but they must not directly
determine terrain elevation, receiver direction, bed elevation or water surface elevation.

## 2. Version Boundary

- `format_version=3` permanently retains the existing legacy terrain semantics.
- `format_version=4` owns `REGION_PLANNED`, AREA/RIDGE terrain, coast/archipelago and the selected P4.7
  surface erosion pipeline. It does not silently gain real hydrology after release.
- `format_version=5` introduces the authoritative hydrology artifact and real surface rivers/lakes.
- Missing hydrology fields in v4 remain hydrology-disabled. A jar update must not reinterpret ungenerated v4
  chunks as v5 terrain.

Hydrology parameters cannot enter a persistent preset until Codec, Validator, Builder, runtime, preview,
round-trip, invalid-state, deterministic and observable-behaviour tests exist together.

## 3. Input Contract

P5 starts from immutable, already accepted inputs:

- final P4.7 macro terrain top before river corridor carving;
- continent identity, landness and inlandness;
- AREA ownership, visible family, terrain tags and erosion resistance;
- RIDGE and archipelago physical influence;
- finite volume support, local thickness and void/ocean connectivity;
- central vanilla protection and outer activation;
- actual world bounds and sea mode.

Hydrology must not call the legacy selector, generate a second terrain topology or recover ownership from
height. Surface erosion and hydrology may share pure primitives, but they must not maintain competing final-top
authorities.

## 4. Required Pipeline

```text
accepted final macro terrain
-> bounded hydrology domain + ocean/terminal halo
-> depression hierarchy and explicit terminal classification
-> provisional adaptive multiple-flow routing
-> deterministic single-receiver collapse
-> exact physical-area accumulation
-> shared node/reach graph
-> outlet-first feasible bed/water profile
-> exact pool/step/cascade quantization
-> bounded visible corridor and lake footprint
-> final terrain/volume update
-> real water placement
-> immutable primitive artifact
-> allocation-free runtime sampling
```

Priority-Flood is a depression/watershed foundation, not a complete visual river algorithm. Stream-power,
profile feasibility and corridor morphology remain separate passes with separate diagnostics.

## 5. Authority Model

One node/reach artifact is the authority for all downstream consumers:

- receiver and terminal type;
- physical contributing area and discharge proxy;
- confluence identity and reach ordering;
- bed elevation and water surface elevation;
- pool, step and cascade classification;
- corridor width, bank envelope and lake footprint;
- provenance, source domain and version.

Surface placement, water blocks, preview, Content Pack selectors and future underground-river integration read
the same profile. They must not recompute water Y from continent distance, local terrain tags or an independent
surface heuristic.

## 6. Runtime Artifact

The production artifact uses immutable primitive structure-of-arrays storage. The minimum candidate channels
are:

```text
nodeX/nodeZ, receiver, terminalType, contributingArea,
rawElevation, bedElevation, waterElevation,
reachId, dischargeClass, corridorHalfWidth, profileFlags
```

The exact schema remains provisional until the feasibility slice is complete. The following constraints are
already fixed:

- no pooled or mutable RTF `Cell` graph;
- no `GeneratorContext`, `RiverCache` or global water table;
- no registry holder or biome object in the artifact;
- no per-sample object creation;
- no unbounded coordinate map;
- no access-order-dependent random stream;
- no strong reference from long-lived C2ME workers to completed worlds.

## 7. Domain, Cache And Scheduling

- Domains are region-aligned and include a derived halo large enough for depression, receiver and corridor
  stencils. Halo size is part of the algorithm contract, not a tuning shortcut.
- Domain keys include seed namespace, resolved preset fingerprint, world bounds and canonical domain coordinate.
- Runtime sampling is read-only and allocation-free after worker scratch initialization.
- Scratch is worker-owned and bounded. Formal worldgen does not create an ETF executor or nested parallel work.
- A bounded immutable-result single-flight cache may be considered only after JFR proves duplicate domain builds
  are material. It cannot be introduced as a correctness dependency.
- Cache hits, misses, evictions, owner swaps and duplicate builds are measurable without changing output.

## 8. Delivery Slices

### P5.0 Contract replay

- Replay the isolated feasibility proof on the project JDK 21 toolchain.
- Port only pure algorithm contracts into ETF-owned tests and primitive fixtures.
- Record any difference between proof assumptions and Minecraft world bounds, floating shelves or ocean modes.

### P5.1 Bounded domain and terminal model

- Build a deterministic macro-top input artifact with explicit ocean, void, protected and unresolved terminals.
- Prove adjacent domains agree on shared input and terminal halo samples.
- Reject domains that cannot resolve a safe terminal instead of inventing centre-directed flow.

### P5.2 Depression and receiver graph

- Implement depression hierarchy/Priority-Flood and provisional adaptive multiple-flow routing.
- Collapse to one deterministic receiver per node for the authoritative DAG.
- Prove acyclicity, terminal reachability, exact tie-breaking and access-order independence.

### P5.3 Accumulation and shared reaches

- Accumulate physical contributing area using world-space sample area.
- Build shared confluence/reach identity once; tributaries do not own independent water profiles.
- Verify conservation, confluence agreement and cross-domain identity.

### P5.4 Feasible bed and water profiles

- Solve feasible intervals from constraints, then assign profiles outlet-first.
- Enforce downstream monotonicity except explicit pool/step/cascade transitions.
- Quantize pools, steps and cascades from the shared profile, not from surface repair code.

### P5.5 Corridor, lakes and real water

- Generate bounded valley/bank/lake footprints from reach properties and local terrain support.
- Apply corridor carving before final volume publication so top and underside remain coherent.
- Place real water from the authoritative profile and preserve central vanilla behaviour.

### P5.6 Preview, Content Pack context and production gate

- Add graph, accumulation, bed, water, pool and corridor diagnostics using the production artifact.
- Expose stable read-only hydrology fields to the later Content Pack API.
- Complete Standard client, ETF/RTF/C2ME, JFR, long-generation and world-reload gates before v5 is enabled.

## 9. Verification Gates

Every production candidate must pass:

1. fixed-seed primitive fixtures for depressions, saddles, confluences, lakes, steps and cascades;
2. acyclic receiver graph and terminal reachability;
3. exact contributing-area conservation;
4. downstream profile feasibility and shared confluence equality;
5. adjacent-domain border identity and different visit-order parity;
6. worker-count and C2ME bit parity;
7. finite shelf and void-edge volume safety;
8. zero influence in the vanilla central protection region and hydrology-disabled v4 worlds;
9. bounded heap, allocation, cache and first-domain p95 budgets;
10. real-client visual, server MSPT and render/JFR evidence for all four mod combinations.

The Standard product target remains sustained new-chunk generation below 40 MSPT, without sustained periods
above 50 MSPT. A candidate that improves rivers but fails memory, first-domain latency, C2ME parity or volume
safety is rejected.

## 10. RTF Reuse Boundary

The RTF R10X research, paper review, feasibility proof and work packages are design evidence. ETF may reuse or
port independently verified MIT pure mathematics with source attribution and `NOTICE.md` updates.

ETF does not migrate:

- centre-high uplift or `LEGACY_UPLIFT` compatibility;
- `cell.waterTable` authority reuse;
- radial centre-to-coast root generation;
- RTF surface/gasket water repair;
- mutable `Cell`, `GeneratorContext`, private executor or `RiverCache` lifecycle;
- main-world biome, ocean or surface-material coupling.

The feasibility proof establishes algorithmic possibility only. JDK 21 replay, Minecraft runtime integration,
cross-domain continuity, cache lifecycle, client quality and performance remain ETF gates.

## 11. Relationship To Later Stages

- P4.7 may output surface drainage diagnostics and dry incision, but it does not publish authoritative water.
- The Content Pack API freezes hydrology-facing fields only after P5 establishes stable semantics.
- The underground stage may add cave/abyss terminals and underground reaches, but it must extend the same
  authority model instead of creating a second underground water table.
- Main-island integration remains the final stage and cannot influence the external-domain graph design.
