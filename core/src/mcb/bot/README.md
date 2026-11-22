# mcb.bot: the planner, and the route search

Plain Java (no Minecraft; fastutil is its one library). The planner decides everything the bot does; it acts
only through an `mcb.game.Client` (keys, view, clicks, slots) and asks an `mcb.game.Oracle` what a click would
place. How it works is in [docs/METHOD.md](../../../../docs/METHOD.md) §2.

## The pieces

| class | what |
|---|---|
| `Grid` | the bot's picture of the site: the believed state of every cell, the target, and what stands where (`stand16`, `hang`, `passable`) |
| `Nav` | the route search: a weighted A* over the places a player can stand, with the moves a player has |
| `Ray` | line of sight on the grid (which face a ray from the eye meets first) |
| `Body` | walking, aiming, clicking and holding items, on a `Client` |
| `BuildState` → `Moves` → `Placing` → `Stances` → `Scaffolds` → `Supplies` → `Builder` | the planner proper, one concern each (shared state; routes and travel; one block into one cell; where to stand; temporary blocks; supply trips; order of work and the main loop) |
| `Rules` | what the world is like: fall damage, whose blocks are around, whether the world is re-read (the singleplayer mod's and the client mod's worlds differ here) |

## The route search on its own

```java
SimplePalette.install();                    // or the game's tables (mcb.mc.BlockFacts.install())
Grid g = new Grid(ox, oy, oz, sx, sy, sz, targetIds, marginX, marginNorth, marginSouth, marginTop, groundState);
g.set(x, y, z, stateId);                    // what stands where (the bot keeps this up to date from block changes)
Nav nav = new Nav(g, new IntOpenHashSet()); // the second argument: cells of the bot's own temporary blocks
nav.towersOn = false;                       // no scaffolding columns
boolean found = nav.search(sx, sy, sz, gx, gy, gz, maxExpand, (x, y, z) -> allowTemporaryBlockHere);
for (int k = 0; k < nav.length; k++) { nav.path[3 * k] ...; nav.move[k] ...; }   // Nav.WALK, JUMP, DROP, PILLAR, BRIDGE ...
```

Moves: walking, sprinting, jumping, dropping (capped by `maxFall16` when fall damage is on), climbing ladders and
scaffolding, opening doors and gates, and — where the rule passed to `search` allows — pillaring and bridging with
temporary blocks, putting up scaffolding columns, and mining through. Costs are in the `C_*` constants. Routes are
good, not guaranteed shortest (the heuristic is weighted). A search has three outcomes: found, `exhausted` (no way),
or the budget ran out (`!found && !exhausted`); the planner treats the last as "don't know", never as "no way".

`core/examples/mcb/example/NavExample.java` searches a site with a wall, once with steps up the wall and once
without (then it pillars):

```bash
javac -cp fastutil-8.5.18.jar -d out $(find core/src core/examples -name '*.java')
java -cp fastutil-8.5.18.jar:out mcb.example.NavExample
#   with the steps: route found, cost 62, 11 places
#     ... jump -> 2,2,5   jump -> 3,3,5   jump -> 3,4,4 ...
```

## The planner in another body

`new Builder(client, grid, site, oracle, rules).run()` builds until done or stopped. To put the planner in a
different body (another client framework, a protocol client, an NPC), implement `mcb.game.Client` and
`mcb.game.Oracle`; the two that ship are `mcb.env.BotClient`/`ServerOracle` (a server-side player) and
`mcbc.LocalClient`/`ClientOracle` (the player's own client), in `adapter/`. Read
[docs/BUILDING-ON-IT.md](../../../../docs/BUILDING-ON-IT.md) first: the planner's rules were tuned on a benchmark,
and their switches (`MCB_NO_*`) are part of how it was measured.

**Safe to change?** Run `gate/gate.sh` (31 generated test builds, every tick and count compared) and, for the
client mod's settings, `tools/client-check.sh`. Both run in CI.
