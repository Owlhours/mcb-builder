# Method: how the bot works and how it was measured

This page explains how the planner builds, what rules it plays by, and where every number in the READMEs comes
from. For the practical "can I reuse this?" questions, read [BUILDING-ON-IT.md](BUILDING-ON-IT.md). For how it got
here, read [HISTORY.md](HISTORY.md).

## 1. The game is the real game

The bot was developed and measured against Mojang's own 26.2 server code, not a re-implementation.

- **The game.** `mcb.env.Env` boots the vanilla 26.2 server classes in-process: a flat world, no network, ticked on
  the calling thread. In the singleplayer mod, the integrated server's own tick loop drives it instead (a mixin
  on the server tick).
- **The body.** `mcb.env.BotPlayer` is an ordinary server-side player entity. Its movement comes from the
  unmodified vanilla physics, fed by key states exactly as the vanilla client's `LocalPlayer` and
  `KeyboardInput` produce them. Those code paths were transcribed from the 26.2 client classes.
- **Keyboard and mouse only.** `mcb.game.Client` is the bot's only way to observe or act (implemented here by
  `mcb.env.BotClient`). It can:
  - turn the view;
  - hold movement, jump, sneak and sprint;
  - left- and right-click, or hold either;
  - pick a hotbar slot;
  - open the inventory, click slots in an open screen, close it.

  One `tick()` is one client tick followed by one server tick. Clicks follow the client's order within a tick
  (slot pick, then clicks, then movement). They reach the game through the server's real packet handlers, so
  reach, placement rules, break times and container rules are all enforced by the game. What the crosshair rests
  on comes from the same ray cast the client uses. The bot learns about block changes only from the block-update
  packets its client is sent.
- **The rules oracle.** `mcb.env.ServerOracle` (an `mcb.game.Oracle`) answers "if I click there, holding this,
  looking that way, what appears?" by running the game's own placement code without placing anything. It is used two ways:
  - to tabulate, per item, what each combination of face, part of the face, view direction (32 yaw × 9 pitch
    classes) and sneaking produces;
  - to double-check every click just before it's made.

  It can't change the world.

The client mod implements the same `Client` and `Oracle` interfaces differently (`mcbc.LocalClient`,
`mcbc.ClientOracle`). There, the body drives the real `LocalPlayer` through a per-tick frame, the bot runs on its own
thread in lockstep with the game, and the oracle runs the client's copy of the placement code. The planner is the
same code in both mods; what differs about the client's world is set in `mcb.bot.Rules` (see
[DESIGN.md](DESIGN.md)).

## 2. How a build is made

The builder is a chain of classes, one concern each:

`BuildState` (shared state and helpers) → `Moves` (routes, travel, doors, columns, trap checks) → `Placing`
(putting one block into one cell) → `Stances` (where to stand) → `Scaffolds` (temporary blocks: upkeep and
clean-up) → `Supplies` (trips to the chests) → `Builder` (order of work, main loop).

Beside the chain sit `Grid` (the bot's own picture of the site), `Ray` (line of sight), `Body` (walking, aiming,
clicking, holding items) and `Nav` (route search).

**Order of work.**
- Every column is built bottom-up.
- A column may take its next block only when none of its eight neighbours has something lower still to do.
- Among the cells allowed next, the bot takes the nearest one it carries the material for.
- Some blocks are held back on purpose: iron doors go last, cobwebs wait until the first clean-up (the bot can't
  move in them), and gravity blocks wait for support. Once any piston in the build has fired, the cells in front
  of pistons wait until the end.

**Where to stand.** For each cell the planner looks for a place to stand from which a click on a neighbouring
face gives exactly the wanted state. It checks that state against the game's placement code for that very
click. The places it considers are, in order:
1. a block that already exists;
2. leaning over the edge of a block while sneaking;
3. a temporary column;
4. a block hung from something else.

A cell with no usable face yet is put off and retried later.

**Getting about.** `Nav` is a weighted A* search over the places a player can stand. Its moves are:
- walking, sprinting, jumping, dropping, and climbing ladders and scaffolding;
- opening doors and gates, and closing them again after;
- opening the trapdoors over ladders on the way through.

