# History

How the bot got from nothing to 963 / 1,000 in ten days, what each named revision does, and how to read the git
log.

## The git history

The commits in this repository are the real development history, filtered down to the files published here.
- **`builder/`:** 88 commits from 2026-11-12 to 11-21, by the development loop (author `mcb <mcb@localhost>`).
  In development the bot lived in `src/mcb/` and the mod in `mod/`; the history shows them already moved to
  `builder/`.
- **`client/`:** 3 commits on 2026-11-21. In the client's development repository, a `.gitignore` line (`build/`)
  also ignored `src/mcb/build/`, so those four files (`Judge`, `Site`, `StateInfo`, `Target`) first appear in the
  final commit. The client couldn't be built from its own history before that.
- **The last commit** adds this documentation, the build scripts that download their dependencies, the license and
  the READMEs.

Commits that changed only files not published here (the simulator, the evaluation scripts, result files and
status notes) were dropped, so many messages mention things that aren't in this repository. Messages ending
`(no change to the bot)` were about those. Short hashes in messages, such as `2d9a193` and `covertour`, refer to the
development repository and the simulator's revision names; see the glossary below.

## Timeline

| date | what happened | measure |
|---|---|---|
| 11-12 | The environment: vanilla 26.2 server in-process, a client-faithful bot player, the keyboard-and-mouse interface. Schematic readers, judge, site with supply chests, the first working builder. Then, the same day: placement tables and the rules oracle, stance planning, scaffolding columns, doors, trap avoidance, restocking, clean-up. | 24 / 40 on the first sample |
| 11-13 | A from-scratch run of one commit on 320 held-out builds gave the first trustworthy number. The evaluation method was rewritten after measuring chance directly ([METHOD.md](METHOD.md) §5). Batches accepted: safer clean-up, the witness way out, building inside, fences. The Rust simulator became the inner loop. | holdout 120 / 150 |
| 11-13–14 | Simulator revisions `mend`, `orphan`, `close`, `chain`, `careful`. The judge was frozen, with operator-only blocks exempt. Then `fewfloat` and `escape`. | first goal sample: 896 / 1,000 |
| 11-16–17 | Route search made cheaper without changing results: `askonce`, `plankeep`, `thoughtway`. Search work ×0.58. Decisions moved to 4–6 seeds. | — |
| 11-18 | Work on large builds: the repair pass (`repair`), then `ropefar`, `excavate`, `minefence`, `lastbreak`, `samefail`, `weblate`. | 918 / 1,000, 10k+ 230 / 297 |
| 11-19–20 | The impossible-cell judge rule. Then `nearclean`, `hungtower`, `magmasneak`, `nostrand`, `dearbreak`, `nostand`, `walkway`. | 927–930 / 1,000 |
| 11-21 | `covertour`. The first goal sample closed at 929. The second sample (no redstone, no impossible cells, fresh builds) was drawn and measured once. The final Java-versus-simulator check: 39 of 40 builds took the same course. The singleplayer Fabric mod was released. | **963 / 1,000**, 10k+ 265 / 297 |
| 11-21 | The client mod: the planner driving the real player, tested against GrimAC on a local server. Fixes for climbing out of scaffolding columns, fall-safe digging and sign editors. | 4 of 4 test builds pass, 0 setbacks |

Goal-sample numbers moved partly through bot changes and partly through judge decisions. For example, 918 → 921
came from the second form of the impossible-cell rule, and 928 → 929 from the bamboo rule. Each commit message
says which.

**An earlier attempt.** Before this project there was an earlier bot built on top of existing client tooling. This
project started fresh and kept only its lessons:
- every attempt to reorder redstone placement made things worse;
- keep helper blocks out of a piston's push cell, and don't fight a machine that has fired;
- a sample you debug on overstates your results;
- one 17,000-line file was part of the problem.

## Glossary of revisions

Each revision was decided on held-out builds and is on by default. Most can be switched off with an environment
variable, so a change in results can be blamed on one of them. The dates are 2026-11.

