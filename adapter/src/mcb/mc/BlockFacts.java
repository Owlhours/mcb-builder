package mcb.mc;

import java.util.HashSet;
import java.util.Set;
import mcb.build.ItemInfo;
import mcb.build.StateInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fills the planner's per-state and per-item tables ({@link StateInfo}, {@link ItemInfo}) from the running game's
 * own block and item definitions. This is where everything the planner knows about blocks comes from; another game
 * version needs only this class (and the rest of the adapter) to be right.
 *
 * <p>Needs a bootstrapped game with its tags bound (a booted server, or a client that has joined a world).
 */
public final class BlockFacts {
    private BlockFacts() { }

    /** Makes {@link StateInfo#init()} fill the tables from this game. Call before anything uses the planner. */
    public static void install() {
        if (StateInfo.filler == null) StateInfo.filler = BlockFacts::fill;
        if (mcb.build.Judge.defaultSurvival == null) mcb.build.Judge.defaultSurvival = new GameSurvival(null);
    }

    /** The game's block state for a state id (adapter code only; the planner works with ids). */
    public static BlockState state(int id) { return Block.stateById(id); }

    /** The game's item for an item id, and back. */
    public static Item item(int id) { return BuiltInRegistries.ITEM.byId(id); }
    public static int itemId(Item item) { return BuiltInRegistries.ITEM.getId(item); }

