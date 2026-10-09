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
import pl.polishforests.worldgen.feature.config.PlantPalette;
import pl.polishforests.worldgen.feature.plan.ColumnPlan;
import pl.polishforests.worldgen.feature.plan.TreeStandPlan;

/**
 * A column layer of step 9 (docs/03-m2-biomy.md §8.2): {@code polishforests:deadwood}, {@code understory},
 * {@code waterside_zones}, {@code ground_layer} and {@code aquatic_plants}, each a feature type with its own palette
 * ({@link PlantPalette}, JSON) and its own salt. The plan is a pure function ({@link ColumnPlan}) of the chunk habitats
 * ({@link ChunkHabitats}, the levels taken from them) and the column data read from the world; this class only carries
 * it out. A block plant is set with flag 2 only where its place is free (air on land and above water, a water source on
 * the bottom) and it can survive: double plants get both halves ({@code DoublePlantBlock.placeAt}, waterlogged in water,
 * e.g. small dripleaf as cattail in water one block deep on mud), big dripleaf (butterbur) grows to a random height,
 * a block with a height above 1 is a column (sugar cane as reed). A feature plant (shrubs, fallen trees) is a placed
 * feature with its own filters, placed with the layer's random source from (world seed, chunk, layer salt).
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

	private final BiomeDecoration.Dispatcher dispatcher;
	private final PlantPalette palette;
	private final List<PlantPalette.Plant> plants = new ArrayList<>();
	private final ColumnPlan.Palette plan;
	private final boolean shore;
	private final long salt;

	public PlantLayerFeature(BiomeDecoration.Dispatcher dispatcher, PlantPalette palette) {
		this.dispatcher = dispatcher;
		this.palette = palette;
		this.salt = TreeStandPlan.SALT * 31 + dispatcher.ordinal() * 0x6A09_E667L;
		List<ColumnPlan.Rule> rules = new ArrayList<>();
		boolean anyShore = false;
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
			anyShore |= r.medium() == ColumnPlan.Medium.SHORE;
			rules.add(new ColumnPlan.Rule(r.condition().match(), r.medium(), r.minDepth(), r.maxDepth(), grounds, r.coverage(),
					r.patch(), index, weights));
		}
		this.plan = new ColumnPlan.Palette(rules);
		this.shore = anyShore;
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
		ColumnPlan.Columns columns = VegetationColumns.of(level, chunk, habitats, shore);
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
			ColumnPlan.Medium medium = ColumnPlan.medium(p);
			int top = habitats.top()[column];
			int y = medium == ColumnPlan.Medium.WATER_SURFACE ? habitats.water()[column] + 1 : top + 1;
			at.set(pos.getMinBlockX() + (column >> 4), y, pos.getMinBlockZ() + (column & 15));
			if (place(level, generator, random, plants.get(ColumnPlan.plant(p)), medium, at)) {
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
			BigDripleafBlock.placeWithRandomHeight(level, random, pos, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
			return true;
		}
		if (block instanceof DoublePlantBlock) {
			BlockPos above = pos.above();
			BlockState upper = level.getBlockState(above);
			boolean free = upper.isAir() || (block instanceof TallSeagrassBlock || state.hasProperty(BlockStateProperties.WATERLOGGED))
					&& isWaterSource(upper, level.getFluidState(above));
			if (!free || !state.canSurvive(level, pos)) {
				return false;
			}
			DoublePlantBlock.placeAt(level, state, pos, Block.UPDATE_CLIENTS);
			return true;
		}
		if (underwater && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			state = state.setValue(BlockStateProperties.WATERLOGGED, true);
		}
		if (!state.canSurvive(level, pos)) {
			return false;
		}
		int height = plant.minHeight() + (plant.maxHeight() > plant.minHeight()
				? random.nextInt(plant.maxHeight() - plant.minHeight() + 1) : 0);
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

	private static boolean isWaterSource(BlockState state, FluidState fluid) {
		return fluid.is(Fluids.WATER) && fluid.isSource() && state.is(net.minecraft.world.level.block.Blocks.WATER);
	}

	@Override
	public Stream<Holder<Feature>> getSubFeatures() {
		return plants.stream().flatMap(p -> p.feature().stream()).flatMap(f -> f.value().getFeatures());
	}
}
