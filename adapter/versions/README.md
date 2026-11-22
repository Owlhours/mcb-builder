# Minecraft versions

The planner (`core/`) never sees the game. Everything that touches Minecraft is in `adapter/`, and the adapter is
compiled against one Minecraft version at a time, chosen by `MC=<version>` (default 26.2):

```
MC=26.3 builder/build.sh        MC=26.3 client/build.sh        MC=26.3 gate/gate.sh
```

## How versions are organised

| What | Where |
| --- | --- |
| What to download and its SHA-1s (server, client, Fabric Loader, Mixin, Fabric API) | `adapter/versions/<mc>.env`, read by `tools/deps.sh` |
| Code that differs between versions | small source overlays: `adapter/versions/<mc>/src` (code both mods share), `adapter/versions/<mc>/server/src` (the server-side body: singleplayer mod, headless runs), `adapter/versions/<mc>/client/src` (the client mod) |
| The gate's expected results | `gate/expected/<mc>.tsv` |

Every version's overlay holds **the same class names with the same members**, each with that version's
implementation; the shared adapter code calls them. The build scripts add the overlays of `$MC` that exist to their
source roots (`version_src` in `tools/deps.sh`). An overlay holds only what cannot be written once for every version:

- a difference that is only a constant present in both versions is written with that constant
  (`InputConstants.KEY_K`, `SharedConstants.WORLD_VERSION`);
- a difference that a version-neutral form covers is written in that form (a bed's head is found by its `part`
  property, not by its class);
- the rest goes into the overlay, and the shared code says where versions part ways.

Today there are two overlay classes:

- `mcb.env.ClientProtocol` (`adapter/versions/<mc>/server/src`): what the vanilla client sends where `BotClient`'s
  transcription of it differs between 26.2 and 26.3;
- `mcb.mc.StateTags` (`adapter/versions/<mc>/src`): the NBT keys of a block state (26.2 `Name`/`Properties`, 26.3
  `id`/`properties`), for `GameStates`.

Their boolean constants are compile-time constants, so the 26.2 build carries none of the 26.3 code paths.

## What was different for 26.3, and how it was found

The server-side body (`BotClient`, `BotPlayer`) transcribes the vanilla client, so for 26.3 it has to transcribe
the 26.3 client. The two client jars and server jars were decompiled locally (outside the repository, for reading
only) and the methods the adapter transcribes were compared one by one; `javap` checked the members that the
compiler does not check (mixin targets, reflection).

