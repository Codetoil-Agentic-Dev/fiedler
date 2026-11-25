# Fiedler

**A NeoForge mod for Minecraft 26.3 that adds no new blocks, items or mobs. It adds only *connections*.**

Fiedler takes the message of the r/MinecraftMemes post "This is Legacy Minecraft / This is good update design /
This is whatever Mojang is doing" (u/Isjoni) to heart. Read as graphs, the post's panels say one thing:

- **Good design.** New features are wired into the old ones and into each other, and old features get revisited.
  The graph stays one connected piece with many loops, and features from different updates mix.
- **Bad design.** New features arrive as islands, old features get cut off, obvious links are missing, and promised
  features never come.

Fiedler is named after Miroslav Fiedler's algebraic connectivity λ₂. λ₂ is the second-smallest eigenvalue of a graph's
Laplacian, and it is positive exactly when the graph is connected.

## Design rules (enforced by tests, not just stated)

1. **No islands.** The mod adds no new nodes, only edges. Each edge joins a newer feature to an older one from a
   *different* update era.
2. **Nothing made obsolete.** Every connection adds an option and the old way keeps working. For example, a test checks
   that the slimeball sticky-piston recipe still works next to the new resin one.
3. **Missing links only.** Each connection is something that looks as if it should already work. Each was checked
   against the decompiled 26.3 code and data to make sure vanilla doesn't already do it.
4. **No "coming never".** The design graph names a GameTest for every connection. A meta-test fails the build if a
   connection has no registered test, and the suite fails if any test does.

## The connections

| Connection | Eras | What happens | Why nothing becomes obsolete | GameTest |
|---|---|---|---|---|
| **Brush → mossy stone** | 2023 → 2009/2011 | Six brush strokes (3 s) scrub mossy cobblestone or stone bricks back to clean ones. This includes stairs, slabs and walls, which keep their shape. A moss carpet drops, and the brush loses 1 durability in survival. | Mossy crafting is unchanged. Restoring gives back ⅔ of the moss block it cost, so nothing can be duplicated. | `moss_restoration` |
| **Goat horn → village alarm** | 2022 → 2019 | Blowing a *Call* or *Seek* horn (tag `#fiedler:sounds_village_alarm`) does what a bell does, with the bell's own numbers. Villagers within 32 blocks take cover and raiders within 48 blocks glow for 3 s. | Bells are unchanged. The horn is the portable version and keeps its 7 s cooldown. | `horn_alarm` |
| **Wind bursts → campfires** | 2024 → 2019 | Wind charges, breeze shots and the mace's Wind Burst blow out campfires and soul campfires. Vanilla already blows out candles this way. | You relight campfires as usual. | `wind_extinguishes_campfire` |
| **Cats → cushions** | 2019 → 2026 | An idle tame cat whose owner is nearby walks to a free cushion and naps on it for 30–90 s. Right-clicking shoos it off (it hisses and stays away for a minute); right-click again to sit. | Purely additive. | `cat_sits_on_cushion` |
| **Chiseled bookshelves → enchanting** | 2023 → 2011 | Each shelved book counts as ⅙ of a bookshelf, so 15 full chiseled shelves give level-30 offers. Glyph particles flow from shelves that hold books. | A plain bookshelf is cheaper per unit of power (3 books instead of 6). | `chiseled_bookshelf_power` |
| **Resin → sticky pistons** | 2024 → 2011 | A resin clump on a piston crafts a sticky piston. | The slimeball recipe still works (tested). | `resin_sticky_piston` |
| **Slime block → Sulfur Cube** | 2014 → 2026 | Slime blocks give the *bouncy* archetype. | Data tag addition only. | `sulfur_cube_archetypes` |
| **Honey block → Sulfur Cube** | 2019 → 2026 | Honey blocks give the *sticky* archetype. | Data tag addition only. | `sulfur_cube_archetypes` |
| **Sulfur → gunpowder** | 2026 → 2009 | A sulfur spike, charcoal and bone meal make 2 gunpowder. Black powder is sulfur, charcoal and saltpetre; bone meal stands in for the fertiliser-derived nitre. | Sulfur spikes don't regrow, so creepers stay the renewable source. | `sulfur_gunpowder` |