It also uses two kinds of temporary block:
- **Scaffolding columns** (the game's scaffolding block). The bot puts them up from inside the way players do,
  climbs and descends them, leaves them standing while they're useful, and takes them down from the foot, so the
  rest collapses by itself. They only ever stand where the target has nothing.
- **Loose blocks** (moss, or another quick-to-break cube the build doesn't use). These serve as bridges, as a
  face to click on where there's none, and as a cap on a column where the bot must sneak. They're remembered in
  order (the "rope") and taken away again before the bot moves on.

**Not getting stuck.** Most of the hard failures were one property broken in different ways: *there is a way
from the bot's position back to the chests, and the bot is alive.*
- The bot keeps one concrete path, a witness, and the set of cells that path depends on.
- An action that touches none of those cells can't break the property, so it needs no search. An action that
  does is simulated, and a new witness is searched for. If none exists, the action is refused.
- Searches have three outcomes: way found, no way, and budget used up. Treating "budget used up" as an answer was
  itself a past bug.
- If the bot ends up shut in anyway, it breaks out through the build and repairs the hole from outside.

**Restocking.** Materials come from the supply chests one load at a time, planned around the cells that come
next. What's no longer needed is put back, and scaffolding slots are reserved.

**Finishing.** When every column has been gone through:
1. Cells that were put off get further tries.
2. Scaffolding comes down, highest-standing columns first, while the lower ones are still there to climb.
3. What's left is fetched in rounds that may break through walls and mend them afterwards.
4. A repair pass forgets the bad cells of sections that still fail by the bot's own picture and builds them
   again, with the special rules for helpers and traps. A round that doesn't lower the bad cells is the last.

The bot stops building in time to clear its scaffolding before the time limit.

**Health.** The bot avoids lit campfires, magma, cactus and fire. In the measured runs, fall damage was off.

## 3. The judge

`mcb.build.Judge` divides the build into 16×16×16 sections counted from the schematic's origin.

- In each section, **bad cells = wrong + missing + left behind**. Scaffolding left inside the box counts.
- A section passes at **3 % or fewer bad cells** of its judged target cells. That's stricter than 3 % of the
  section's volume, which is reported too.
- A build passes when every section passes.

**What counts as right.** A cell is right when its block and its "intent" properties match. Properties that come
from neighbours, or that change by themselves, are ignored: fence, wall and pane connections, stair shape,
power, age, leaf decay distance, waterlogging and so on (`StateInfo.IGNORED`). Chest halves, repeater delay,
door open or closed, and similar properties are compared.

**Exempt cells** are neither missing nor part of a section's count:
- decoration: blocks with no collision box (flowers, torches, signs, rails, redstone dust...);
- blocks only an operator in creative mode can place: command, structure and jigsaw blocks, and the test blocks
  (the game's own `GameMasterBlock` list).

**Impossible cells** are cells whose target can't exist in survival, plus cells that hang only from such cells.
They count as standing right, whatever is in them, so this rule can only help a build. A placed bamboo shoot
counts as bamboo, because a single stalk never grows into bamboo with random ticks off.

**Target normalisation** (`Target`):
- Fluids, and plants that only exist in water, are not part of the target. Waterlogged blocks lose their water.
- Gravity blocks with nothing under them are dropped.
- Piston heads, portals and similar technical blocks are dropped.

**A second, independent Java port** of the judge was written to check the simulator's. The judge in this
repository is that port, and it agrees with the simulator's judge on the builds compared.

## 4. The world it was measured in

- **Time limit:** 48 hours of game time at 20 ticks per second, 3,456,000 ticks per build. Read literally, "48
  Minecraft-clock hours" would be 48,000 ticks, which no build of more than a few thousand blocks could meet.
- **World rules:** survival, Peaceful, fall damage off, random ticks off (no growth, decay or spread), time and
  weather frozen, no mob spawning, and block drops off. Broken blocks vanish instead of lying around.
- **Site:** supply chests stand north of the build in stacked rows. They hold everything the target needs plus a
  5 % reserve, scaffolding, and loose blocks. Every chest's last row is left empty so things can be put back.
- **Tools:** none. Every block is broken by hand.
- **Inventory:** a normal survival inventory, 36 slots.

## 5. How it was measured

**The corpus.** About 80,000 schematic files were harvested from public sharing sites: 79,135 were readable,
giving **74,515 distinct builds**. The median is 2.5k blocks, 96 % are at most 100k blocks, and 28.8 % are over
10k. The corpus isn't included, because the builds are other people's work under unknown licenses.

**The simulator.** Most measurements ran in a Rust simulator: a statement-by-statement port of this Java bot,
on a re-implementation of the parts of the game the bot touches. All five development sets, 290 builds, ran in
4.5 minutes, against an hour or more for the large set alone in Java.

How closely it matches the real game:
- At one checked revision it gave the Java verdict on 290 of 290 builds, and the exact row (ticks and every
  count) on 288.
- At the final revision, the Java bot played exactly the simulator's course on 39 of 40 builds of a development
  set. The 40th differed by a known 12-tick Java run-to-run variation.
- In the released mod, the `timber-frame-house` build took exactly the simulator's 68,361 ticks, and a 9,975-block
  build took exactly its 537,714 ticks, with the same judge counts.
- The simulator flags its own blind spots: a run whose log shows `unmodelled` or `lost` above 0 isn't trusted.

**Every headline number in these READMEs comes from the simulator.** It lives in the companion
repository [mcb-sim](https://github.com/Owlhours/mcb-sim), with the fast simulator and the headless Java runner.

**The goal samples.** Each is 1,000 builds drawn at random from the corpus, keeping the corpus's mix of sizes:
297 builds over 10k blocks, 43 of them over 100k. Neither sample was used for development; they were measurement
only.

| sample | filter | final result | 10k+ blocks | 100k+ |
|---|---|---|---|---|
| first sample | none | 929 / 1,000 | 238 / 297 (80.1 %) | 20 / 43 |
| **second sample** | no redstone component, no impossible cell, no build used in development before | **963 / 1,000** | **265 / 297 (89.2 %)** | 24 / 43 |

The first sample was closed at 929 when the remaining failures turned out to be mostly redstone machines firing
while being built, judge edge cases and huge builds. 23,960 of 59,586 fresh builds passed the second sample's
filter.

Second sample by size:

| blocks | pass | | blocks | pass |
|---|---|---|---|---|
| 0–50 | 26 / 26 | | 10k–30k | 116 / 122 |
| 50–300 | 107 / 107 | | 30k–100k | 125 / 132 |
| 300–1k | 195 / 195 | | 100k–300k | 22 / 27 |
| 1k–3k | 196 / 198 | | 300k+ | 2 / 16 |
| 3k–6k | 103 / 103 | | | |
| 6k–10k | 71 / 74 | | **all** | **963 / 1,000** |

The 37 failures: 13 ran out of time; 14 of the 16 builds over 300k blocks failed (most of their cells missing);
10 were near-misses on scaffolding the clean-up left behind; 9 had a few to a few hundred wrong or missing cells
in 1–6 sections. These overlap.

**How changes were judged.** This is the part most worth copying if you continue the work.
- **Runs are deterministic.** The same code on the same build gives the same ticks. But any change that alters
  one decision sends a build down a different course, and builds near a threshold then flip for reasons that
  have nothing to do with the change.
- **Chance was measured directly.** The same version was run under several seeds (the bot starting a few blocks
  further along). On 220 small builds, only 2 changed verdict with the seed. Most flips between two versions are
  therefore real, small, unintended effects of the change on particular builds.
- **The rule "no build may go from PASS to FAIL" was dropped.** A change that perturbs 200 builds has about four
  side effects in each direction, so that rule rejected nearly every broad change. Changes are judged on their
  *net* effect instead:
  - exact McNemar tests on the verdicts;
  - a sign-flip permutation test on log10(1 + bad cells);
  - a Skellam-based guard on the net loss;
  - a count of collapses (bad cells up tenfold, to 50 or more) that repeat under a second seed.
- **Decisions use 4–6 seeds** against the base version's own seed noise on the same builds. Each decision uses a
  freshly drawn held-out set that's read once.
- **A side effect is answered by narrowing** the trigger of the part that caused it, which perturbs fewer builds,
  never by adding a compensating behaviour. Without that rule, the work turned into an endless chain of fixes
  answering fixes.
- **Attribution costs one build.** Because runs are deterministic, a build that got worse can be rerun once per
  switch to name the part responsible. Every revision has an `MCB_NO_*` switch for this reason.

**What the model of failures said.** Fitted to an early full run, a build failed for size-independent reasons
about 8.6 % of the time, and met a fatal event (shut in, wedged, a region never reached) about once per 167,000
blocks. Under that model:
- halving the fatal events is worth about +2.8 points over the whole corpus;
- making the bot twice as fast is worth about +0.2 points.

**Speed was never the goal.** Time mattered only where it was a symptom, for example clean-up rounds undoing each
other. That's why the bot is 5–25 ticks per block and not faster.

## 6. The client mod's differences

The client mod runs the same planner with your player in a real, possibly hostile world.

- **The world is what the server sent.** Anything unknown counts as solid, so the bot doesn't assume an empty box.
- **Fall damage is on.** Planned drops are capped at 3.5 blocks.
- **The climb out of a scaffolding column is timed** so the player never hangs above the column top for a tick.
  That hang caused the server setbacks seen in testing.
- **Breaks are paced in wall time** to stay clear of fast-break checks.
- **Clicks wait for the server.** A click is made only with a view the server already has, and after each
  placement or break the bot waits for the server's acknowledgement.
- **Safety stops** pause the bot and hand control back to you.
- **Containers are yours.** It reads the containers you registered; nothing is filled for it.