| Client code path | 26.3 compared with 26.2 | In the adapter |
| --- | --- | --- |
| `Minecraft.startAttack`, `continueAttack` | the swing is local (`swing(hand, animation, false)`), then `ServerboundPunchPacket.INSTANCE` is sent; `ServerboundSwingPacket` is gone. Same places, same conditions | `ClientProtocol.attackSwing` |
| `Minecraft.startUseItem` (on a block) | the swing is local only (when the result's swing source is `PREDICTED`); no packet. The server swings in `handleUseItemOn` | `ClientProtocol.useSwing` (26.3: nothing) |
| `Minecraft.handleKeybinds`, `tick`, `pick` | unchanged for the bot (dropping items moved to `MultiPlayerGameMode.dropItem`; the bot never drops). `LocalPlayer.sendChanges` (input and position packets) now runs from `Minecraft.tick` after the entities ticked, still after the player's tick | nothing |
| `MultiPlayerGameMode.startDestroyBlock`, `continueDestroyBlock` | a new `destroyDirection`: set when a break starts, cleared when the block breaks; a tick that does not finish the block sends `CHANGE_DESTROY_DIRECTION` when the face changed (the server uses it only for particles). The hit sound moved out of the client | `ClientProtocol.REPORTS_BREAK_FACE`, `breakFaceChanged` |
| `stopDestroyBlock`, `useItemOn`, `ensureHasSentCarriedItem`, `handleContainerInput`, `destroyBlock` | unchanged (`useItemOn`'s prediction of the held item changed on the client only) | nothing |
| `ClientPacketListener.handleMovePlayer` | the teleport confirmation carries the position and rotation after the teleport (26.2: the id, then a separate move packet), and the client then stops any block break | `ClientProtocol.acceptTeleport`, `STOP_BREAK_ON_TELEPORT` |
| chunk batch acknowledgement | unchanged | nothing |
| `LocalPlayer.aiStep`, `applyInput`, `modifyInput`, sprint and crouch rules; `KeyboardInput`, `ClientInput` | unchanged, except that the push out of blocks now also requires "not a passenger" (the body never rides) | nothing |

On the server side, the handlers the body calls are the same except `handlePunch` (new; it also resets the attack
strength), `handleAcceptTeleportPacket` (it now applies the reported position like a move packet, with
`onGround = false`) and `handlePlayerAction` (the new action; an `ABORT_DESTROY_BLOCK` from out of reach now aborts
the server's break too). Those are the game's own rules and are not copied. One consequence needed care: the
accepted position goes through the "moved too quickly" check, which measures from where the player stood when its
last server tick began (`resetPosition`, run by `tickPlayer`). A real client's answer always comes after such a tick;
the bot's connection is never ticked by the server and the bot answers at once, so the 26.3 `acceptTeleport` calls
`resetPosition()` first. Without it, the first long teleport was answered with another teleport, forever.

Elsewhere in the adapter:

- **block facts**: 26.3 adds 3,357 block states (poplar wood, wool and concrete stairs and slabs, the straw bed,
  shelf mushroom, red shrub); no new property names. Every state that both versions have gets the same facts, except
  the dragon head's outline (the game changed it). The straw bed is not a `BedBlock`, so `BlockFacts` now finds a
  bed's head by its `part` property (the 26.2 tables are unchanged by this, checked by dumping them).
- **block states as NBT**: 26.3 renamed the keys `NbtUtils.readBlockState` reads from `Name`/`Properties` to
  `id`/`properties` (the data fixer's `BlockStateFieldNamesFix` renames them when it upgrades older states). Schematic
  palettes and the gate's block specs use the old keys; `GameStates` gives a state at the current data version the
  current keys (`StateTags`). Without this every block resolved to air in 26.3.
- **schematic upgrades**: `GameStates.CURRENT` (the data version schematics are upgraded to) is now
  `SharedConstants.WORLD_VERSION` (26.2: 4903, 26.3: 5023) instead of a literal 4903.
- **client mod**: the pause key is `InputConstants.KEY_K` (26.3 moved from GLFW to SDL; the constant is 75 in 26.2
  as before).
- **mixins and reflection**: every `@Inject`, `@Accessor`, `@Invoker` and `@ModifyArg` target, and every member reached
  by reflection (`Env`, `LiveLoop`, `ServerOracle`, `ClientOracle`), exists with the same signature in 26.3;
  `MinecraftServer`'s loop, which `LiveLoop` follows, is unchanged.

## The gate on 26.3

`gate/expected/26.3.tsv`: 31 of 31 builds pass the judge, as on 26.2. 29 rows are identical to 26.2's. Two differ,
`rubble` (2937 ticks, 201 placed, 24 scaffold on 26.2; 2219, 186, 12 on 26.3) and `stairs` (2478, 208, 40; 1896,
200, 32). The cause is the game, not the bot: 26.3's game-test server makes its flat world with another preset
(`FLAT_ALL_DIMENSIONS`), whose ground is at y = 4 instead of y = -60. A tick-by-tick trace of the player shows both
versions playing exactly the same course, x and z equal to the bit and y exactly 64 higher, until a jump in which the
position's rounding at the other height differs by 7e-15 (tick 191 of `stairs`, 264 of `rubble`); a few hundred ticks
later that has become a different decision. The other 29 builds never reach such a point.

## Adding the next version

1. Write `adapter/versions/<new>.env` with the new pins (the version manifest gives the server and client jars and
   their SHA-1s; Fabric API's version for it from maven.fabricmc.net).
2. Copy the newest overlay directory to `adapter/versions/<new>/` and build: `MC=<new> builder/build.sh`,
   `MC=<new> client/build.sh`. Compile errors show what moved.
3. Compare, between the two versions' client jars, every vanilla method the adapter transcribes (listed in the
   table above and in the class comments of `BotClient` and `BotPlayer`), and the server handlers `BotClient`
   calls; put what differs into the overlay. Check the mixin targets and reflected members with `javap`: the
   compiler does not check those strings.
4. Look at the block facts (a new block class or property may need a rule in `BlockFacts`; dumping the tables of
   both versions and comparing them by state name shows it) and at how block states are read (`GameStates`: the
   data fixer, the NBT keys).
5. Record the gate: `MC=<new> gate/gate.sh --record`, compare its rows with the previous version's build by build,
   and explain the differences. Add the version to the CI matrix (`.github/workflows/ci.yml`).