    private static final Set<String> IGNORED = Set.of("waterlogged", "powered", "power", "lit", "triggered", "extended", "locked", "enabled",
        "in_wall", "snowy", "distance", "persistent", "age", "moisture", "stage", "honey_level", "level", "bites", "charges", "eye", "has_record",
        "has_book", "has_bottle_0", "has_bottle_1", "has_bottle_2", "slot_0_occupied", "slot_1_occupied", "slot_2_occupied", "slot_3_occupied",
        "slot_4_occupied", "slot_5_occupied", "occupied", "hatch", "dusted", "unstable", "conditional", "drag", "tilt", "sculk_sensor_phase", "bloom",
        "can_summon", "shrieking", "cracked", "crafting", "ominous", "trial_spawner_state", "vault_state", "tip", "bottom", "natural",
        "creaking_heart_state", "hydration", "potent_sulfur_state", "berries", "attached", "disarmed", "short", "thickness", "signal_fire",
        "instrument", "side_chain", "leaves", "copper_golem_pose");
    private static final Set<String> CONNECTION = Set.of("north", "east", "south", "west", "up", "down");

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void fill() {
        // ---- items
        int ni = BuiltInRegistries.ITEM.size();
        ItemInfo.allocate(ni);
        for (int k = 0; k < ni; k++) {
            Item it = BuiltInRegistries.ITEM.byId(k);
            ItemInfo.name[k] = BuiltInRegistries.ITEM.getKey(it).getPath();
            ItemInfo.maxStack[k] = new ItemStack(it).getMaxStackSize();
        }
        ItemInfo.AIR = itemId(Items.AIR);
        ItemInfo.SCAFFOLDING = itemId(Items.SCAFFOLDING);
        // ---- block states
        int N = Block.BLOCK_STATE_REGISTRY.size();
        StateInfo.allocate(N);
        Set<Class<?>> interactiveClasses = new HashSet<>(), plainClasses = new HashSet<>();
        EmptyBlockGetter g = EmptyBlockGetter.INSTANCE;
        for (Block b : BuiltInRegistries.BLOCK) StateInfo.defaults.put(BuiltInRegistries.BLOCK.getKey(b).getPath(), Block.getId(b.defaultBlockState()));
        for (int i = 0; i < N; i++) {
            BlockState s = Block.stateById(i);
            Block b = s.getBlock();
            StateInfo.name[i] = s.toString().replace("Block{minecraft:", "").replace("}", "");
            StateInfo.blockName[i] = BuiltInRegistries.BLOCK.getKey(b).getPath();
            StateInfo.descriptionId[i] = b.getDescriptionId();
            StateInfo.block[i] = BuiltInRegistries.BLOCK.getId(b);
            StateInfo.air[i] = s.isAir();
            boolean waterPlant = !s.getFluidState().isEmpty() && !s.hasProperty(BlockStateProperties.WATERLOGGED);
            StateInfo.fluid[i] = b instanceof LiquidBlock || waterPlant || b == Blocks.BUBBLE_COLUMN;
            StateInfo.gravity[i] = b instanceof FallingBlock || b instanceof BrushableBlock;
            StateInfo.replaceable[i] = s.canBeReplaced();
            VoxelShape col;
            try { col = s.getCollisionShape(g, BlockPos.ZERO); } catch (RuntimeException e) { col = net.minecraft.world.phys.shapes.Shapes.block(); }
            StateInfo.fullCube[i] = Block.isShapeFullBlock(col);
            StateInfo.noCollision[i] = col.isEmpty();
            StateInfo.scaffolding[i] = b == Blocks.SCAFFOLDING;
            StateInfo.walkThrough[i] = StateInfo.noCollision[i] || StateInfo.scaffolding[i];
            try { StateInfo.sturdyTop[i] = s.isFaceSturdy(g, BlockPos.ZERO, Direction.UP); } catch (RuntimeException e) { StateInfo.sturdyTop[i] = false; }
            if (b instanceof net.minecraft.world.level.block.DoorBlock door) {
                // the leaf is a thin slab along one side of the cell: one can stand in the cell, but not cross that side
                int m = 0;
                for (AABB box : col.toAabbs()) {
                    if (box.maxX > 0.8 && box.minZ < 0.8 && box.maxZ > 0.2) m |= 1;
                    if (box.minX < 0.2 && box.minZ < 0.8 && box.maxZ > 0.2) m |= 2;
                    if (box.maxZ > 0.8 && box.minX < 0.8 && box.maxX > 0.2) m |= 4;
                    if (box.minZ < 0.2 && box.minX < 0.8 && box.maxX > 0.2) m |= 8;
                }
                StateInfo.doorEdges[i] = (byte) m;
                StateInfo.handDoor[i] = door.type().canOpenByHand();
                StateInfo.walkThrough[i] = true;
                StateInfo.door[i] = true;
                StateInfo.doorLower[i] = s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
                StateInfo.doorFacing[i] = (byte) s.getValue(BlockStateProperties.HORIZONTAL_FACING).get3DDataValue();
                StateInfo.doorHingeRight[i] = s.getValue(BlockStateProperties.DOOR_HINGE) == net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT;
            }
            // (not magma: whole floors are made of it, and a second or two on it is survivable - see Builder.guardHealth;
            // not berry bushes: placed without berries they do nothing, and nothing grows during a build)
            StateInfo.hazard[i] = (b instanceof net.minecraft.world.level.block.CampfireBlock && s.getValue(BlockStateProperties.LIT))
                || b == Blocks.CACTUS || b == Blocks.WITHER_ROSE || b instanceof net.minecraft.world.level.block.BaseFireBlock;
            StateInfo.hot[i] = b == Blocks.MAGMA_BLOCK;
            if (b instanceof net.minecraft.world.level.block.LadderBlock) {
                StateInfo.rung[i] = 1;
                StateInfo.rungFacing[i] = (byte) (1 + s.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue());
            }
            if (b instanceof net.minecraft.world.level.block.TrapDoorBlock && b != Blocks.IRON_TRAPDOOR) {
                boolean open = s.getValue(BlockStateProperties.OPEN);
                if (open) StateInfo.rung[i] = 2;
                else if (s.getValue(BlockStateProperties.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP) StateInfo.rung[i] = 3;
                StateInfo.rungFacing[i] = (byte) (1 + s.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue());
                StateInfo.handTrapdoor[i] = true;
            }
            if (b instanceof net.minecraft.world.level.block.FenceGateBlock) {
                StateInfo.handDoor[i] = true;
                StateInfo.closedGate[i] = !s.getValue(BlockStateProperties.OPEN);
                StateInfo.fenceGate[i] = true;
            }
            double top = 0, bottom = 99, atop = 0, abottom = 99;
            for (AABB box : col.toAabbs()) {
                if (box.maxX > 0.2 && box.minX < 0.8 && box.maxZ > 0.2 && box.minZ < 0.8) { top = Math.max(top, box.maxY); bottom = Math.min(bottom, box.minY); }
                atop = Math.max(atop, box.maxY); abottom = Math.min(abottom, box.minY);
            }
            StateInfo.anyTop[i] = (byte) Math.round(atop * 16);
            StateInfo.anyBottom[i] = (byte) (abottom > 90 ? 0 : Math.round(abottom * 16));
            StateInfo.colTop[i] = (byte) Math.round(top * 16);
            StateInfo.colBottom[i] = (byte) (bottom > 90 ? 0 : Math.round(bottom * 16));
            // the outline (what the crosshair hits), as Ray tests it
            var list = s.getShape(g, BlockPos.ZERO).toAabbs();
            float[] ob = new float[list.size() * 6];
            int k = 0;
            for (AABB a : list) {
                ob[k++] = (float) a.minX; ob[k++] = (float) a.minY; ob[k++] = (float) a.minZ; ob[k++] = (float) a.maxX; ob[k++] = (float) a.maxY; ob[k++] = (float) a.maxZ;
            }
            StateInfo.outline[i] = ob;
            StateInfo.leaves[i] = s.is(BlockTags.LEAVES);
            StateInfo.climbable[i] = s.is(BlockTags.CLIMBABLE);
            StateInfo.interactive[i] = isInteractive(b.getClass(), interactiveClasses, plainClasses);
            StateInfo.secondary[i] = (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
                // (any bed's head, by its property: 26.3 has beds that are not a BedBlock, such as the straw bed)
                || (s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD);
            // (doors and gates that open by hand are no obstacle to the builder; the rest could shut it in or out)
            // (and TNT: it is payload, not footing, and anything live next to it sets it off)
            StateInfo.late[i] = (b instanceof net.minecraft.world.level.block.DoorBlock d0 && !d0.type().canOpenByHand()) || b instanceof net.minecraft.world.level.block.TntBlock;
            StateInfo.technical[i] = b == Blocks.PISTON_HEAD || b == Blocks.MOVING_PISTON || b == Blocks.STRUCTURE_VOID || b == Blocks.NETHER_PORTAL
                || b == Blocks.END_PORTAL || b == Blocks.END_GATEWAY || b == Blocks.LIGHT || b == Blocks.FROSTED_ICE;
            BlockState dw = s.hasProperty(BlockStateProperties.WATERLOGGED) ? s.setValue(BlockStateProperties.WATERLOGGED, false) : s;
            StateInfo.dewater[i] = (char) Block.getId(dw);
            // ---- items
            Item it = b.asItem();
            if (b instanceof FlowerPotBlock) it = Items.FLOWER_POT;
            if (b == Blocks.WATER_CAULDRON || b == Blocks.LAVA_CAULDRON || b == Blocks.POWDER_SNOW_CAULDRON) it = Items.CAULDRON;
            StateInfo.item[i] = itemId(it);
            int cnt = 1;
            if (s.hasProperty(BlockStateProperties.SLAB_TYPE) && s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) cnt = 2;
            if (s.hasProperty(BlockStateProperties.LAYERS)) cnt = s.getValue(BlockStateProperties.LAYERS);
            if (s.hasProperty(BlockStateProperties.CANDLES)) cnt = s.getValue(BlockStateProperties.CANDLES);
            if (s.hasProperty(BlockStateProperties.PICKLES)) cnt = s.getValue(BlockStateProperties.PICKLES);
            if (s.hasProperty(BlockStateProperties.EGGS)) cnt = s.getValue(BlockStateProperties.EGGS);
            if (s.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) cnt = s.getValue(BlockStateProperties.FLOWER_AMOUNT);
            if (s.hasProperty(BlockStateProperties.SEGMENT_AMOUNT)) cnt = s.getValue(BlockStateProperties.SEGMENT_AMOUNT);
            if (StateInfo.secondary[i] || it == Items.AIR) cnt = 0;
            StateInfo.itemCount[i] = (byte) cnt;
            // the state the first of several clicks makes (BuildState.spec)
            BlockState first = s;
            if (cnt > 1) {
                if (s.hasProperty(BlockStateProperties.SLAB_TYPE)) first = s.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
                if (s.hasProperty(BlockStateProperties.LAYERS)) first = s.setValue(BlockStateProperties.LAYERS, 1);
                if (s.hasProperty(BlockStateProperties.CANDLES)) first = s.setValue(BlockStateProperties.CANDLES, 1);
                if (s.hasProperty(BlockStateProperties.PICKLES)) first = s.setValue(BlockStateProperties.PICKLES, 1);
                if (s.hasProperty(BlockStateProperties.EGGS)) first = s.setValue(BlockStateProperties.EGGS, 1);
                if (s.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) first = s.setValue(BlockStateProperties.FLOWER_AMOUNT, 1);
                if (s.hasProperty(BlockStateProperties.SEGMENT_AMOUNT)) first = s.setValue(BlockStateProperties.SEGMENT_AMOUNT, 1);
            }
            StateInfo.firstState[i] = (char) Block.getId(first);
            // a potted plant: the plant that goes into the pot
            StateInfo.contentItem[i] = ItemInfo.NONE;
            if (b instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR) {
                Item c = pot.getPotted().asItem();
                if (c != Items.AIR) StateInfo.contentItem[i] = itemId(c);
            }
            // ---- breaking by hand
            StateInfo.destroyTime[i] = b.defaultDestroyTime();
            StateInfo.needsTool[i] = s.requiresCorrectToolForDrops();
            // ---- canonical form
            BlockState c = s;
            if (StateInfo.air[i]) c = Blocks.AIR.defaultBlockState();
            else if (b == Blocks.WATER_CAULDRON || b == Blocks.LAVA_CAULDRON || b == Blocks.POWDER_SNOW_CAULDRON) c = Blocks.CAULDRON.defaultBlockState();
            else {
                BlockState def = b.defaultBlockState();
                boolean multiface = b instanceof MultifaceBlock || b instanceof VineBlock;
                boolean ironOpen = b == Blocks.IRON_DOOR || b == Blocks.IRON_TRAPDOOR;
                for (Property p : s.getProperties()) {
                    String n = p.getName();
                    boolean ignore = IGNORED.contains(n) || (CONNECTION.contains(n) && !multiface)
                        || (n.equals("shape")) || (n.equals("open") && ironOpen);
                    if (ignore) c = c.setValue(p, def.getValue(p));
                }
            }
            // a closed fence gate looks the same from both sides, and opening one turns it to swing away from whoever
            // opens it: north/south and east/west are one state each
            if (b instanceof net.minecraft.world.level.block.FenceGateBlock && !c.getValue(BlockStateProperties.OPEN)) c = gateAxis(c);
            StateInfo.canon[i] = (char) Block.getId(c);
            BlockState l = c;
            if (!StateInfo.air[i]) {
                BlockState def = b.defaultBlockState();
                for (Property p : c.getProperties()) {
                    String n = p.getName();
                    if (StateInfo.ADJUSTABLE.contains(n) || (n.equals("type") && b instanceof ChestBlock)) l = l.setValue(p, def.getValue(p));
                }
            }
            if (b instanceof net.minecraft.world.level.block.FenceGateBlock) l = gateAxis(l);
            StateInfo.loose[i] = (char) Block.getId(l);
            // ---- judging
            StateInfo.gameMaster[i] = b instanceof net.minecraft.world.level.block.GameMasterBlock;
            StateInfo.decorWide[i] = !StateInfo.air[i] && (StateInfo.noCollision[i] || StateInfo.leaves[i] || decorativeWide(b));
            // ---- kinds and properties the planner looks at
            if (b instanceof ChestBlock) {
                StateInfo.chest[i] = true;
                ChestType type = s.getValue(ChestBlock.TYPE);
                StateInfo.chestType[i] = (byte) (type == ChestType.SINGLE ? 0 : type == ChestType.LEFT ? 1 : 2);
                StateInfo.chestFacing[i] = (byte) s.getValue(ChestBlock.FACING).get3DDataValue();
                StateInfo.chestLink[i] = (byte) ChestBlock.getConnectedDirection(s).get3DDataValue();
                StateInfo.chestSingle[i] = (char) Block.getId(s.setValue(ChestBlock.TYPE, ChestType.SINGLE));
            }
            if (b instanceof net.minecraft.world.level.block.piston.PistonBaseBlock) {
                StateInfo.pistonBase[i] = true;
                StateInfo.facing[i] = (byte) s.getValue(BlockStateProperties.FACING).get3DDataValue();
            }
            StateInfo.machine[i] = b instanceof net.minecraft.world.level.block.piston.PistonBaseBlock || b instanceof net.minecraft.world.level.block.piston.PistonHeadBlock
                || b instanceof net.minecraft.world.level.block.ObserverBlock || b == Blocks.SLIME_BLOCK || b == Blocks.HONEY_BLOCK;
            StateInfo.tnt[i] = b instanceof net.minecraft.world.level.block.TntBlock;
            StateInfo.hopper[i] = b instanceof net.minecraft.world.level.block.HopperBlock;
            StateInfo.bamboo[i] = b == Blocks.BAMBOO;
            StateInfo.bambooShoot[i] = b == Blocks.BAMBOO_SAPLING;
            StateInfo.cobweb[i] = b == Blocks.COBWEB;
            StateInfo.ice[i] = b == Blocks.ICE;
            StateInfo.chain[i] = b.getClass() == net.minecraft.world.level.block.ChainBlock.class;
            StateInfo.axis[i] = 1;
            if (s.hasProperty(BlockStateProperties.AXIS)) {
                var a = s.getValue(BlockStateProperties.AXIS);
                StateInfo.axis[i] = (byte) (a == Direction.Axis.X ? 0 : a == Direction.Axis.Z ? 2 : 1);
            }
            if (b == Blocks.SCAFFOLDING) {
                StateInfo.scaffoldDistance[i] = (byte) (int) s.getValue(net.minecraft.world.level.block.ScaffoldingBlock.DISTANCE);
                StateInfo.scaffoldBottom[i] = s.getValue(net.minecraft.world.level.block.ScaffoldingBlock.BOTTOM);
            }
        }
    }

    /** True if the block class (below BlockBehaviour) overrides either right-click hook. */
    private static boolean isInteractive(Class<?> c, Set<Class<?>> yes, Set<Class<?>> no) {
        if (yes.contains(c)) return true;
        if (no.contains(c)) return false;
        boolean r = false;
        for (Class<?> k = c; k != null && k != BlockBehaviour.class && k != Object.class; k = k.getSuperclass()) {
            for (var m : k.getDeclaredMethods()) {
                if (m.getName().equals("useWithoutItem") || m.getName().equals("useItemOn")) { r = true; break; }
            }
            if (r) break;
        }
        (r ? yes : no).add(c);
        return r;
    }

    private static BlockState gateAxis(BlockState s) {
        Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (f == Direction.SOUTH) return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        if (f == Direction.WEST) return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        return s;
    }

    /**
     * The wider reading of "decorative" and "leaves judged leniently", after the rules the project's owner settled on
     * for the earlier attempt: decoration that is not a full block - redstone parts, rails, lecterns, flower pots,
     * candles, lanterns, torches, flowers and other plants, carpets, buttons, levers, signs, banners, heads - is exempt,
     * missing or in the wrong state, and leaves count as foliage. Stairs, slabs, fences, walls, panes, doors and
     * trapdoors still count; so do cactus, bamboo, sea pickles, dripleaf and chorus. (This is the block-class part.)
     */
    private static boolean decorativeWide(Block b) {
        return b instanceof net.minecraft.world.level.block.CarpetBlock || b.getDescriptionId().endsWith("carpet") || b instanceof net.minecraft.world.level.block.LanternBlock
            || b instanceof FlowerPotBlock || b instanceof net.minecraft.world.level.block.CandleBlock
            || b instanceof net.minecraft.world.level.block.LecternBlock || b instanceof net.minecraft.world.level.block.DiodeBlock
            || b instanceof net.minecraft.world.level.block.AbstractSkullBlock || b instanceof net.minecraft.world.level.block.DaylightDetectorBlock
            || b instanceof net.minecraft.world.level.block.SignBlock || b instanceof net.minecraft.world.level.block.AbstractBannerBlock;
    }
}