| revision | date | what it does | switch |
|---|---|---|---|
| `mend` | 13 | After the last clean-up rounds broke through the build to reach scaffolding, put back what was broken. | — |
| `orphan` | 13 | A log (or similar) whose column starts in mid-air gets a helper block to click against, instead of being put off for ever. | — |
| `close` | 13 | Last resort for cells with no usable face (observers, hoppers, pistons): a helper block on each side in turn, with a fine search of spots off the middle of blocks. | — |
| `chain` | 13 | A helper resting on a second helper, allowed in builds with pistons once everything else stands. | — |
| `careful` | 14 | The clean-up takes a piece away only from a place it can still walk back to the chests from. | `MCB_NO_CAREFUL` |
| `fewfloat` | 14 | In the careful clean-up, at most two stands "hung in the air" per piece, each with a capped search. A speed-up. | `MCB_FLOAT_CAREFUL=1` restores the old way |
| `escape` | 14 | An enclosed bot may, on its last try, break a block unless a neighbour would really come off with it. | `MCB_NO_ESCAPE` |
| `askonce` | 16 | Way-out searches that don't settle quickly switch to a search that visits each place once. Tunnelling first asks once for a way to any place next to the cell. | `MCB_NO_PFHEUR`, `MCB_NO_PFTUN` |
| `plankeep` | 17 | The stance planner keeps its search between questions about the same world. | `MCB_NO_PLANKEEP` |
| `thoughtway` | 17 | A way out found in a world-in-thought is kept while the same world is asked about. | `MCB_NO_THOUGHTWAY` |
| `repair` | 18 | After the end game, sections that still fail by the bot's own picture are built again, then the end game runs again, while bad cells keep falling. | `MCB_NO_REPAIR` |
| `ropefar` | 18 | Behind pace, the bot doesn't walk back to take down pieces of its own way that lie 6 or more blocks behind; the clean-up gets them. Walking back was 31 % of one build's ticks. | `MCB_NO_ROPEFAR` |
| `excavate` | 18 | In the repair pass, a cell shut in by standing blocks is reached through a short shaft, which is closed again after. | `MCB_NO_EXCAVATE` |
| `minefence` | 18 | A bot fenced in can break out. | `MCB_NO_MINEFENCE` |
| `lastbreak` | 18 | An enclosed bot whose last-resort search failed makes one more, in which it may break a block whatever comes off with it. "A torch on a wall is one cell; a bot that never moves again is the rest of the build." | `MCB_NO_LASTBREAK` |
| `samefail` | 18 | A put-off cell that failed is not tried again under the same rules until something within two cells of it changes, or its block comes into the inventory. | `MCB_NO_SAMEFAIL` |
| `weblate` | 18 | Cobwebs wait until the first clean-up is over, because the bot can't move in them. | `MCB_NO_WEBLATE` |
| `nearclean` | 19 | Behind pace or past half its time, the clean-up takes the cheapest piece to walk to (with a penalty per level below the highest) instead of the highest: top-down, but region by region. | `MCB_NO_NEARCLEAN` |
| `hungtower` | 20 | Two parts. **Hung back:** when the way back to the top of its own way is lost, the bot climbs back to it instead of leaving it hanging. **Tower serve:** after putting up a long way for one piece, it takes every other piece reachable from up there first. | `MCB_NO_HUNG`, `MCB_NO_HUNGTOWER` |
| `magmasneak` | 20 | On a hot floor (magma) with no safe spot reached, the bot crouches, and the clean-up steps aside when hurt. | `MCB_NO_MAGMASNEAK` |
| `nostrand` | 20 | Before taking away the temporary block it stands on, the bot checks it wouldn't come down somewhere with no way back. | `MCB_NO_NOSTRAND` |
| `dearbreak` | 20 | In last-resort searches, breaking a finished block with no spare in the chests costs far more. After breaking an opening, the bot tries again to walk in without breaking more. | `MCB_NO_DEARBREAK` |
| `nostand` | 20 | The bot keeps the block under its feet when it would come down where it can't stand, for example on a floor of hoppers or cauldrons. | `MCB_NO_NOSTAND` |
| `walkway` | 20 | Behind pace, scaffolding put out sideways from a column top makes a walkway, up to 6 pieces hanging from one column. The clean-up leaves hanging pieces to last. | `MCB_NO_WALKWAY` |
| `covertour` | 21 | Behind pace, before picking the next cell, the bot walks (putting up nothing) to the spot within four blocks that serves the most cells now, and places them. | `MCB_NO_COVERTOUR` |

