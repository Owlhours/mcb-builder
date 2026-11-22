# MCB Builder (singleplayer)

A Fabric mod for Minecraft 26.2 that builds a schematic for you in survival style, unattended, from empty
ground to a finished, cleaned-up build.

Give it a `.litematic`, `.schem`, structure `.nbt` or old `.schematic`. A bot player named **Builder** appears
next to a row of supply chests and builds it like a patient survival player would. It walks, jumps, climbs and
falls. It never flies or airplaces, and it places and breaks only within normal survival reach.

## What it does

- **It plays by the game's rules.** The bot acts only through a keyboard-and-mouse interface: movement keys,
  jump, sneak, look, left and right click. Its clicks go through the server's own packet handlers, so reach,
  placement rules and break times are the game's own.
- **It gets block states right.** It checks every click against Minecraft's own placement code before it makes
  it. Stairs and slabs face the right way, logs lie on the right axis, and doors, trapdoors and blocks that need
  support go in correctly.
- **It builds its own scaffolding.** It puts up pillars and bridges to reach high or overhanging parts, then
  takes them down and leaves the site clean.
- **It doesn't trap itself.** It checks that it can always get back to the chests. If it does get stuck, it digs
  out and patches the hole.
- **It restocks itself.** It plans trips to the supply chests for the blocks it needs next, and puts back what
  it no longer needs.
- **It checks its own work.** `/mcb judge` compares the world with the schematic in 16×16×16 sections. A
  section passes with at most 3 % wrong cells, and a build passes when every section passes.

**In testing:** the bot completed 963 of 1,000 randomly drawn builds, and 89 % of the builds over 10,000
blocks. That sample left out builds with redstone components and builds with blocks a survival player can't
place; an unfiltered sample gave 929 / 1,000. These numbers come from a simulator, which matched the real game
tick for tick on the builds that were checked in both. See [../docs/METHOD.md](../docs/METHOD.md).

## Install

1. Install Minecraft **26.2** with Fabric Loader **0.19.5** or newer (for example, in Prism Launcher). Java 25 is
   required. No Fabric API is needed.
2. Put `mcb-builder-<version>.jar` in the instance's `mods` folder.
3. Make a `schematics` folder in the game folder, next to `mods`, and put your schematics in it.

## Use it

Create a **new Superflat world** with **Allow Commands on**, and set render distance to 12 or more.

| command | what it does |
|---|---|
| `/mcb list` | lists the schematics in `.minecraft/schematics/` |
| `/mcb build <name> [x y z]` | builds `<name>`. With `x y z`, the schematic's corner goes there, with `y` the first layer above the ground. Without them, the site starts 3 blocks in front of you. |
| `/mcb pause` / `/mcb resume` | pauses and resumes the bot |
| `/mcb stop` | ends the build and leaves everything as it is |
| `/mcb status` | the phase, cells done, blocks placed, scaffolding standing, ticks so far |
| `/mcb judge` | PASS or FAIL, with the failing sections and their most common wrong cells |

**The first `/mcb build` changes nothing.** It shows the size of the box: the build, the supply chests and
the margins the bot walks in. Run the same command again within 10 seconds to start.

When the build starts, the mod:
- clears the box to air over a grass floor, without drops (nothing outside the box is changed);
- places the supply chests and fills them with everything the schematic needs, plus scaffolding blocks;
- sets these world rules: Peaceful, block drops off, fall damage off, random ticks 0, daylight, weather and mob
  spawning off. They stay set in that world, so **use a test world.**

**Speed.** The bot takes about 5 to 25 game ticks per block. A small house takes about 5 minutes, and a
30,000-block build a few hours. `/tick rate 100` runs the game 5 times faster if your computer keeps up, and
`/tick sprint <ticks>` runs it as fast as it can.

## Limits

- **Singleplayer test worlds only.** The bot is a server-side player in your own world, in Peaceful with fall
  damage off. To build with your own character on a server, use the [client mod](../client/README.md).
- **Its supplies are free.** The mod fills the chests for it. It doesn't gather, craft or ferry materials.
- **No redstone.** Redstone in a schematic is live while it's built, so pistons may fire and lamps light.
- **Flat ground.** The bot assumes everything outside its box is air above a flat floor. Use Superflat.
- **One build at a time.** Saving and quitting stops the build, and it doesn't resume.
- Builds take hours of game time when they're large. There is a 48-hour game-time limit per build.

## Build from source

```
cd builder
./build.sh            # -> builder/build/mcb-builder-1.0.0.jar
```

You need a JDK 25, `curl` and `unzip`. Minecraft 26.2 is not obfuscated, so the mod compiles directly against
the vanilla server classes, with no Gradle and no remapping. On the first run the script downloads the vanilla
server jar from Mojang, plus Fabric Loader and Mixin from the Fabric Maven, into `builder/.deps/`, and checks their
SHA-1s. None of those files are in this repository.

### Layout

| path | what |
|---|---|
| `src/mcb/bot` | the bot: build order, stances, movement, route search, scaffolding, supplies |
| `src/mcb/env` | the keyboard-and-mouse client interface, the bot player, and the placement oracle |
| `src/mcb/build` | the build site, the target, the judge |
| `src/mcb/schem` | schematic readers (`.litematic`, `.schem`, `.nbt`, `.schematic`) |
| `src/mcb/mod` | the Fabric entry point, commands, and the tick-loop mixins |

Some features can be switched off with `MCB_NO_*` environment variables, for comparing behaviour. They are all
on by default.

## License

MIT. See [LICENSE](../LICENSE).

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
