package pl.polishforests.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
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
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.worldgen.feature.config.TreePalette;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Feature type {@code polishforests:procedural_tree} (step V6 of docs/04-pionowy-wycinek.md): one tree of the tree
 * planner ({@link TreeShapePlan}) with the species, the layer and the age class of its configuration, as a lone tree
 * (no crown territory and no stand map, so an open-grown crown; for saplings and solitary trees, reachable from Java
 * only until a configured feature of this type exists, V7/V9). The tree stand of step V9 calls the planner directly with
 * the territories and the stand map (rule ZW-1, {@code PlacedFeature.place} passes no context) and writes the result with
 * {@link #write}.
 *
 * <p><b>Writing</b> ({@link #write}): every block only where the world holds air or a block of
 * {@code #minecraft:replaceable_by_trees} (plants, leaves), so the wood of a tree replaces leaves of another tree and leaves
 * never replace wood, in either order. The wood goes first, in the planner's order from the root; a block whose parent
 * could not be written is left out with all its descendants. Before the leaves, the feature notes which leaf blocks can
 * be placed; when some wood or some leaf cannot, the leaf distances are computed again from the wood that exists through
 * the placeable leaves only ({@link TreeVoxels#leafDistances(boolean[], boolean[])}), so no leaf is placed that would
 * decay. (A leaf of this tree that a later tree overwrites can still cut the path of another leaf; within a stand the
 * territories and the split surfaces keep the leaves of procedural trees apart, risk R5 for the vanilla substitutes.)
 * No vanilla tree decorators
 * (risk R8: a bracket fungus or moss on the face of a block would hang 8 − r px away from a thin trunk; own fungi in M4).
 *
 * <p><b>Blocks.</b> Radius 1–7 px: the {@code TrunkBlock} of the species with its parent direction (the Scots pine with
 * its lower bark, the two blocks of the transition zone and the "mirror"); a full log ({@link TreeVoxels#FULL}): the
 * vanilla log of {@link TreeTrunk#log()} with the axis of its parent. Leaves: vanilla placeholder leaves by id until step
 * V7 adds the species' own leaf blocks (pine and spruce {@code spruce_leaves}, birch {@code birch_leaves}, hornbeam and
 * beech {@code dark_oak_leaves}, oak, linden, maple and ash {@code oak_leaves}), with the planner's distance.
 *
 * @param species a species with a planner shape ({@link SpeciesShape#supported})
 */
public record ProceduralTreeFeature(Species species, CrownLayer layer, AgeClass age) implements Feature {
	private static final Codec<Species> SPECIES = TreePalette.SPECIES_CODEC.validate(s -> SpeciesShape.supported(s)
			? DataResult.success(s) : DataResult.error(() -> "no tree shape for " + s.id()));
	public static final MapCodec<ProceduralTreeFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			SPECIES.fieldOf("species").forGetter(ProceduralTreeFeature::species),
			enumCodec(CrownLayer.values()).optionalFieldOf("layer", CrownLayer.CANOPY).forGetter(ProceduralTreeFeature::layer),
			enumCodec(AgeClass.values()).optionalFieldOf("age_class", AgeClass.MATURE).forGetter(ProceduralTreeFeature::age))
			.apply(i, ProceduralTreeFeature::new));

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

	/** Plants the tree with its trunk base at {@code origin}, which must be free with a sturdy block below. */
	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos below = origin.below();
		if (origin.getY() <= level.getMinY() || !replaceable(level.getBlockState(origin))
				|| !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
			return false;
		}
		TreeVoxels v = TreeShapePlan.plan(new TreeRequest(species, layer, age, level.getSeed(), origin.getX(), origin.getY(),
				origin.getZ()));
		if (write(level, v, species) == 0) {
			return false;
		}
		ModFeatures.woodyPlaced(origin, species);
		return true;
	}

	/**
	 * Writes a planned tree (see the class comment).
	 *
	 * @return the number of wood blocks written (0 when even the root could not be)
	 */
	public static int write(WorldGenLevel level, TreeVoxels v, Species species) {
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
			placeable[i] = pos.getY() < level.getMaxY() && replaceable(level.getBlockState(pos));
			all &= placeable[i];
		}
		byte[] distances = all ? null : v.leafDistances(written, placeable);
		BlockState leaf = leaves(species);
		for (int i = 0; i < leaves; i++) {
			int d = distances == null ? v.leafDistance(i) : distances[i];
			if (d < 1 || !placeable[i]) {
				continue;
			}
			pos.set(v.x() + v.leafX(i), v.y() + v.leafY(i), v.z() + v.leafZ(i));
			level.setBlock(pos, leaf.setValue(LeavesBlock.DISTANCE, d), FLAGS);
		}
		return count;
	}

	private static boolean replaceable(BlockState state) {
		return state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES);
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

	/** A wood block: a thin trunk of radius 1–7 px or, for {@link TreeVoxels#FULL}, a vanilla log along the parent axis. */
	public static BlockState wood(Species species, int bark, int radius, Direction parent) {
		TreeTrunk t = trunk(species, bark);
		if (radius >= TreeVoxels.FULL) {
			Block log = t.log();
			BlockState s = log.defaultBlockState();
			return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, parent.getAxis()) : s;
		}
		return ModBlocks.trunk(t).state(radius, parent);
	}

	/** The placeholder leaves of a species until step V7 (vanilla blocks by id), without a distance. */
	public static BlockState leaves(Species species) {
		Block b = switch (species) {
			case SCOTS_PINE, SPRUCE -> Blocks.SPRUCE_LEAVES;
			case BIRCH -> Blocks.BIRCH_LEAVES;
			case HORNBEAM, BEECH -> Blocks.DARK_OAK_LEAVES;
			default -> Blocks.OAK_LEAVES;
		};
		return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
	}
}
