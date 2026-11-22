# Building on it: what you're walking into

Read this before you fork the planner into a companion bot, an autonomous player, a server script or anything
else. The bot builds well, but the code was written for one benchmark, and that shows.

## How it was written

- **It was written by AI agents.** Claude agents wrote it in an automated loop over about ten days
  (2026-11-12 to 11-21), working against a fixed benchmark: the share of randomly drawn schematics that pass the
  judge. A person set the goals and made the judgement calls (judge rules, which changes to keep, when to stop).
  The commit messages carry `Co-Authored-By: Claude` trailers. See [HISTORY.md](HISTORY.md).
- **It's optimised for the benchmark, not for readability.** Most changes were kept because they moved the pass
  rate on held-out builds. Many of them are narrow rules for situations found in specific builds ("cobwebs wait
  until the first clean-up", "keep the block underfoot over a hopper floor").
- **The comments speak an internal language.** They refer to revision names (`covertour`, `hungtower`...), to the
  Rust simulator's functions and environment variables (`rev().walk_way`, `MCSIM_COVW`), and to build hashes from
  the corpus. None of those are in this repository. [HISTORY.md](HISTORY.md) has a glossary of the revision names.
- **There are about 40 `MCB_NO_*` environment switches.** Each turns off one revision, and they exist so a change
  could be blamed on one build. They are all on by default. Removing one changes the bot's course on many builds,
  so treat them as features, not dead code.
- **The commit history is real but filtered.** It's the development history cut down to the files in this
  repository. Messages often mention files, tools and sets that aren't here.

## Technical debts

- **It's built for one game version at a time.** The planner itself (`core/`) uses no Minecraft classes, but the
  adapter (`adapter/`) uses the game's internals: server packet handlers, the placement code, the physics, block
  definitions. A new Minecraft version means checking and adapting the adapter; that is the only place that changes
  (see [../adapter/versions/](../adapter/versions/)).
- **The benchmark is not here.** The development tests were whole-corpus runs in the simulator, on builds that are
  other people's work and can't be published. What is here: unit tests (`tools/test.sh`), a regression gate of 31
  generated test builds that catches any change in what the bot does (`gate/gate.sh`), and a check of the client
  mod's planner (`tools/client-check.sh`). They tell you whether you changed the bot's behaviour, not whether you
  made it better; for that you need the simulator in [mcb-sim](https://github.com/Owlhours/mcb-sim) and a corpus of
  your own. (Until 2026-11-22 there were no tests at all, and the client mod had its own copy of the planner;
  [DESIGN.md](DESIGN.md) says how that was undone.)
- **The planner blocks while it thinks.** In the singleplayer mod it runs between server ticks, so the game can
  hitch for a moment on big builds. The client mod runs it on its own thread in lockstep: the game waits while it
  thinks, up to `maxThinkMs`.
- **Memory grows with the site box.** The grid and route search keep about 17 bytes per cell of the box. Sites
  over about 100 million cells would need a sparse representation.
- **Classes are big.** The bot is about 6,600 lines, and `Moves` and `Builder` are over a thousand lines each of
  interlocking rules.

## Assumptions it makes

| assumption | singleplayer mod | client mod |
|---|---|---|
| The world outside the box is flat ground and air | yes | no: unknown counts as solid |
| Supplies | chests set up and filled by the mod | your containers; it pauses when something runs out |
| Danger | Peaceful, fall damage off | real damage; it pauses on hostiles, players, lava, damage, low food |
| Tools | none; every block is broken by hand | none; same |
| Redstone | live while it's built | live while it's built |
| Food | not needed | it never eats; it pauses when food is low |
| Other players | none | any player in range pauses it |
| Speed | the server's own ticks | 20 TPS assumed for break timing |

## Using it in your own project

**The seams you can use** (each with a README and a runnable example):
- **`mcb.game.Client`, the body.** The planner only ever acts through this interface. Two implementations ship:
  `mcb.env.BotClient` drives a server-side bot player, `mcbc.LocalClient` your real `LocalPlayer`. To put the
  planner in a different body (another client framework, a headless protocol client, a different player entity),
  implement it. See [core/src/mcb/bot/README.md](../core/src/mcb/bot/README.md).
- **`mcb.game.Oracle`, the placement rules.** "What would this click produce?", answered with the game's own code
  (`mcb.env.ServerOracle`, `mcbc.ClientOracle`). Useful on its own for anything that places blocks in a specific
  state. See [adapter/README.md](../adapter/README.md).
- **`mcb.bot.Nav`, route search.** A weighted A* search over a player's real moves: walking, jumping, dropping,
  sprinting, ladders, scaffolding, doors and gates, and temporary blocks where allowed. It runs over `Grid`, the
  bot's picture of the area, and needs no game. See [core/src/mcb/bot/README.md](../core/src/mcb/bot/README.md).
- **`mcb.build.Judge`, the self-check.** It compares a world region with a schematic section by section. It's
  useful for any "is it built right?" check, whoever built it, and needs no game. See
  [core/src/mcb/build/README.md](../core/src/mcb/build/README.md).
- **`mcb.schem`, the readers.** These read `.litematic`, Sponge `.schem` (v1–3), structure `.nbt` and old
  `.schematic` files into one normal form. They need nothing but a JDK; see
  [core/src/mcb/schem/README.md](../core/src/mcb/schem/README.md).

The block facts the planner and the judge read come from tables (`mcb.build.StateInfo`) that the adapter fills
from the game; tests and examples fill them from a small made-up palette instead (`SimplePalette`).

**Entry point.** Both mods wire the planner up the same way:
`new Builder(client, grid, site, oracle, rules).run()` blocks until the build is done or stopped. `Grid` takes the
box, `Site` takes the target and the supplies, `Target` normalises the schematic, and `Rules` says what the world is
like (`Rules.headless()` or `Rules.client(maxFall16)`). Read `adapter/builder/src/mcb/mod/Job.java` (singleplayer),
`adapter/client/src/mcbc/Job.java` (client) or `adapter/headless/src/mcb/headless/BuildRun.java` (headless) for the
full setup.

**For the uses people have asked about:**
- **A companion bot that plays alongside you.** The planner assumes it owns the build box. It needs a lock or
  claim on cells you might change, and a way to hand control back and forth. The client mod's pause-on-input is
  the start of that. Following, fighting and chatting are not here.
- **An autonomous bot on an anarchy server.** Building is the solid part. Everything around it is missing:
  - eating, gathering, crafting, and moving supplies around in shulkers;
  - terrain it can't see or doesn't know about;
  - griefers breaking the build or emptying the containers;
  - other anticheats (it was tested against GrimAC only);
  - servers below 20 TPS;
  - recovering after disconnects.

  Today it stops at the first sign of trouble, by design.
- **Scripted building on servers that allow it.** The client mod is the closest fit. It plays within vanilla
  movement and reach, and stops when anything looks wrong. Keep Timer off, register supplies, and expect 2–3×
  the singleplayer bot's ticks per block.
- **Roleplay or NPC builders.** For NPCs, the singleplayer mod's server-side bot player is the simpler body. It
  already runs inside the server and needs no client. You would add your own commands, a way to queue builds, and
  your own rules for when it may build.
- **Research on build planners.** The method in [METHOD.md](METHOD.md), especially how changes were judged, is
  probably the most reusable part. The benchmark itself needs the simulator and a corpus.

## Things it can't do (as of 1.0.0)

- **Redstone builds.** Machines fire while they're built (pistons, observers, flying machines).
- **Blocks with no placeable face.** Blocks whose only usable face is hidden by the time their turn comes.
- **Lily pads.** Their water is dropped from the target.
- **Open iron doors and trapdoors.** It can't open iron by hand.
- **Huge builds.** Builds over about 300k blocks: 2 of 16 pass on the goal sample, mostly because they run out of
  time.
- **Speed.** It's no speed-builder: about 5–25 ticks per block. Speed was deliberately not the goal; see
  [METHOD.md](METHOD.md).