Two further tests keep the mod honest: `feature_graph` checks the design graph described below, and `graph_command`
runs `/fiedler graph` and `/fiedler connections` through the server's real command dispatcher.

## The feature graph

`src/main/resources/fiedler/feature_graph.json` is the design document stored as data. It is a curated slice of the
vanilla feature graph around the features Fiedler touches. Each of its 40 nodes is tagged with the era it shipped in.
Each edge is either an interaction that already exists in 26.3 or one that Fiedler adds. In game, `/fiedler graph`
prints:

| | n | m | β₀ (pieces) | β₁ (loops) | λ₂ | r (era mixing) |
|---|---|---|---|---|---|---|
| vanilla 26.3 slice | 40 | 37 | 5 | 2 | 0 | +0.227 |
| with Fiedler | 40 | 46 | **1** | 7 | **0.0388** | **+0.134** |

In the vanilla slice the newest features sit on islands: brush–armadillo–suspicious sand, wind charge–breeze–candle, and
resin–creaking. That is the meme's third panel. Fiedler's 9 edges join everything into one component and cut the
tendency of features to connect only within their own era (Newman's mixing coefficient r; 1 means every edge stays in
its era).

**Caveat:** the slice is hand-picked around the mod's own features, so its absolute numbers are illustrative, not a
measurement of Minecraft as a whole. The GameTest enforces the *structural* claims instead:

- every node points at blocks, items and entities that exist in this Minecraft version;
- every Fiedler edge crosses eras and names a registered GameTest;
- the slice is connected (β₀ = 1, λ₂ > 0);
- r decreases when Fiedler's edges are added.

The pure-Java spectral code (`fiedler.graph.SpectralGraph`, a cyclic Jacobi eigensolver plus union–find) is unit-tested
against closed-form spectra (P_n, C_n, K_n), against similarity invariants on random matrices, and against the meme
itself. The three panels were transcribed pixel by pixel. For example, the "Legacy" panel's Laplacian has
characteristic polynomial x(x−6)(x²−7x+9)(x²−7x+11), so λ₂ = (7−√13)/2. The "Mojang" panel has β₀ = 5. Setting the
"Fletching table (Coming never)" edge to weight 0 makes β₀ = 6.

## Building and testing

You need JDK 25 (Minecraft 26.3's Java version). The build uses NeoForge 26.3.0.118 with ModDevGradle 2.0.148.

```bash
./gradlew build
```

```bash
./gradlew runGameTestServer
```

`build` compiles the mod, runs the JUnit tests and produces `build/libs/fiedler-1.0.0.jar`. `runGameTestServer` starts
a headless server, runs every GameTest and exits non-zero if any test fails.

CI (`.github/workflows/test.yml`) runs both on every push. It then launches a real NeoForge 26.3 client with the built jar
through [MC-Runtime-Test](https://github.com/headlesshq/mc-runtime-test), which uses HeadlessMc. The client joins a
singleplayer world and the job fails if the game crashes. GameTests stay on the development server, because NeoForge
only registers GameTests outside production.

## Known limits

- The two new recipes have no recipe-book unlock advancements. They can be crafted from the start but don't appear in
  the recipe book until crafted once.
- Before CI, the client was never launched: the build container can't open a game window. The MC-Runtime-Test step is
  the client check.
- Install the mod on both client and server. The enchanting-power change runs server-side; the client copy only adds
  the glyph particles.
- The tests use `GameTestHelper#makeMockServerPlayerInLevel`, which 26.3 marks as deprecated-for-removal (it is still
  the only in-level mock player).

## License

MIT; see `LICENSE`.
