# adapter: everything that touches Minecraft

The planner (`core/`) imports nothing from Minecraft. Everything that does is here, so moving to another game
version touches only this folder (see [versions/](versions/)).

| folder | package | what | used by |
|---|---|---|---|
| `src/` | `mcb.mc` | the game ↔ planner bridge both mods share: `BlockFacts` (fills the planner's block and item tables from the game), `GameStates` (schematic block states → the game's state ids, with its data fixers), `GameSurvival` (the judge's "can it stay here?"), `Convert` | everything |
| `server/src/` | `mcb.env` | the server-side body: `Env` (the vanilla server in-process, or the integrated server's own loop), `BotPlayer` (a server-side player moved by the vanilla physics), `BotClient` (the planner's `Client` on it: keys, view, clicks through the server's real packet handlers), `ServerOracle`, `Depot` (the supply chests a depot site sets up) | the singleplayer mod, the headless runner |
| `headless/src/` | `mcb.headless` | one build start to verdict (`BuildRun`), the regression gate's runner (`Gate`), the client mod's settings headless (`ClientRun`, `ClientGate`), `OracleExample` | `gate/gate.sh`, `tools/client-check.sh`, the harness in mcb-sim |
| `builder/` | `mcb.mod` | the singleplayer Fabric mod: commands, the server-tick mixin, the job | `builder/build.sh` |
| `client/` | `mcbc` | the client Fabric mod: `LocalClient` (the planner's `Client` on your own player), `ClientOracle`, safety stops, commands, mixins | `client/build.sh` |
| `versions/` | | the pins of each Minecraft version (`<mc>.env`) | `tools/deps.sh` |

## The placement oracle on its own

"If I click there, holding this, looking that way, what appears?" — answered by the game's own placement code
(`BlockItem.updatePlacementContext` / `getPlacementState`, the steps `BlockItem.place` takes before it writes the
block), without placing anything. The API is `mcb.game.Oracle` (core); `mcb.env.ServerOracle` answers on a server
level, `mcbc.ClientOracle` on the client's world.

```java
Env env = Env.boot(Files.createTempDirectory("w"));       // or wrap your own server level (new Env(server, level, null))
StateInfo.init();
ServerOracle oracle = new ServerOracle(env);
int st = oracle.predict(itemId, new Hit(px, py, pz, Hit.UP, x, y, z, false), yaw, pitch, sneak);
StateInfo.name(st);                                         // "oak_stairs[facing=south,half=bottom,...]"; 0 = nothing
oracle.placedX; oracle.placedY; oracle.placedZ;            // where it would appear
char[] table = oracle.table(itemId);                        // every face x part of the face x 32 yaws x 9 pitches x sneak,
table[Oracle.index(face, quad, Oracle.yawClass(yaw), Oracle.pitchClass(pitch), sneak)];   // against a lone block
```

`OracleExample` runs this for one item: after `gate/gate.sh` (which compiles the headless classes into
`build/headless`),

```bash
. tools/deps.sh && server_cp
java -cp build/headless:$SERVER_CP mcb.headless.OracleExample oak_stairs
#   Clicking the top face of a lone block, holding oak_stairs, not sneaking:
#     looking north: oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false] ...
```

## Rules for code here

- Code here may use anything of the game's; code in `core/` may not (`tools/check-core.sh` checks it, and CI runs it).
- `BotPlayer` and `BotClient` transcribe the vanilla client (`LocalPlayer`, `KeyboardInput`, `Minecraft.startAttack`
  and friends, `MultiPlayerGameMode`). Each transcribed method names the vanilla method it follows; when the game
  changes one of them, the transcription changes with it, in this folder only.
- Nothing from Mojang is in this repository: the build scripts download the game's jars (SHA-1 pinned) and use them
  only to compile and, for the headless runner, to run.
