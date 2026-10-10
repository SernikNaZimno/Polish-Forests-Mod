package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.config.PlantPalette;
import pl.polishforests.worldgen.feature.plan.ColumnPlan;
import pl.polishforests.worldgen.feature.plan.Ecotone;
import pl.polishforests.worldgen.feature.plan.TreeStandPlan;

/**
 * A column layer of step 9 (docs/03-m2-biomy.md §8.2): {@code polishforests:deadwood}, {@code understory},
 * {@code waterside_zones}, {@code ground_layer} and {@code aquatic_plants}, each a feature type with its own palette
 * ({@link PlantPalette}, JSON) and its own salt. The plan is a pure function ({@link ColumnPlan}) of the chunk habitats
 * ({@link ChunkHabitats}, the levels taken from them) and the column data read from the world; this class only carries
 * it out. A block plant is set with flag 2 only where its place is free (air on land and above water, a water source on
 * the bottom) and it can survive: double plants get both halves ({@code DoublePlantBlock.placeAt}, waterlogged in water,
 * e.g. small dripleaf as cattail in water one block deep on mud), big dripleaf (butterbur) is a leaf on a stem of the
 * plant's height range (1: the leaf on the ground; not the vanilla random height of 2–5), a block with a height above 1
 * is a column (sugar cane as reed). Both halves of a double plant out of water are marked for the post-processing of the
 * chunk, which removes a half whose other half or ground a later feature of a neighbor replaced (a fallen tree, a crown,
 * a sand disk). A feature plant (shrubs, fallen trees) is a placed feature with its own filters, placed with the layer's
 * random source from (world seed, chunk, layer salt).
 */
public final class PlantLayerFeature implements Feature {
	private static final Map<BiomeDecoration.Dispatcher, MapCodec<PlantLayerFeature>> CODECS = new EnumMap<>(
			BiomeDecoration.Dispatcher.class);

