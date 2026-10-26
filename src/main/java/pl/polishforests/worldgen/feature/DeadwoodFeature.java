package pl.polishforests.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.config.TreePalette;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.DeadwoodPlan;
import pl.polishforests.worldgen.tree.DeadwoodVoxels;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.StandMap;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Feature type {@code polishforests:procedural_deadwood} (step V11 of docs/04-pionowy-wycinek.md): one dead wood object
 * of the dead wood planner ({@link DeadwoodPlan}), the configured features {@code polishforests:deadwood/<species>} (a
 * dead tree: a log, a snag, a broken tree or a windthrow, by the species' way of dying) and
 * {@code polishforests:stump/<species>} (a stump), placed by the dead wood layer ({@link PlantLayerFeature}) in the forests
 * of the slice (decision PW-3 b for dead wood: the other forests keep the fallen trees of M2 under
 * {@code deadwood_substitute/<species>}).
 *
 * <p><b>Stands</b> (decision PW-5 b): the palettes give the dead trees of a natural stand (the reserves) and the stumps of a
 * managed one; at the base column the stand map of the world decides: a natural stand has no stumps, a managed stand keeps
 * {@link DeadwoodPlan#managedKeep} of the dead trees (the WISL means). The object's size follows the age class there.
 *
 * <p><b>Where.</b> The planner sees the ground of the decorated chunk and its eight neighbors (the write window) from the
 * chunk habitats; columns with water and columns of structure pieces and their surroundings (streets, buildings) are
 * unknown ground, so no wood lies there. Wood goes where the world holds air or a block trees replace, in the planner's
 * order from the root (a block whose parent could not be written is left out with its descendants), the soil of a root
 * plate on air or plants, a pit only in natural ground with nothing standing on it, moss carpet or leaf litter on free
 * ground beside the lying wood. Nothing of this changes the terrain model or the habitats (the golden test is unaffected).
 */
public record DeadwoodFeature(Species species, boolean stump) implements Feature {
	private static final Codec<Species> SPECIES = TreePalette.SPECIES_CODEC.validate(s -> SpeciesShape.supported(s)
			? DataResult.success(s) : DataResult.error(() -> "no tree shape for " + s.id()));
	public static final MapCodec<DeadwoodFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			SPECIES.fieldOf("species").forGetter(DeadwoodFeature::species),
			Codec.BOOL.optionalFieldOf("stump", false).forGetter(DeadwoodFeature::stump))
			.apply(i, DeadwoodFeature::new));

	/** Block update flags of the generation, as the trees. */
	private static final int FLAGS = 19;
	/** Least distance of dead wood from a structure piece (streets, buildings), as the feature plants of the layers. */
	public static final int PIECE_GAP = PlantLayerFeature.FEATURE_GAP;
	/** Objects placed, by kind (diagnostics for the game tests). */
	public static final Map<DeadwoodPlan.Kind, LongAdder> PLACED = new EnumMap<>(DeadwoodPlan.Kind.class);
	/**
	 * Picks not placed (diagnostics for the game tests, review of V11, round 1): the base column is not the ground of the
	 * habitats, the stand does not keep the pick (a stump in a natural stand, a dead tree a managed stand leaves out), the
	 * plan is empty, nothing of it could be written.
	 */
	public static final LongAdder SKIPPED_BASE = new LongAdder();
	public static final LongAdder NOT_KEPT = new LongAdder();
	public static final LongAdder EMPTY = new LongAdder();
	public static final LongAdder UNWRITTEN = new LongAdder();

	static {
		for (DeadwoodPlan.Kind k : DeadwoodPlan.Kind.values()) {
			PLACED.put(k, new LongAdder());
		}
	}

	public DeadwoodFeature {
		if (!SpeciesShape.supported(species)) {
			throw new IllegalArgumentException("no tree shape for " + species);
		}
	}

	@Override
	public MapCodec<DeadwoodFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (origin.getY() <= level.getMinY()) {
			return false;
		}
		long seed = level.getSeed();
		int x = origin.getX();
		int z = origin.getZ();
		StandMap stands = generator instanceof PolandChunkGenerator poland ? poland.standMap(seed)
				: StandMap.uniform(AgeClass.MATURE, true);
		Ground ground = new Ground(level, level.getChunk(x >> 4, z >> 4), generator, seed);
		if (ground.top(x, z) != origin.getY() - 1) {
			SKIPPED_BASE.increment();
			return false;
		}
		DeadwoodPlan.Request request = DeadwoodPlan.request(seed, species, stump, stands, x, origin.getY(), z, ground);
		if (request == null) {
			NOT_KEPT.increment();
			return false;
		}
		DeadwoodVoxels v = DeadwoodPlan.plan(request);
		if (v.empty()) {
			EMPTY.increment();
			return false;
		}
		if (write(level, v) == 0) {
			UNWRITTEN.increment();
			return false;
		}
		PLACED.get(request.kind()).increment();
		ModFeatures.deadwoodPlaced(origin, v);
		return true;
	}

	/**
	 * Writes a planned object (see the class comment); returns the wood blocks written.
	 */
	public static int write(WorldGenLevel level, DeadwoodVoxels v) {
		TreeVoxels t = v.wood();
		int n = t.woodCount();
		boolean[] written = new boolean[n];
		int count = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		if (n == 0 || t.y() >= level.getMaxY() || !replaceable(level.getBlockState(pos.set(t.x() + t.woodX(0), t.y() + t.woodY(0),
				t.z() + t.woodZ(0))))) {
			return 0;
		}
		// The pit of a windthrow before the wood: the roots on the plate's underside hang over it (review of V11, round 1).
		for (int p : v.pit()) {
			dig(level, t.x() + v.x(p), t.y() - 1, t.z() + v.z(p), v.y(p), pos);
		}
		for (int i = 0; i < n; i++) {
			int p = t.parentIndex(i);
			if (p >= 0 && !written[p]) {
				continue;
			}
			pos.set(t.x() + t.woodX(i), t.y() + t.woodY(i), t.z() + t.woodZ(i));
			if (pos.getY() >= level.getMaxY() || !replaceable(level.getBlockState(pos))) {
				continue;
			}
			level.setBlock(pos, ProceduralTreeFeature.wood(v.species(), t.bark(i), t.radius(i), Direction.from3DDataValue(t.parent(i))),
					FLAGS);
			written[i] = true;
			count++;
		}
		if (count == 0) {
			return 0;
		}
		for (int i = 0; i < v.soil().length; i++) {
			int s = v.soil()[i];
			pos.set(t.x() + v.x(s), t.y() + v.y(s), t.z() + v.z(s));
			BlockState here = level.getBlockState(pos);
			if (pos.getY() < level.getMaxY() && (here.isAir() || here.is(BlockTags.REPLACEABLE_BY_TREES) && !here.is(BlockTags.LEAVES))) {
				level.setBlock(pos, soil(v.soilType()[i]), Block.UPDATE_CLIENTS);
			}
		}
		long seed = level.getSeed();
		for (int i = 0; i < v.cover().length; i++) {
			int c = v.cover()[i];
			pos.set(t.x() + v.x(c), t.y(), t.z() + v.z(c));
			if (!level.getBlockState(pos).isAir()) {
				continue;
			}
			long h = Noise.mix(seed ^ Noise.key(pos.getX(), pos.getZ(), 0x0C0E_A11DL));
			BlockState state = v.coverType()[i] == DeadwoodPlan.COVER_MOSS ? Blocks.MOSS_CARPET.defaultBlockState()
					: Blocks.LEAF_LITTER.defaultBlockState().setValue(BlockStateProperties.SEGMENT_AMOUNT, 1 + (int) ((h >>> 8) & 3))
							.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue((int) ((h >>> 12) & 3)));
			if (state.canSurvive(level, pos)) {
				level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}
		}
		return count;
	}

	/**
	 * Lowers the ground of a column by {@code depth} blocks (a pit of a windthrow) where the top block at {@code top} is
	 * natural ground with free air or a single plant above and natural ground below the pit; the new floor is coarse dirt,
	 * rooted dirt or dirt.
	 */
	private static void dig(WorldGenLevel level, int x, int top, int z, int depth, BlockPos.MutableBlockPos pos) {
		BlockState above = level.getBlockState(pos.set(x, top + 1, z));
		boolean plant = !above.isAir();
		if (plant && (!above.is(BlockTags.REPLACEABLE_BY_TREES) || above.is(BlockTags.LEAVES) || above.getBlock() instanceof DoublePlantBlock
				|| !above.getFluidState().isEmpty())) {
			return;
		}
		for (int d = 0; d <= depth; d++) {
			if (!natural(level.getBlockState(pos.set(x, top - d, z)))) {
				return;
			}
		}
		if (plant) {
			level.setBlock(pos.set(x, top + 1, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (int d = 0; d < depth; d++) {
			level.setBlock(pos.set(x, top - d, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		long h = Noise.mix(Noise.key(x, z, 0x9171L));
		BlockState floor = (h & 3) == 0 ? Blocks.ROOTED_DIRT.defaultBlockState() : (h & 3) == 1 ? Blocks.DIRT.defaultBlockState()
				: Blocks.COARSE_DIRT.defaultBlockState();
		level.setBlock(pos.set(x, top - depth, z), floor, Block.UPDATE_CLIENTS);
	}

	/** Natural ground a pit may go into: the dirt family (grass, podzol, coarse and rooted dirt, mud, moss). */
	private static boolean natural(BlockState s) {
		return s.is(BlockTags.DIRT) && !s.is(Blocks.MUD) && !s.is(Blocks.MUDDY_MANGROVE_ROOTS);
	}

	private static BlockState soil(byte type) {
		return switch (type) {
			case DeadwoodPlan.SOIL_CORE -> Blocks.ROOTED_DIRT.defaultBlockState();
			case DeadwoodPlan.SOIL_RIM -> Blocks.COARSE_DIRT.defaultBlockState();
			default -> Blocks.DIRT.defaultBlockState();
		};
	}

	private static boolean replaceable(BlockState state) {
		return state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES);
	}

	/**
	 * The ground of the write window from the chunk habitats: the top ground block of each column of the decorated chunk
	 * and its eight neighbors, unknown on water, near structure pieces and outside.
	 */
	static final class Ground implements TreeShapePlan.Ground {
		private final int chunkX;
		private final int chunkZ;
		private final @Nullable ChunkHabitats[] habitats = new ChunkHabitats[9];

		Ground(WorldGenLevel level, ChunkAccess chunk, ChunkGenerator generator, long seed) {
			chunkX = chunk.getPos().x();
			chunkZ = chunk.getPos().z();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					habitats[(dx + 1) * 3 + dz + 1] = dx == 0 && dz == 0 ? ModFeatures.habitats(chunk, generator, seed)
							: level.getChunk(chunkX + dx, chunkZ + dz).getAttached(ModFeatures.CHUNK_HABITATS);
				}
			}
		}

		@Override
		public int top(int x, int z) {
			int dx = (x >> 4) - chunkX;
			int dz = (z >> 4) - chunkZ;
			if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
				return UNKNOWN;
			}
			ChunkHabitats h = habitats[(dx + 1) * 3 + dz + 1];
			if (h == null) {
				return UNKNOWN;
			}
			int i = ChunkHabitats.index(x & 15, z & 15);
			if (h.hasWater(i) || h.pieceDistance(i) <= PIECE_GAP) {
				return UNKNOWN;
			}
			return h.top()[i];
		}
	}
}