Judge decisions were made separately from the bot:
- **11-14:** operator-only blocks became exempt.
- **11-19:** cells that can't exist in survival became exempt.
- **11-20:** cells hanging only from such cells became exempt, and a bamboo shoot started counting as bamboo.

`MCB_OLD_JUDGE` gives the judge as it was before 11-14.

**Development switches** that are off or diagnostic:
- `MCB_TRACE` logs every route and every column taken down.
- `MCB_WATCH=x,y,z` logs every change to one cell.
- `MCB_PARANOID` reports the first cell after which the bot has no way out.
- `MCB_SEED` shifts the start position, for seed tests.
- `MCB_WALKFAST`, `MCB_OFFSTOCK*`, `MCB_OD_*`, `MCB_OWNDOWN` and `MCB_HOPPERFLOOR` are candidates that weren't
  taken.

**Not decided when the project ended:** `walkstandbp` (+11 builds over four seeds, within noise) and `odbp`. Both
are off.

## 2026-11-22: the reorganisation

After the release the code was reorganised for people who want to reuse it, without changing what the bot does.
[DESIGN.md](DESIGN.md) describes the result; in short:
- **one planner**: the client mod's copy of the planner was merged into the shared one (`core/`), its differences
  behind `mcb.bot.Rules` and `Site.depot`; the harness in mcb-sim compiles the same planner from here;
- **an adapter layer**: nothing outside `adapter/` uses Minecraft's classes; the planner reads block facts from
  tables the adapter fills, and acts through two interfaces (`mcb.game.Client`, `mcb.game.Oracle`);
- **tests**: unit tests that need no game, a regression gate of 31 test builds generated in code, and a check of
  the client mod's planner against its old copy, all in CI;
- **liftable pieces**: the schematic readers, the judge, the route search and the placement oracle, each with a
  README and a runnable example.

**The baseline** was recorded at the published commits (mcb-builder `0ff6f68`, mcb-sim `8414a5c`) before anything
changed: the Java bot in the headless game on 290 corpus builds (dev3, dev2big, small220; the 9 release
schematics are among them or were added), and `mcsim` at its default revision on the same builds.

**The checks** (corpus results stay outside the repositories; only these summaries are here):

| check | result |
|---|---|
| gate (31 generated builds), every step | identical rows at every commit |
| client check (old client planner vs shared, client settings), every step from the merge | 31 of 31 identical, traces included |
| client check on 43 corpus builds | 41 identical; the other 2 vary from run to run on both sides alike (the same alternative courses, with the same traces) |
| full tier, Java after vs Java baseline (290 builds) | identical on every build where the baseline is stable; see below |
| full tier, Java after vs `mcsim` | as at the baseline: `5f74a607` (Java 50,884 ticks every time, `mcsim` 51,014) and `95a77a6a` (draws clock-seeded random numbers) differ, as before the change |
| fast tier on the final commit (small220 + the small release schematics, 221 builds) | identical to the baseline, apart from the baseline's one flake below |
| `mcsim` on the 31 gate builds (mcb-sim `scripts/synth-check.sh`) | 31 of 31 identical to the Java rows |
| simulator tables (`harness/run.sh tables`) | `states.tsv` byte-identical; placement tables identical except the three items with a random age |

**Java run-to-run variation.** Repeated runs showed that the Java bot is not perfectly reproducible on an
overloaded machine: the game loads chunks on worker threads, and when the machine can't keep up the bot can see a
block update a tick later. On build `896c4378` the baseline once took 75,653 ticks instead of its usual 57,434 (which
`mcsim` and every later run gave); on `3b87ace3` both the old and the new code take 5,924 instead of 5,912 now and
then under overload (16 processes on 8 cores: baseline 1 in 16, new code 2 in 16). Every difference between the
"before" and "after" runs was re-run on both sides until it was clear which it was; none was a change in the bot.

**Minecraft 26.3.** The same day the adapter was made to build and run on 26.3 as well (`MC=26.3`), with the
version differences in two small per-version classes; see [../adapter/versions/README.md](../adapter/versions/README.md).
On 26.3 the gate passes with its own expected rows: 29 of 31 builds take exactly the 26.2 course; two differ by a
rounding difference that comes from the 26.3 test world's ground being at another height.