	static {
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			if (d != BiomeDecoration.Dispatcher.TREE_STAND) {
				CODECS.put(d, PlantPalette.Rule.CODEC.listOf().fieldOf("rules")
						.xmap(rules -> new PlantLayerFeature(d, new PlantPalette(rules)), f -> f.palette.rules()));
			}
		}
	}

	/** Codec of the feature type of a column layer (a separate instance for each, as the registry needs). */
	public static MapCodec<PlantLayerFeature> codec(BiomeDecoration.Dispatcher dispatcher) {
		return CODECS.get(dispatcher);
	}

	/**
	 * Least distance (blocks) of a feature plant (shrub, fallen tree) from a structure piece; block plants only stay off
	 * the footprints themselves (ChunkHabitats mask, {@code StructureGround}).
	 */
	public static final int FEATURE_GAP = 3;

	private final BiomeDecoration.Dispatcher dispatcher;
	private final PlantPalette palette;
	private final List<PlantPalette.Plant> plants = new ArrayList<>();
	private final ColumnPlan.Palette plan;
	private final boolean shore;
	private final boolean seaward;
	/** Whether a rule of the palette asks for a forest edge class (the mantle and fringe of step S8b). */
	private final boolean edges;
	private final long salt;

	public PlantLayerFeature(BiomeDecoration.Dispatcher dispatcher, PlantPalette palette) {
		this.dispatcher = dispatcher;
		this.palette = palette;
		this.salt = TreeStandPlan.SALT * 31 + dispatcher.ordinal() * 0x6A09_E667L;
		List<ColumnPlan.Rule> rules = new ArrayList<>();
		boolean anyShore = false;
		boolean anySeaward = false;
		boolean anyEdge = false;
		for (PlantPalette.Rule r : palette.rules()) {
			int[] index = new int[r.plants().size()];
			int[] weights = new int[r.plants().size()];
			for (int k = 0; k < index.length; k++) {
				PlantPalette.Plant p = r.plants().get(k);
				PlantPalette.Plant key = p.withWeight(1);
				int at = plants.indexOf(key);
				if (at < 0) {
					plants.add(key);
					at = plants.size() - 1;
				}
				index[k] = at;
				weights[k] = p.weight();
			}
			int grounds = 0;
			for (ColumnPlan.Ground g : r.grounds()) {
				grounds |= 1 << g.ordinal();
			}
			anyShore |= r.medium() == ColumnPlan.Medium.SHORE || r.medium() == ColumnPlan.Medium.PUDDLE_SHORE;
			anySeaward |= r.medium() == ColumnPlan.Medium.SEAWARD_EDGE;
			int edgeMask = 0;
			for (Ecotone.Edge e : r.edges()) {
				edgeMask |= 1 << e.ordinal();
			}
			anyEdge |= edgeMask != 0;
			rules.add(new ColumnPlan.Rule(r.condition().match(), r.medium(), r.minDepth(), r.maxDepth(), grounds, r.coverage(),
					r.patch(), index, weights, edgeMask));
		}
		this.plan = new ColumnPlan.Palette(rules);
		this.shore = anyShore;
		this.seaward = anySeaward;
		this.edges = anyEdge;
	}

	public BiomeDecoration.Dispatcher dispatcher() {
		return dispatcher;
	}

	public PlantPalette palette() {
		return palette;
	}

	/** The resolved plan palette (tests and the game test read its rules). */
	public ColumnPlan.Palette plan() {
		return plan;
	}

	public List<PlantPalette.Plant> plants() {
		return plants;
	}

	@Override
	public MapCodec<PlantLayerFeature> codec() {
		return CODECS.get(dispatcher);
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		long t0 = System.nanoTime();
		int placed = 0;
		try {
			placed = plant(level, generator, origin);
			return placed > 0;
		} finally {
			ModFeatures.STATS.get(dispatcher).add(System.nanoTime() - t0, placed);
		}
	}

	private int plant(WorldGenLevel level, ChunkGenerator generator, BlockPos origin) {
		ChunkAccess chunk = level.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
		ChunkHabitats habitats = ModFeatures.habitats(chunk, generator, level.getSeed());
		if (habitats == null) {
			return 0;
		}
		ChunkPos pos = chunk.getPos();
		ColumnPlan.Columns columns = VegetationColumns.of(level, chunk, habitats, shore, seaward);
		if (dispatcher == BiomeDecoration.Dispatcher.DEADWOOD || dispatcher == BiomeDecoration.Dispatcher.UNDERSTORY
				|| dispatcher == BiomeDecoration.Dispatcher.GROUND_LAYER) {
			// Ecotones (rule Z10, step S8b): the palette of each column from the code of a column across the border within
			// the belt of the pair, and the mantle and fringe of the forest edges (shared by the layers of the chunk; the
			// waterside zones and the aquatic plants follow the water, whose borders are sharp).
			double k = generator instanceof PolandChunkGenerator poland ? poland.settings().scale().landscape().local() : 1;
			ChunkEcotones e = ChunkEcotones.of(level, chunk, habitats, k);
			columns = columns.with(dispatcher == BiomeDecoration.Dispatcher.GROUND_LAYER ? e.ground() : e.plants(),
					edges ? e.edges() : null);
		}
		int[] planned = ColumnPlan.of(columns, plan, level.getSeed(), pos.x(), pos.z(), salt);
		if (planned.length == 0) {
			return 0;
		}
		WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(TreeStandPlan.seed(level.getSeed(), pos.x(),
				pos.z(), salt)));
		int placed = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int p : planned) {
			int column = ColumnPlan.column(p);
			PlantPalette.Plant plant = plants.get(ColumnPlan.plant(p));
			int piece = habitats.pieceDistance(column);
			if (piece == 0 || piece <= FEATURE_GAP && plant.feature().isPresent()) {
				// Round 1 of the S8 review: nothing on the footprint of a structure piece (moss carpet on the floor
				// planks of a house), no shrub or fallen tree reaching into it.
				continue;
			}
			ColumnPlan.Medium medium = ColumnPlan.medium(p);
			int top = habitats.top()[column];
			int y = medium == ColumnPlan.Medium.WATER_SURFACE ? habitats.water()[column] + 1 : top + 1;
			at.set(pos.getMinBlockX() + (column >> 4), y, pos.getMinBlockZ() + (column & 15));
			if (place(level, generator, random, plant, medium, at)) {
				placed++;
			}
		}
		return placed;
	}

	/** Places one plant at its position; false when the place is taken or the plant cannot survive there. */
	private static boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, PlantPalette.Plant plant,
			ColumnPlan.Medium medium, BlockPos.MutableBlockPos at) {
		boolean underwater = medium == ColumnPlan.Medium.WATER_BOTTOM;
		BlockState here = level.getBlockState(at);
		if (underwater ? !isWaterSource(here, level.getFluidState(at)) : !here.isAir()) {
			return false;
		}
		BlockPos pos = at.immutable();
		if (plant.feature().isPresent()) {
			return plant.feature().get().value().place(level, generator, random, pos);
		}
		BlockState state = plant.block().orElseThrow();
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random));
		}
		Block block = state.getBlock();
		if (block instanceof BigDripleafBlock) {
			if (!state.canSurvive(level, pos)) {
				return false;
			}
			int height = height(plant, random);
			Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			BlockPos.MutableBlockPos up = pos.mutable();
			int stems = 0;
			while (stems < height - 1 && level.getBlockState(up.move(Direction.UP)).isAir()) {
				stems++;
			}
			up.set(pos);
			for (int k = 0; k < stems; k++) {
				level.setBlock(up, Blocks.BIG_DRIPLEAF_STEM.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing),
						Block.UPDATE_CLIENTS);
				up.move(Direction.UP);
			}
			level.setBlock(up, state, Block.UPDATE_CLIENTS);
			return true;
		}
		if (block instanceof DoublePlantBlock) {
			BlockPos above = pos.above();
			BlockState upper = level.getBlockState(above);
			// Tall seagrass holds water in both halves, so its upper half needs water too (in air it would be a new water
			// source above the surface, which spills); a waterloggable plant may stand with its upper half in air or water.
			boolean free = block instanceof TallSeagrassBlock ? isWaterSource(upper, level.getFluidState(above))
					: upper.isAir() || state.hasProperty(BlockStateProperties.WATERLOGGED) && isWaterSource(upper, level.getFluidState(above));
			if (!free || !state.canSurvive(level, pos)) {
				return false;
			}
			DoublePlantBlock.placeAt(level, state, pos, Block.UPDATE_CLIENTS);
			ChunkAccess chunk = level.getChunk(pos);
			// Lower half first: the post-processing of a section goes in this order and removes the upper half after the
			// lower one; a waterlogged half is left alone (its fluid would be ticked).
			if (level.getFluidState(pos).isEmpty()) {
				chunk.markPosForPostProcessing(pos);
			}
			if (level.getFluidState(above).isEmpty()) {
				chunk.markPosForPostProcessing(above);
			}
			return true;
		}
		if (underwater && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			state = state.setValue(BlockStateProperties.WATERLOGGED, true);
		}
		if (!state.canSurvive(level, pos)) {
			return false;
		}
		int height = height(plant, random);
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		BlockPos.MutableBlockPos up = pos.mutable();
		for (int k = 1; k < height; k++) {
			up.move(Direction.UP);
			if (!level.getBlockState(up).isAir()) {
				break;
			}
			level.setBlock(up, state, Block.UPDATE_CLIENTS);
		}
		return true;
	}

	/** Height of a block plant: uniform in the plant's range. */
	private static int height(PlantPalette.Plant plant, RandomSource random) {
		return plant.minHeight() + (plant.maxHeight() > plant.minHeight()
				? random.nextInt(plant.maxHeight() - plant.minHeight() + 1) : 0);
	}

	private static boolean isWaterSource(BlockState state, FluidState fluid) {
		return fluid.is(Fluids.WATER) && fluid.isSource() && state.is(Blocks.WATER);
	}

	@Override
	public Stream<Holder<Feature>> getSubFeatures() {
		return plants.stream().flatMap(p -> p.feature().stream()).flatMap(f -> f.value().getFeatures());
	}
}
