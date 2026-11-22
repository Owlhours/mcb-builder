# Design: how the code is organised, and why

This page is for people who want to change the bot or take pieces of it. It records the decisions made when the
code was reorganised after the 1.0.0 release (2026-11-22) so that it could be reused: one planner instead of
three copies, no Minecraft classes in the planner, tests that run anywhere. **None of it changed what the bot
does**; how that was checked is at the end.

## Layout

```
core/       the planner and everything else that needs no game: plain Java, one library (fastutil)
  src/mcb/game     the planner's view of the game: Client (the body), Oracle (the placement rules), Hit, Vec3, Stack ...
  src/mcb/bot      the planner (Builder and its chain), the route search (Nav), the site picture (Grid)
  src/mcb/build    Judge, Target, Site, and the block tables (StateInfo, ItemInfo) the adapter fills
  src/mcb/schem    the schematic readers, on their own NBT reader
  test/            unit tests (tools/test.sh)          examples/   runnable examples of the liftable pieces
adapter/    everything that touches Minecraft (adapter/README.md)
gate/       the regression gate: test builds generated in code, and their expected results per game version
builder/, client/   the two mods' READMEs and build scripts (their code is in adapter/builder and adapter/client)
tools/      deps.sh (the pinned downloads), test.sh, client-check.sh, check-core.sh, check-public-tree.sh
```

## Decisions

**One planner, with the differences behind settings.** The client mod had its own copy of the planner, which
differed in nine files. Those differences were of two kinds, and each became one explicit setting:
- *The world*, `mcb.bot.Rules`: fall damage (planned drops capped; nothing broken that carries the bot over a
  harmful drop), only the bot's own blocks broken outside the box, the world re-read when chunks arrive, and a timed
  climb out of scaffolding columns. `Rules.headless()` is the measured world; `Rules.client(maxFall16)` the client's.
- *The supplies*, `Site.depot`: a depot of chests the site sets up itself, or containers someone registered,
  each with its own access point, read by walking to them (`Supplies.survey`).

Each setting is off in the measured world, so the singleplayer mod and the headless runs take literally the code
paths they took before; the client mod takes literally the paths its copy took. The harness in
[mcb-sim](https://github.com/Owlhours/mcb-sim) compiles the same planner from this repository (it pins a commit in
`harness/mcb-builder.ref`) instead of keeping a third copy.

**Interfaces where the mods really differ, tables everywhere else.** The planner talks to the game in two ways:
it acts and observes through a body, and it asks what a click would place. Those are the two interfaces,
`mcb.game.Client` and `mcb.game.Oracle`, with one implementation per mod. Everything else the planner wanted to
know about blocks it used to ask Minecraft's `BlockState` objects directly ("is this a chest?", "which way does
this door face?", "what does the first of two slab clicks make?"). Those questions are now arrays indexed by state
id (`StateInfo`), filled once by the adapter (`mcb.mc.BlockFacts`). Tables rather than an interface with a method
per question because the planner asks them millions of times a second, and because a table can be filled by
anything: the game, a test palette (`SimplePalette`), a file.

**Items are numbers.** The planner's item maps used to be keyed by Minecraft's `Item` objects; they are keyed by
item ids now. Every map whose iteration order could matter was checked: all are insertion-ordered (`LinkedHashMap`)
or only looked up.

**The adapter is a folder, not a module system.** `adapter/` holds everything that uses Minecraft, split by who
needs it (`src/` both mods, `server/src/` the server-side body, `client/` the client mod ...). `tools/check-core.sh`
enforces the rule in CI: no file outside `adapter/` may use Minecraft's classes. A new game version changes files in
`adapter/` only; version-specific code goes into small per-version source folders (see `adapter/versions/`).

**Plain build scripts, no Gradle.** Minecraft 26.x ships unobfuscated, so the mods compile straight against the
vanilla jars with `javac`. The scripts download the jars once, check each against a pinned SHA-1
(`adapter/versions/<mc>.env`) and never redistribute them. A Gradle/Loom setup would add a large toolchain and a
remapping step that has nothing to do here. The cost is that IDEs need the classpath pointed at `.deps/` by hand.

**Tests that need nothing private.** Two layers:
- *Unit tests* (`tools/test.sh`, JUnit, no Minecraft): the schematic readers on files the tests write themselves,
  the judge's rules, the route search, the site picture, position packing.
- *The regression gate* (`gate/gate.sh`): 31 test builds — huts, stairs and slabs in every orientation, towers,
  bridges, overhangs, trapdoors, gravity blocks, cobwebs, magma, a closed box, iron doors, a keep, a hill, a village —
  built by the real bot in the headless game, every row (ticks, verdict, missing, wrong, leftover, placed,
  scaffolding, trips) compared exactly with `gate/expected/<mc>.tsv`. The bot is deterministic, so any change in what
  it does shows up as a changed row. A change that is *meant* to change behaviour re-records the rows
  (`gate/gate.sh --record`) and says why in the commit.

  The test builds are **generated in code** (`gate/src/mcb/gate/Synth.java`), not stored as files: nothing
  third-party can slip in, the public-tree check stays a blanket ban on schematic files, the builds are readable
  and reviewable as code, and each carries a content digest so an accidental edit is caught. The release
  schematics and the development corpus stay outside the repositories; results on them are summarised in the docs.

**The client mod's planner has its own check.** `tools/client-check.sh` runs the client mod's *old* copy of the
planner (from git history, commit `0ff6f68`) and the shared planner side by side in the headless game with the
client's settings (`mcb.headless.ClientRun`: fall damage on, registered containers read by walking to them, the grid
read from the world, the client's `Rules`), and requires identical rows — including a trace of every key, click and
position, tick by tick. It runs in CI too. What it cannot check is the client mod's own body (`mcbc.LocalClient`:
latency, server acknowledgements, safety stops); that code was converted mechanically (types only).

## How "nothing changed" was checked

- At every step: the gate (31 of 31 rows identical) and, from the merge on, the client check (31 of 31 identical,
  traces included).
- At each milestone, the full tier in the headless game against the baseline recorded at the published commits
  (mcb-builder `0ff6f68`, mcb-sim `8414a5c`): 290 corpus builds (dev3, dev2big, small220) and the 9 release
  schematics, compared on ticks, verdict, and the missing, wrong and leftover counts, and with `mcsim` as an
  independent check. Results: [docs/HISTORY.md](HISTORY.md#2026-11-22-the-reorganisation).
- The simulators' tables generated from the game (`harness/run.sh tables` in mcb-sim) are byte-identical to the
  baseline's (the placement tables differ only for the three items whose age is random, as between any two
  baseline runs).
- The mods' class files were byte-identical to the release's after the pure moves; after the real changes, the
  gate and the full tier are the evidence.

One thing learnt on the way: the Java bot is deterministic only on a machine that keeps up. The game loads chunks
on worker threads; on a heavily overloaded machine (load 50–70 on 8 cores) one build took a different course once
in four runs. Gate runs use at most one worker per core.
