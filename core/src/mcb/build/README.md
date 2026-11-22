# mcb.build: the judge, the target, the site and the block tables

Plain Java (no Minecraft). Three things in this package are useful on their own:

- **`Judge`**, the self-check: compares what stands in a region with a target, section by section.
- **`Target`**, the normalisation of a schematic into what a survival player can build.
- **`StateInfo` / `ItemInfo`**, the per-block-state and per-item tables everything else reads.

`Site` (where the build goes and where its supplies are) belongs to the planner; see [../bot/README.md](../bot/README.md).

## The judge

```java
Target target = new Target(schem);                  // a Schem from mcb.schem (or built by hand)
char[] world = ...;                                 // what stands there now, same layout as target.ids
Judge.Result r = Judge.judge(target, world);        // or judge(target, world, survival), see below
r.pass;                                             // every 16x16x16 section has at most 3 % bad cells
r.missing; r.wrong; r.leftover;                     // the bad cells by kind
r.sections; r.failedSections; r.worstRatio;
r.examples;                                         // "wrong oak_stairs <- stone @3,0,2" ...
```

The rules, in short (full version: [docs/METHOD.md](../../../../docs/METHOD.md) §3):
- Sections are 16×16×16 cells counted from the target's origin. In each, bad = wrong + missing + left behind,
  and the section passes at 3 % or fewer bad cells of its judged target cells.
- A cell is right when its block and its "intent" properties match: `StateInfo.canon` maps every state to a
  representative with the properties that come from neighbours or change by themselves (connections, power, age,
  waterlogging ...) set to defaults.
- Decoration (no collision box) and operator-only blocks are exempt.
- Impossible cells (targets that can't exist in survival, and what hangs only from them) count as right. The
  "can it stay here?" part of that rule asks a `Survival`: the game's own rule in the adapter
  (`mcb.mc.GameSurvival`). Without one, only unplaceable items and unsupported gravity blocks make a cell
  impossible.

`world` is a `char[]` of state ids; `Site.snapshot(BlockView)` reads one from any world you can wrap as a
`BlockView` (`(x, y, z) -> stateId`).

## The tables

Everything reads block facts from `StateInfo` (indexed by the game's global state id) and `ItemInfo` (by item
id). Nothing here knows Minecraft's classes; someone has to fill the tables once per process:

| filler | when |
|---|---|
| `mcb.mc.BlockFacts.install()` (adapter) | in the game: the mods and the headless runner call it; it fills the tables from the running game version |
| `SimplePalette.install()` | tests and examples: 18 made-up states (stone, slabs, ladders, scaffolding, a torch, water ...). Not the game's data |
| your own | set `StateInfo.filler`, or call `StateInfo.allocate(n)` and fill the arrays yourself |

The tables are documented field by field in `StateInfo.java`.

## A runnable example

`core/examples/mcb/example/JudgeExample.java` judges a small wall with one wrong and one missing block, using
`SimplePalette`. With a JDK 25 and fastutil (the planner's one library; `tools/deps.sh` fetches it, or take
`fastutil-8.5.18.jar` from Maven Central):

```bash
javac -cp fastutil-8.5.18.jar -d out $(find core/src core/examples -name '*.java')
java -cp fastutil-8.5.18.jar:out mcb.example.JudgeExample
#   FAIL sections=1 failed=1 target=8 wrong=1 missing=1 leftover=0 exempt=1 ...
```

The unit tests in `core/test/mcb/build` (`tools/test.sh core`) show more of the rules.
