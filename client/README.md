# MCB Builder (client)

A Fabric **client** mod for Minecraft 26.2 that builds a schematic with **your own player** on a server, the way
a patient survival player would. It walks, jumps, climbs and sneaks. It puts up and takes down its own
scaffolding, places and breaks blocks within reach, and takes its blocks from containers you point it at.

It uses the same planner as the [singleplayer mod](../builder/README.md), but drives the real client instead of
a server-side bot player.

It stops and hands control back to you on any of these: damage, low health or food, a hostile mob or another
player nearby, lava or fire nearby, a fall, a burst of server setbacks, or any key you press.

## Install

Put `mcb-client-<version>.jar` in the `mods` folder of a Minecraft 26.2 instance with Fabric Loader 0.19.5 or
newer and **Fabric API**. Java 25 is required. Meteor, Baritone and Litematica are optional; see below.

Put your schematics (`.schem`, `.litematic`, `.nbt`, `.schematic`) in `.minecraft/schematics/`.

## Use

1. **Register supply containers.** Stand by your chests, barrels or placed shulker boxes and run
   `/mcbc supply here`, which registers every container within 4 blocks (`/mcbc supply here 8` reaches further).
   You can also look at one and run `/mcbc supply add`. `/mcbc supply list` and `/mcbc supply clear` also exist.
   With no containers, the bot uses only what you carry.
2. **Fill them,** or your inventory, with what the build needs. Add **32 or more temporary blocks** of a kind the
   build doesn't use (moss, dirt, netherrack or cobblestone), and **16 or more scaffolding**. The preview lists
   what's needed. These are the least it starts with: tall builds use up many more temporary blocks (a few stacks
   for a tower of glass), because the blocks it takes down again are not always picked up.
3. **Run `/mcbc build <name>`.** The build goes 3 blocks in front of you, at your feet's height.
   - `/mcbc build <name> x y z` puts the schematic's origin at x y z, as Litematica and Baritone place it (y is the
     first layer above the ground for a file whose bottom layer has blocks). Empty layers at the bottom of the file
     are kept, so a floating build floats at its height in the file.
   - `/mcbc build litematica` uses the placement selected in Litematica (unrotated, unmirrored, saved to a file).
4. **The first `/mcbc build` is a dry run.** It changes nothing. For a minute it shows particles for:
   - the build box (green) and the area the bot works in (yellow);
   - the supply containers (blue);
   - every block it would break (red) and blocks it can't break by hand (purple).

   In chat it lists what's in the box, the materials needed against what you have, unloaded chunks, the safety
   settings and the Timer state. `/mcbc preview <name> [x y z]` does only this.
5. **Run the same `/mcbc build` again within 60 s to start.** The bot reads each container, checks that it has
   everything (if not, it says what's missing and doesn't start), clears what's in the way inside the box, and
   builds. At the end it judges the box and says PASS or FAIL.

| command | what it does |
|---|---|
| `/mcbc pause`, **K**, any movement, jump, sneak, attack, use, inventory or hotbar key, or turning the view | pauses at once and gives you control |
| `/mcbc resume` (or K again) | carries on, once the chat is closed |
| `/mcbc stop` | ends the build; what stands stays |
| `/mcbc status` | phase, blocks placed, ticks per block, setbacks, Timer state |
| `/mcbc judge` | judges the box as your client sees it |
| `/mcbc list`, `/mcbc reload` | lists schematics; re-reads `config/mcb-client.properties` |

Opening the chat pauses it, and it carries on by itself when the chat closes. If it runs out of a material
mid-build, it first reads the supply containers again, then, if there really is none left, pauses and says what it
needs. Fill a container and run `/mcbc resume`. The same goes for temporary blocks: without them it cannot reach
cells high up, so when they run out (or it cannot get down to the containers without them) it pauses and asks for
more of the kind it started with (it names it). `/mcbc resume` without adding any carries on and skips those cells; a build that ends with cells missing
says why they were skipped.

**If you die,** the build pauses and waits. After you respawn (in the same dimension), `/mcbc resume` starts it
again where it stands: the bot reads the site and the containers again and builds what is still missing. Walk back
near the site first if you respawned far away; it says so if parts of the site are not loaded. What you dropped is
not picked up, so the containers may now run short; it tells you, as at any start. With `autoResumeAfterDeath` on,
it starts again by itself once you are healthy and nothing hostile is near.

## Settings (`config/mcb-client.properties`)

