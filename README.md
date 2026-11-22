# MCB Builder

A Minecraft 26.2 bot that builds schematics the way a patient survival player would, unattended, from empty
ground to a finished, cleaned-up build.

Hand it a `.litematic`, `.schem`, `.nbt` or `.schematic`. It walks, jumps, climbs and sneaks, and never flies
or airplaces. It places and breaks only within survival reach, and acts only through keys and mouse clicks.

- **Correct block states:** every click is checked against Minecraft's own placement code first, so stairs,
  slabs, logs, doors and trapdoors come out facing the right way.
- **Its own scaffolding:** pillars and bridges to reach high or overhanging parts, taken down at the end.
- **Never stuck for long:** it keeps a way back to its supplies, and digs itself out if it gets boxed in.
- **Restocking:** it plans trips to the supply containers for the blocks it needs next.
- **Self-check:** it judges the result against the schematic, section by section.

In testing it completed 963 of 1,000 randomly drawn builds, and 89 % of builds over 10,000 blocks. The sample
left out builds with redstone components and builds with blocks a survival player can't place; on an unfiltered
sample it was 929 / 1,000. These figures come from a simulator that matched the real game tick for tick on the
builds checked in both. See [docs/METHOD.md](docs/METHOD.md).

## Two mods

| | for | what it drives |
|---|---|---|
| [**builder**](builder/README.md) (`mcb-builder`) | singleplayer test worlds | a bot player named Builder, next to supply chests the mod sets up and fills for you |
| [**client**](client/README.md) (`mcb-client`) | your own character, on a server | your player, with supplies from containers you register, and safety stops that hand control back to you |

Both need Minecraft 26.2, Fabric Loader 0.19.5 or newer, and Java 25. The client mod also needs Fabric API.
Each folder has its own `build.sh`, which downloads its compile dependencies from Mojang and the Fabric Maven.

## Documentation

| | |
|---|---|
| [docs/METHOD.md](docs/METHOD.md) | how the planner works, the judge, the world rules, and how every number was measured |
| [docs/BUILDING-ON-IT.md](docs/BUILDING-ON-IT.md) | **read before reusing it:** how it was written, its technical debts and assumptions, the seams for your own project (companion bots, autonomous bots, server scripting, NPCs) |
| [docs/HISTORY.md](docs/HISTORY.md) | the timeline, how to read the git log, and a glossary of the named revisions and their `MCB_NO_*` switches |
| [prompts.md](prompts.md) | what the owner asked for, in their own words, from the start of the clean-room attempt to release (shared with mcb-sim) |

The code was written by Claude agents in an automated loop against a fixed benchmark, with a person setting the
goals and making the calls. The commit history is the real development history.

## License

MIT. See [LICENSE](LICENSE).

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
