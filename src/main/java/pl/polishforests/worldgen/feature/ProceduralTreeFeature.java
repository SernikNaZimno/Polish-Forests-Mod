package pl.polishforests.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import pl.polishforests.block.MarcescentLeavesBlock;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.SpeciesLeavesBlock;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.season.FoliageCurve;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.feature.config.TreePalette;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeSize;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Feature type {@code polishforests:procedural_tree} (step V6 of docs/04-pionowy-wycinek.md): one tree of the tree
 * planner ({@link TreeShapePlan}) with the species, the layer and the age class of its configuration, as a lone tree
 * (no crown territory and no stand map, so an open-grown crown; the configured features {@code polishforests:sapling/<species>}
 * of step V7 grow the saplings, the pole stage of the canopy; solitary trees V9). The tree stand of step V9 calls the planner directly with
 * the territories and the stand map (rule ZW-1, {@code PlacedFeature.place} passes no context) and writes the result with
 * {@link #write}.
 *
 * <p><b>Writing</b> ({@link #write}): the wood only where the world holds air or a block of
 * {@code #minecraft:replaceable_by_trees} (plants, leaves), so the wood of a tree replaces leaves of another tree and leaves
 * never replace wood, in either order; the leaves only on air or such a block that is not a leaf (step V9: a leaf never
 * replaces the leaf of another tree, and the placeholder trees of {@link FastTreeFeature} do not replace the blocks of
 * procedural trees, so no tree cuts the leaf paths of another one; where two crowns meet, the first one keeps the block).
 * The wood goes first, in the planner's order from the root; a block whose parent could not be written is left out with
 * all its descendants. Before the leaves, the feature notes which leaf blocks can be placed; when some wood or some leaf
 * cannot, the leaf distances are computed again from the wood that exists through the placeable leaves only
 * ({@link TreeVoxels#leafDistances(boolean[], boolean[])}), so no leaf is placed that would decay. Within a stand the
 * territories and the split surfaces keep the crowns of procedural trees apart.
 * No vanilla tree decorators
 * (risk R8: a bracket fungus or moss on the face of a block would hang 8 − r px away from a thin trunk; own fungi in M4).
 *
 * <p><b>Blocks.</b> Radius 1–7 px: the {@code TrunkBlock} of the species with its parent direction (the Scots pine with
 * its lower bark, the two blocks of the transition zone and the "mirror"); a full log ({@link TreeVoxels#FULL}): the
 * log of {@link TreeTrunk#log()} (vanilla, or the LITE Scots pine log) with the axis of its parent. Leaves (step V7): the
 * species' own leaf block ({@link SpeciesLeavesBlock}) with the planner's distance and the tree's phase ({@link #phase}:
 * 0-3 from the hash of the trunk base, so every leaf of a tree turns at the same time and the trees of a stand at different
 * times; ZW-5); for oak, hornbeam and beech the marcescent leaves ({@link MarcescentLeavesBlock}) on the lower share
 * {@link #marcescentShare} of the crown (V7 review round 1) of the trees that keep their dead leaves ({@link #marcescentChance},
 * a roll of the trunk base; step Z2).
 *
 * <p><b>Ground.</b> The trunk base needs a block below with a sturdy top face, or farmland (as vanilla saplings grow on
 * it); farmland under a placed tree turns into dirt.
 *
 * <p><b>Size</b> (step S1): the optional field {@code size} ({@link TreeSize}, by default the planner's own) scales the
 * tree; the configured features {@code polishforests:sapling/<species>/medium} and {@code …/large} of the sapling sizes
 * ({@link pl.polishforests.block.SaplingSize}) use it.
 *
 * @param species a species with a planner shape ({@link SpeciesShape#supported})
 */
public record ProceduralTreeFeature(Species species, CrownLayer layer, AgeClass age, TreeSize size) implements Feature {
	private static final Codec<Species> SPECIES = TreePalette.SPECIES_CODEC.validate(s -> SpeciesShape.supported(s)
			? DataResult.success(s) : DataResult.error(() -> "no tree shape for " + s.id()));
	public static final MapCodec<ProceduralTreeFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			SPECIES.fieldOf("species").forGetter(ProceduralTreeFeature::species),
			enumCodec(CrownLayer.values()).optionalFieldOf("layer", CrownLayer.CANOPY).forGetter(ProceduralTreeFeature::layer),
			enumCodec(AgeClass.values()).optionalFieldOf("age_class", AgeClass.MATURE).forGetter(ProceduralTreeFeature::age),
			TreeSize.CODEC.optionalFieldOf("size", TreeSize.ONE).forGetter(ProceduralTreeFeature::size))
			.apply(i, ProceduralTreeFeature::new));

	/** Salt of the tree phase (rule ZW-2: a new field, a new salt). */
	public static final String PHASE_SALT = "feature.tree.phase";
	/** Salt of the roll whether a tree keeps its dead leaves over the winter (step Z2; rule ZW-2: a new field, a new salt). */
	public static final String MARCESCENCE_SALT = "feature.tree.marcescence";

	/** Block update flags of the generation, as {@link FastTreeFeature}. */
	private static final int FLAGS = 19;
	private static final Map<Species, TreeTrunk> TRUNKS = new EnumMap<>(Species.class);

	static {
		for (TreeTrunk t : TreeTrunk.values()) {
			TRUNKS.putIfAbsent(t.species(), t);
		}
	}

	public ProceduralTreeFeature {
		if (!SpeciesShape.supported(species)) {
			throw new IllegalArgumentException("no tree shape for " + species);
		}
		if (size == null) {
			size = TreeSize.ONE;
		}
	}

	/** A tree of the planner's own size. */
	public ProceduralTreeFeature(Species species, CrownLayer layer, AgeClass age) {
		this(species, layer, age, TreeSize.ONE);
	}

	/** A codec of the lower-case names of an enum. */
	private static <E extends Enum<E>> Codec<E> enumCodec(E[] values) {
		return Codec.STRING.comapFlatMap(name -> {
			for (E e : values) {
				if (e.name().toLowerCase(Locale.ROOT).equals(name)) {
					return DataResult.success(e);
				}
			}
			return DataResult.error(() -> "unknown value " + name);
		}, e -> e.name().toLowerCase(Locale.ROOT));
	}

	@Override
	public MapCodec<ProceduralTreeFeature> codec() {
		return CODEC;
	}

	/** Plants the tree with its trunk base at {@code origin}, which must be free with ground below ({@link #ground}). */
	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos below = origin.below();
		if (origin.getY() <= level.getMinY() || !replaceable(level.getBlockState(origin)) || !ground(level, below)) {
			return false;
		}
		TreeVoxels v = plan(level.getSeed(), origin);
		if (write(level, v, species, layer, age) == 0) {
			return false;
		}
		if (level.getBlockState(below).is(Blocks.FARMLAND)) {
			level.setBlock(below, Blocks.DIRT.defaultBlockState(), FLAGS);
		}
		ModFeatures.woodyPlaced(origin, species);
		return true;
	}

	/** Ground for a trunk base: a block with a sturdy top face, or farmland (a sapling planted on it can grow). */
	static boolean ground(WorldGenLevel level, BlockPos below) {
		BlockState s = level.getBlockState(below);
		return s.isFaceSturdy(level, below, Direction.UP) || s.is(Blocks.FARMLAND);
	}

	/** The tree of this configuration with its trunk base at {@code origin} (a pure function of the seed and position). */
	public TreeVoxels plan(long seed, BlockPos origin) {
		return TreeShapePlan.plan(new TreeRequest(species, layer, age, size, seed, origin.getX(), origin.getY(), origin.getZ()));
	}

	/**
	 * Whether the tree of this configuration fits at {@code origin} (a sapling's room check, step V7): ground below,
	 * and every wood block of the planned tree inside the build height on air or a block that trees replace; the block at
	 * {@code origin} itself counts as free (the sapling is removed before the tree is placed). Leaves need no room, as in
	 * {@link #write} they are left out where something else stands.
	 */
	public boolean fits(WorldGenLevel level, BlockPos origin) {
		BlockPos below = origin.below();
		if (origin.getY() <= level.getMinY() || !ground(level, below)) {
			return false;
		}
		return woodFits(plan(level.getSeed(), origin), origin, level.getMaxY(), p -> replaceable(level.getBlockState(p)));
	}

	/**
	 * The room check of {@link #fits} on a planned tree: every wood block below {@code maxY} (exclusive) and {@code free},
	 * the block at {@code origin} (the sapling) excepted.
	 */
	public static boolean woodFits(TreeVoxels v, BlockPos origin, int maxY, Predicate<BlockPos> free) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < v.woodCount(); i++) {
			pos.set(v.x() + v.woodX(i), v.y() + v.woodY(i), v.z() + v.woodZ(i));
			if (pos.equals(origin)) {
				continue;
			}
			if (pos.getY() >= maxY || !free.test(pos)) {
				return false;
			}
		}
		return true;
	}

	/** The phase 0-3 of the tree with its trunk base at (x, z): the shift of its color curve (step V7, ZW-5). */
	public static int phase(long seed, int x, int z) {
		return (int) (new Noise(seed).derive(PHASE_SALT).hash(x, z) >>> 62);
	}

	/** The roll 0-1 of the tree with its trunk base at (x, z) against {@link #marcescentChance} (step Z2). */
	public static double marcescenceRoll(long seed, int x, int z) {
		return (new Noise(seed).derive(MARCESCENCE_SALT).hash(x, z) >>> 11) * 0x1.0p-53;
	}

	/**
	 * The chance that a tree of a species, crown layer and age class keeps dead leaves over the winter (step Z2, CP-C.2:
	 * the bare oak-hornbeam canopy was too closed and too brown, about 17% of its broadleaf leaves marcescent). Strongest in
	 * young hornbeams and beeches (juvenile marcescence: 3 of 4 pole-stage trees), weaker in young oaks (1 of 2), in half of
	 * the subcanopy hornbeams and beeches and a quarter of the subcanopy oaks, in the lower crown of 1 of 4 mature canopy
	 * beeches only; none for a species that never keeps dead leaves. A pole-stage tree includes one grown from a sapling.
	 */
	public static double marcescentChance(Species species, CrownLayer layer, AgeClass age) {
		if (!FoliageCurve.marcescent(species)) {
			return 0.0;
		}
		boolean oak = species == Species.OAK;
		if (age == AgeClass.POLE_STAGE) {
			return oak ? 0.5 : 0.75;
		}
		if (layer == CrownLayer.SUBCANOPY) {
			return oak ? 0.25 : 0.5;
		}
		return species == Species.BEECH ? 0.25 : 0.0;
	}

	/**
	 * The share of the crown (from its lowest leaf up) whose leaves are marcescent on a tree that keeps its dead leaves
	 * ({@link #marcescentChance}): V7 review round 1 had the whole crown of a young tree, the lower half of a subcanopy tree
	 * and the lowest quarter of a canopy tree on every tree; step Z2 keeps the juvenile zone smaller: 0.6 of a young
	 * hornbeam or beech (0.4 of a young oak), the lower 0.3 of a subcanopy tree and the lowest 0.15 of a canopy beech.
	 */
	public static double marcescentShare(Species species, CrownLayer layer, AgeClass age) {
		if (marcescentChance(species, layer, age) <= 0.0) {
			return 0.0;
		}
		if (age == AgeClass.POLE_STAGE) {
			return species == Species.OAK ? 0.4 : 0.6;
		}
		return layer == CrownLayer.SUBCANOPY ? 0.3 : 0.15;
	}

	/**
	 * The height (relative to the trunk base, exclusive) below which the leaves of a planned tree are marcescent, or
	 * {@link Integer#MIN_VALUE} when none are: the lower {@link #marcescentShare} of its leaf rows if the roll of its trunk
	 * base ({@link #marcescenceRoll}) is below {@link #marcescentChance}; a pure function of the seed and the tree.
	 */
	public static int dryBelow(long seed, TreeVoxels v, Species species, CrownLayer layer, AgeClass age) {
		double share = marcescentShare(species, layer, age);
		int leaves = v.leafCount();
		if (share <= 0 || leaves == 0 || marcescenceRoll(seed, v.x(), v.z()) >= marcescentChance(species, layer, age)) {
			return Integer.MIN_VALUE;
		}
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int i = 0; i < leaves; i++) {
			lo = Math.min(lo, v.leafY(i));
			hi = Math.max(hi, v.leafY(i));
		}
		return lo + (int) Math.ceil(share * (hi - lo + 1));
	}

	/**
	 * Writes a planned tree (see the class comment) of a crown layer and age class (they pick the marcescent leaves).
	 *
	 * @return the number of wood blocks written (0 when even the root could not be)
	 */
	public static int write(WorldGenLevel level, TreeVoxels v, Species species, CrownLayer layer, AgeClass age) {
		return (int) (writeCounted(level, v, species, layer, age) >>> 32);
	}

	/**
	 * {@link #write}, returning the wood blocks written in the high and the leaves written in the low 32 bits (the counters
	 * of the tree stand, {@code ModFeatures.STATS}).
	 */
	public static long writeCounted(WorldGenLevel level, TreeVoxels v, Species species, CrownLayer layer, AgeClass age) {
		int n = v.woodCount();
		boolean[] written = new boolean[n];
		int count = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < n; i++) {
			int p = v.parentIndex(i);
			if (p >= 0 && !written[p]) {
				continue;
			}
			pos.set(v.x() + v.woodX(i), v.y() + v.woodY(i), v.z() + v.woodZ(i));
			if (pos.getY() >= level.getMaxY() || !replaceable(level.getBlockState(pos))) {
				continue;
			}
			level.setBlock(pos, wood(species, v.bark(i), v.radius(i), Direction.from3DDataValue(v.parent(i))), FLAGS);
			written[i] = true;
			count++;
		}
		if (count == 0) {
			return 0;
		}
		int leaves = v.leafCount();
		boolean[] placeable = new boolean[leaves];
		boolean all = count == n;
		for (int i = 0; i < leaves; i++) {
			pos.set(v.x() + v.leafX(i), v.y() + v.leafY(i), v.z() + v.leafZ(i));
			placeable[i] = pos.getY() < level.getMaxY() && leafReplaceable(level.getBlockState(pos));
			all &= placeable[i];
		}
		byte[] distances = all ? null : v.leafDistances(written, placeable);
		BlockState leaf = leaves(species, phase(level.getSeed(), v.x(), v.z()));
		int dryBelow = dryBelow(level.getSeed(), v, species, layer, age);
		BlockState dry = dryBelow == Integer.MIN_VALUE ? leaf : leaf.setValue(MarcescentLeavesBlock.MARCESCENT, true);
		int placed = 0;
		for (int i = 0; i < leaves; i++) {
			int d = distances == null ? v.leafDistance(i) : distances[i];
			if (d < 1 || !placeable[i]) {
				continue;
			}
			pos.set(v.x() + v.leafX(i), v.y() + v.leafY(i), v.z() + v.leafZ(i));
			level.setBlock(pos, (v.leafY(i) < dryBelow ? dry : leaf).setValue(LeavesBlock.DISTANCE, d), FLAGS);
			placed++;
		}
		return (long) count << 32 | placed;
	}

	private static boolean replaceable(BlockState state) {
		return state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES);
	}

	/**
	 * Where a leaf may go: air or a block trees replace, but not the leaves of another tree (decision V9): a leaf that
	 * overwrote a neighbor's leaf could cut the path of that neighbor's other leaves to its wood, and they would decay.
	 */
	private static boolean leafReplaceable(BlockState state) {
		return state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES) && !state.is(BlockTags.LEAVES);
	}

	/** The trunk block of a species and bark ({@link TreeVoxels#LOWER} … {@link TreeVoxels#UPPER}). */
	public static TreeTrunk trunk(Species species, int bark) {
		if (species == Species.SCOTS_PINE) {
			return switch (bark) {
				case TreeVoxels.TRANSITION -> TreeTrunk.SCOTS_PINE_TRANSITION;
				case TreeVoxels.TRANSITION_UPPER -> TreeTrunk.SCOTS_PINE_TRANSITION_UPPER;
				case TreeVoxels.UPPER -> TreeTrunk.SCOTS_PINE_UPPER;
				default -> TreeTrunk.SCOTS_PINE;
			};
		}
		TreeTrunk t = TRUNKS.get(species);
		if (t == null) {
			throw new IllegalArgumentException("no trunk block for " + species);
		}
		return t;
	}

	/**
	 * A wood block: a thin trunk of radius 1–7 px or, for {@link TreeVoxels#FULL}, the log of {@link TreeTrunk#log()} (vanilla,
	 * or the LITE Scots pine log) along the parent axis.
	 */
	public static BlockState wood(Species species, int bark, int radius, Direction parent) {
		if (bark == TreeVoxels.ROOT) {
			// Step I1: the roots of a windthrow's root plate.
			return ModBlocks.treeRoot().state(radius, parent);
		}
		TreeTrunk t = trunk(species, bark);
		if (radius >= TreeVoxels.FULL) {
			Block log = t.log();
			BlockState s = log.defaultBlockState();
			return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, parent.getAxis()) : s;
		}
		return ModBlocks.trunk(t).state(radius, parent);
	}

	/** The leaves of a species (step V7) in a phase, not persistent, without a distance. */
	public static BlockState leaves(Species species, int phase) {
		TreeFoliage f = TreeFoliage.of(species);
		if (f == null) {
			throw new IllegalArgumentException("no leaf block for " + species);
		}
		return ModBlocks.leaves(f).defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(SpeciesLeavesBlock.PHASE, phase);
	}
}