| setting | default | what |
|---|---|---|
| `hostileRadius` | 16 | pause when a hostile mob is this close |
| `playerRadius` | 64 | pause when another player is this close (0 = off) |
| `hazardRadius` | 4 | pause near lava or fire |
| `minHealth`, `minFood` | 14, 6 | pause below these |
| `stopOnDamage` | true | pause when hurt |
| `pauseOnOwnerInput` | true | pause on your own key presses |
| `maxThinkMs` | 8000 | the longest the planner may think in one go |
| `maxClearTicks` | 200 | blocks slower than this to break by hand are left |
| `maxFall16` | 56 | the longest planned drop in sixteenths of a block (3.5 blocks: no fall damage) |
| `meteorOff` | (list) | Meteor modules switched off for the run and back on afterwards |
| `autoResumeAfterDeath` | false | after a death and respawn, start the build again by itself (otherwise: `/mcbc resume`) |

## What the server sees

- **One ordinary vanilla client.** The bot sets the view angles on your player and replaces the keyboard's
  movement input with its own key states. It clicks through the vanilla use and attack calls, at the point in
  the tick where keyboard clicks are handled. No packet is made by hand.
- **It clicks only with a view the server already has.** When the angles change, it turns the view for a tick
  first. It drops the click if the crosshair is no longer on the face it meant. The yaw always turns the short
  way round, and it makes at most one use per tick.
- **It waits for the server's answer** after each placement or finished break, so a rejected placement is seen
  as missing and tried again.
- **It stays within reach.** It uses no more than the vanilla interaction range, clicks only a face the
  crosshair ray hits, and never places against air.
- **Its picture of the world is what the server sent.** Anything unknown counts as solid.
- **It breaks only inside the build box,** plus its own temporary blocks. It never breaks your containers or
  anything outside the box.
- **Lockstep.** While the bot thinks, the game waits. After a long think the client doesn't catch up with a burst
  of ticks.

## Meteor, Timer, Baritone

- **Meteor:** modules that would fight the bot for the hotbar, the mouse or the break timing are switched off
  for the run and on again after. Examples are auto-tool, auto-eat, speed-mine and nuker; the list is
  `meteorOff`.
- **Timer:** left as you set it. The preview and `/mcbc status` say whether it's on. **Run builds with Timer
  off.** In testing against GrimAC, a fast Timer got the player kicked within seconds, with or without the bot.
- **Baritone:** cancelled at the start, and again whenever it starts pathing during a run.

## Testing

It was tested on a local Paper 26.2 server with GrimAC 2.3.74, in survival with fall damage and drops on,
Timer off:

| build | blocks | result | setbacks | Grim flags | ticks per block |
|---|---|---|---|---|---|
| small spruce cottage | 337 | PASS | 0 | 0 | 16.3 |
| small stone tower | 346 | PASS | 0 | 0 | 35.9 |
| birch house with garden | 935 | PASS | 0 | 0 | 13.1 |
| timber-frame house | 3,381 | PASS | 0 | 23 (below the setback threshold) | 29.8 |

None of the runs changed anything outside the box. It hasn't been tested on public servers.

**Known limits:**
- It's 2–3× slower than the singleplayer bot. Most of the time is walking, and breaks are paced to stay clear of
  fast-break checks.
- It doesn't eat. It pauses when food runs low.
- Sharp turns mid-jump can still raise movement flags on strict anticheats.
- Any player in range pauses it, by design.
- Break timing assumes a server at 20 TPS.
- No redstone: redstone is live while it's built.

## Build from source

```
cd client
./build.sh            # -> client/build/mcb-client-0.2.0.jar
```

You need a JDK 25, `curl`, `unzip` and `python3`. The script downloads into `.deps/` at the top of the repository
the vanilla 26.2 client jar and its libraries from Mojang, plus Fabric Loader, Mixin and Fabric API from the
Fabric Maven. It checks each against a pinned SHA-1 (`adapter/versions/26.2.env`). None of those files are in this
repository.

| path | what |
|---|---|
| `../core/` | the planner, the same code as the singleplayer mod's; the client's world is described by `mcb.bot.Rules.client` (fall-damage-safe drops, only its own blocks broken outside the box, the world re-read, the timed climb out of scaffolding) and its registered containers by `Site` |
| `../adapter/src/mcb/mc` | the bridge to the game both mods share |
| `../adapter/client/src/mcbc/` | the client mod: `LocalClient` and `ClientOracle` (the planner's body and oracle on your own player), commands, safety checks, the lockstep driver, mixins |

The client planner's behaviour is checked headless, without a Minecraft client, by `../tools/client-check.sh`.

## License

MIT. See [LICENSE](../LICENSE).
