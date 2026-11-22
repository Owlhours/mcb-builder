# mcb.schem: schematic readers

This package reads the common Minecraft schematic formats into one simple form: a box of block-state numbers. It
is plain Java with no dependencies (no Minecraft, no libraries), so you can copy the folder into another project
and use it on its own.

| format | files | notes |
|---|---|---|
| Litematica | `.litematic` | any number of regions, including regions with negative sizes; overlapping regions are merged |
| Sponge | `.schem` | versions 1, 2 and 3 |
| vanilla structure | `.nbt` | the structure block format; `structure_void` counts as air; with `palettes`, the first one is used |
| MCEdit / Schematica | `.schematic` | numeric block ids from before 1.13, including `AddBlocks` for ids above 255 |

The format is recognised from the contents, not the file name.

## Use

```java
PaletteResolver states = new PaletteResolver();
Schem s = new SchemIO(states).read(Path.of("house.litematic"));

s.sx, s.sy, s.sz          // size, after cropping to the blocks
s.get(x, y, z)            // the block-state number at a position; 0 is air
states.state(s.get(x, y, z))   // "minecraft:oak_stairs[facing=north,half=top,...]"
s.unknownCells            // blocks the resolver did not know (stored as air)
s.format, s.dataVersion   // "litematic" / "sponge" / "structure" / "legacy", and the game version that wrote it
```

`read` throws an `IOException` for a file that is not a schematic, is damaged, holds no blocks, or is larger than
`SchemIO.MAX_VOLUME` (400 million cells before cropping). A few damaged files end in an unchecked exception instead
(for example a palette index that does not fit), so catch both if a bad file must not stop your program.

## Block states: the resolver

The readers do not decide what number a block state gets; a `StateResolver` does. It has one method for each way
files write block states: a compound tag `{Name, Properties}` (Litematica, structures), a string
`minecraft:oak_stairs[facing=north]` (Sponge), and a numeric id with a data value (MCEdit). It returns 0 for air and
-1 for a block it does not know.

- **`PaletteResolver`** (in this package) gives each distinct state its own number, 1, 2, 3 ... in the order it
  first sees them, and keeps the list (`states()`). The numbers are positions in that list, **not** the game's
  block-state ids. It writes states in a normal form: `namespace:name[key=value,...]`, properties sorted by key,
  `minecraft:` added when there is no namespace; old numeric blocks become `legacy:<id>:<data>`. It does **not**
  upgrade states from older game versions (a 1.12 schematic's `minecraft:grass` stays `minecraft:grass`), does not
  check that blocks exist, and does not fill in default properties. Share one resolver between several reads to
  number several files from one palette.
- **The game's resolver**, `mcb.mc.GameStates` in this repository's Minecraft adapter (`adapter/src/mcb/mc`),
  upgrades old states with Minecraft's own data fixers and returns the running game's global block-state ids;
  `GameStates.reader()` gives a `SchemIO` that uses it. That is what the bot uses.
- **Your own**: implement the three methods of `StateResolver`, for example to map blocks onto your own registry.

## The NBT reader

Schematic files are NBT (Minecraft's binary tag format, usually gzip-compressed). `Nbt` is a small reader for
it: `Nbt.readCompressed(path)` gives the root `Nbt.CompoundTag`. The accessors (`getIntOr`, `getCompoundOrEmpty`,
`getListOrEmpty`, `getByteArray` ...) return a default when an entry is missing or has another type, and convert
numbers the way the game does. Compound keys iterate in `HashMap` order, as in the game, which matters for
schematics whose result depends on order (overlapping regions). Nesting is limited to 512 levels, like in the game.

## The normal form, and `.mcb` files

A `Schem` is a box of `char` block-state numbers, `sx * sy * sz` of them, at index `(y * sz + z) * sx + x`. The
readers crop it to the smallest box that holds every non-air block, so the corner `(0, 0, 0)` is the minimum corner
of the blocks. `contentHash()` is a short hash of the size and the numbers, for recognising the same build in
different files.

`save(path)` and `Schem.load(path)` write and read the `.mcb` format, a compact cache of the normal form. A `.mcb`
file is a zlib (deflate) stream of big-endian values:

| field | type |
|---|---|
| magic `MCB1` | int, `0x4d434231` |
| `sx`, `sy`, `sz` | 3 ints |
| `unknownCells` | long |
| `dataVersion` | int |
| `format` | Java "modified UTF-8" string (2-byte length, then the bytes) |
| the cells | runs, until `sx * sy * sz` cells are covered: a run length (unsigned varint: 7 bits per byte, low bits first, high bit set on all but the last byte) and the block-state number (2 bytes) |

The numbers in a `.mcb` file mean whatever the resolver made them mean: the game's ids for the files this
repository's tools write.

## Example

`Example.java` reads a file and prints its size and its blocks by state, most common first. With nothing but a
JDK 25, from the repository root:

```sh
javac -d /tmp/schem core/src/mcb/schem/*.java
java -cp /tmp/schem mcb.schem.Example path/to/house.litematic
```

```
litematic (data version 4903): 3 x 2 x 2, 8 blocks, 0 unknown
         4  minecraft:stone
         4  minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]
```

## Tests

`core/test/mcb/schem` has the tests (`tools/test.sh` runs them; they need no Minecraft either). They make small
files of every format in code (`Samples.java`, with a little NBT writer in `NbtWriter.java`), so no schematic file
is kept in the repository.
