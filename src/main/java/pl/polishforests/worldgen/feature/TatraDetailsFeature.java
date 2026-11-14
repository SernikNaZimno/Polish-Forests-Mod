package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.WoodBlocks;
import pl.polishforests.block.WoodSet;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.tatra.BoulderPlan;
import pl.polishforests.worldgen.feature.tatra.HutPlan;
import pl.polishforests.worldgen.feature.tatra.MouthPlan;
import pl.polishforests.worldgen.feature.tatra.Piece;
import pl.polishforests.worldgen.feature.tatra.Placement;
import pl.polishforests.worldgen.feature.tatra.TatraSites;
import pl.polishforests.worldgen.feature.tatra.TerrainView;
import pl.polishforests.worldgen.feature.tatra.WaterPlan;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Feature type {@code polishforests:tatra_details}, placed once per chunk as {@code polishforests:tatra/details} at the end
 * of step 9 (step TT6 of docs/04-pionowy-wycinek.md): the details of the Tatras that the terrain and the vegetation
 * dispatchers do not make. It does nothing outside a "Poland" world with the Tatras and outside the reach of a massif (a
 * box test of the chunk), so it costs nothing elsewhere and changes nothing there.
 * <ul>
 * <li>The anchors of the chunk ({@link TatraSites}: waterfall, karst spring, cave mouth, shepherd's hut) are built from the
 * plans of {@code feature.tatra} ({@link WaterPlan}, {@link MouthPlan}, {@link HutPlan}), each over the ground of the
 * 3 x 3 chunks of the write window ({@link Region}) and only where the plan says it fits (water that stays in its cells,
 * a tunnel with rock over it and under it, a flat site for a hut).</li>
 * <li>Boulders ({@link BoulderPlan}) on the moraines, the valley floors and the block fields (the scree, cirque and gully
 * landforms above 1000 m in patches of a field of its own), a few erratic ones elsewhere on the rock, with rock lichens on
 * some of their tops; at a block field the ground around the larger ones turns to scree.</li>
 * </ul>
 * Every random value comes from a source hashed from the world seed and the place, never from the feature's random source,
 * so the result depends on neither the order of generation nor the index of the feature in the step. Writes stay in the
 * window of the chunk and its eight neighbors (the anchors keep the extent of their kind inside it,
 * {@link TatraSites.Kind#extent()}).
 */
public final class TatraDetailsFeature implements Feature {
	public static final TatraDetailsFeature INSTANCE = new TatraDetailsFeature();
	public static final MapCodec<TatraDetailsFeature> CODEC = MapCodec.unit(() -> INSTANCE);

	/** Salt of the field of the block fields ({@code decoration.tatra.block_field}) and of the boulder candidates. */
	private static final long FIELD_SALT = 0x6B3D_F20A_91C5_E748L;
	private static final long BOULDER_SALT = 0x1C85_7AE4_D3F9_2B60L;
	/** Boulder candidates per chunk. */
	static final int CANDIDATES = 14;
	/** Lowest ground of the block fields (m): the cirque floors and above. */
	static final double FIELD_FROM = 1_000;

	/** Counts of the placed details (diagnostics of the game tests). */
	public static final LongAdder WATERFALLS = new LongAdder();
	public static final LongAdder SPRINGS = new LongAdder();
	public static final LongAdder MOUTHS = new LongAdder();
	public static final LongAdder HUTS = new LongAdder();
	public static final LongAdder BOULDERS = new LongAdder();
	/** Anchors whose plan found no place (the ground did not hold the detail). */
	public static final LongAdder REJECTED = new LongAdder();

	/**
	 * Observer of the placed details (the game test {@code tatra}): the kind, the anchor or center, and the placements (null
	 * for a boulder, whose block count is {@code blocks}). Null outside the game tests; called on the generation threads.
	 */
	public interface Placements {
		void placed(TatraSites.Kind kind, BlockPos at, @Nullable List<Placement> placements, int blocks);

		void boulder(BlockPos at, int radius);
	}

	public static volatile @Nullable Placements OBSERVER;

	private TatraDetailsFeature() {
	}

	@Override
	public MapCodec<TatraDetailsFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!(generator instanceof PolandChunkGenerator poland)) {
			return false;
		}
		long seed = level.getSeed();
		LandscapeModel m = poland.model(seed);
		int cx = origin.getX() >> 4;
		int cz = origin.getZ() >> 4;
		if (!m.hasTatra() || !m.tatraNear(cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15)) {
			return false;
		}
		ChunkAccess chunk = level.getChunk(cx, cz);
		ChunkHabitats h = ModFeatures.habitats(chunk, generator, seed);
		if (h == null) {
			return false;
		}
		Region region = new Region(level, chunk, poland, seed, h);
		boolean presentDay = poland.presentDay();
		boolean any = false;
		for (TatraSites.Site site : TatraSites.inChunk(m, cx, cz, presentDay, (x, z) -> h.code((int) x & 15, (int) z & 15))) {
			try {
				any |= build(level, region, site);
			} catch (Region.OutOfWindow e) {
				REJECTED.increment();
			}
		}
		try {
			any |= boulders(level, region, m, seed, cx, cz, h);
		} catch (Region.OutOfWindow e) {
			REJECTED.increment();
		}
		return any;
	}

	private boolean build(WorldGenLevel level, Region region, TatraSites.Site site) {
		List<Placement> plan;
		int y;
		switch (site.kind()) {
			case WATERFALL -> {
				WaterPlan.Plan p = WaterPlan.waterfall(region, site.x(), site.z(), site.seed());
				plan = p == null ? null : p.placements();
				y = p == null ? 0 : p.y();
			}
			case SPRING -> {
				WaterPlan.Plan p = WaterPlan.spring(region, site.x(), site.z(), site.seed());
				plan = p == null ? null : p.placements();
				y = p == null ? 0 : p.y();
			}
			case CAVE_MOUTH -> {
				MouthPlan.Plan p = MouthPlan.mouth(region, site.x(), site.z(), site.seed());
				plan = p == null ? null : p.placements();
				y = p == null ? 0 : p.y();
			}
			case SHEPHERD_HUT -> {
				HutPlan.Plan p = HutPlan.hut(region, site.x(), site.z(), site.seed());
				plan = p == null ? null : p.placements();
				y = p == null ? 0 : p.floor();
			}
			default -> {
				plan = null;
				y = 0;
			}
		}
		if (plan == null) {
			REJECTED.increment();
			return false;
		}
		apply(level, region, plan, site.rock());
		switch (site.kind()) {
			case WATERFALL -> WATERFALLS.increment();
			case SPRING -> SPRINGS.increment();
			case CAVE_MOUTH -> MOUTHS.increment();
			case SHEPHERD_HUT -> HUTS.increment();
		}
		Placements o = OBSERVER;
		if (o != null) {
			o.placed(site.kind(), new BlockPos(site.x(), y, site.z()), plan, plan.size());
		}
		return true;
	}

	/** Writes the plan into the window (the cells outside it are skipped). */
	private static void apply(WorldGenLevel level, Region region, List<Placement> plan, Substrate rock) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Placement p : plan) {
			if (!region.inWindow(p.x(), p.z())) {
				continue;
			}
			BlockState state = state(p, rock);
			if (state == null) {
				continue;
			}
			pos.set(p.x(), p.y(), p.z());
			if (p.piece() == Piece.LICHEN && !(level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced())) {
				continue;
			}
			level.setBlock(pos, state, 2);
		}
	}

	private static @Nullable BlockState state(Placement p, Substrate rock) {
		return switch (p.piece()) {
			case AIR -> Blocks.AIR.defaultBlockState();
			case ROCK -> rock(rock).defaultBlockState();
			case SCREE -> scree(rock).defaultBlockState();
			case SOURCE -> Blocks.WATER.defaultBlockState();
			case FLOW_LIP -> ((FlowingFluid) Fluids.FLOWING_WATER).getFlowing(7, false).createLegacyBlock();
			case FALL -> ((FlowingFluid) Fluids.FLOWING_WATER).getFlowing(8, true).createLegacyBlock();
			case LICHEN -> ModBlocks.ground(GroundPlant.ROCK_LICHEN).defaultBlockState()
					.setValue(LeafLitterBlock.AMOUNT, Math.clamp(p.param(), 2, 4));
			case PLANKS -> wood().planks().defaultBlockState();
			case LOG -> {
				BlockState s = wood().log().defaultBlockState();
				yield s.hasProperty(BlockStateProperties.AXIS)
						? s.setValue(BlockStateProperties.AXIS, p.param() == 0 ? Direction.Axis.X : Direction.Axis.Z) : s;
			}
			case STAIRS -> wood().stairs().defaultBlockState().setValue(StairBlock.FACING, direction(p.param()));
			case HAY -> Blocks.HAY_BLOCK.defaultBlockState();
		};
	}

	private static WoodBlocks wood() {
		return ModBlocks.wood(WoodSet.LARCH);
	}

	private static Direction direction(int d) {
		return switch (d & 3) {
			case 0 -> Direction.NORTH;
			case 1 -> Direction.EAST;
			case 2 -> Direction.SOUTH;
			default -> Direction.WEST;
		};
	}

	/** The block of the rock of a Tatra column (granite, gneiss, limestone, dolomite). */
	static Block rock(Substrate rock) {
		List<Block> rocks = ModBlocks.tatraRocks();
		return switch (rock) {
			case CRYSTALLINE -> rocks.get(1);
			case LIMESTONE -> rocks.get(2);
			case DOLOMITE -> rocks.get(3);
			default -> rocks.get(0);
		};
	}

	/** The scree of the rock: granite scree on the granite and the gneiss, limestone scree on the carbonate rock. */
	static Block scree(Substrate rock) {
		List<Block> surface = ModBlocks.tatraSurface();
		return rock == Substrate.LIMESTONE || rock == Substrate.DOLOMITE ? surface.get(1) : surface.get(0);
	}

	// ------------------------------------------------------------------ boulders

	private boolean boulders(WorldGenLevel level, Region region, LandscapeModel m, long seed, int cx, int cz, ChunkHabitats h) {
		Noise field = new Noise(seed ^ FIELD_SALT);
		boolean any = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < CANDIDATES; i++) {
			int lx = (int) (WaterPlan.unit(seed ^ BOULDER_SALT, cx * 64 + i, cz * 3 + 1) * 16);
			int lz = (int) (WaterPlan.unit(seed ^ BOULDER_SALT, cx * 64 + i, cz * 3 + 2) * 16);
			int idx = ChunkHabitats.index(lx, lz);
			if (h.hasWater(idx) || h.caveOpened(idx) || h.blocked(idx) || h.pieceDistance(idx) <= 4) {
				continue;
			}
			Zone zone = Habitat.zone(h.codes()[idx]);
			if (zone == Zone.CHANNEL || zone == Zone.SNOW_PATCH) {
				continue;
			}
			int x = (cx << 4) + lx;
			int z = (cz << 4) + lz;
			ColumnSample s = m.sample(x, z);
			Substrate rock = s.terrain().rock();
			if (rock == null || s.terrain().wTatra() < 0.5 || s.hasWater()) {
				continue;
			}
			int top = h.top()[idx];
			int steep = Math.max(Math.max(Math.abs(region.top(x + 1, z) - top), Math.abs(region.top(x - 1, z) - top)),
					Math.max(Math.abs(region.top(x, z + 1) - top), Math.abs(region.top(x, z - 1) - top)));
			if (steep > 1 || !region.solid(x, top, z)) {
				continue;
			}
			int bits = s.terrain().landformBits();
			double height = s.surface();
			double patch = field.fbm(x, z, 260, 2, 0.5);
			double u = WaterPlan.unit(seed ^ BOULDER_SALT, cx * 64 + i, cz * 3 + 3);
			boolean blockField = height >= FIELD_FROM && (bits & (Landform.SCREE.bit() | Landform.CIRQUE.bit() | Landform.MORAINE.bit()
					| Landform.GULLY.bit() | Landform.U_VALLEY.bit())) != 0 && patch > 0.2;
			int radius;
			if (blockField) {
				radius = u < 0.45 ? 1 : u < 0.8 ? 2 : 3;
			} else if ((bits & Landform.MORAINE.bit()) != 0) {
				if (u >= 0.55) {
					continue;
				}
				radius = u < 0.35 ? 1 : u < 0.52 ? 2 : 3;
			} else if ((bits & (Landform.U_VALLEY.bit() | Landform.MOUNTAIN_VALLEY.bit())) != 0) {
				if (u >= 0.25) {
					continue;
				}
				radius = u < 0.18 ? 1 : 2;
			} else {
				if (u >= 0.04) {
					continue;
				}
				radius = 1 + (int) (u * 50) % 2;
			}
			if (blockField && u >= 0.9) {
				continue;
			}
			List<Placement> plan = BoulderPlan.boulder(seed ^ BOULDER_SALT ^ x * 31L ^ z, x, top, z, radius,
					rock == Substrate.LIMESTONE || rock == Substrate.DOLOMITE ? 25 : 45);
			int placed = 0;
			BlockState stone = rock(rock).defaultBlockState();
			for (Placement p : plan) {
				if (!region.inWindow(p.x(), p.z())) {
					continue;
				}
				pos.set(p.x(), p.y(), p.z());
				BlockState old = level.getBlockState(pos);
				if (p.piece() == Piece.ROCK) {
					if (old.is(BlockTags.LOGS) || old.is(BlockTags.LEAVES) || !old.getFluidState().isEmpty() || old.hasBlockEntity()) {
						continue;
					}
					level.setBlock(pos, stone, 2);
					placed++;
				} else if (p.piece() == Piece.LICHEN && (old.isAir() || old.canBeReplaced()) && !old.is(BlockTags.LEAVES)) {
					level.setBlock(pos, state(p, rock), 2);
				}
			}
			if (placed == 0) {
				continue;
			}
			if (blockField && radius >= 2) {
				debris(level, region, rock, seed, x, z, radius);
			}
			BOULDERS.increment();
			any = true;
			Placements o = OBSERVER;
			if (o != null) {
				o.boulder(new BlockPos(x, top, z), radius);
			}
		}
		return any;
	}

	/** The ground around a large boulder of a block field turns to scree where it is soil (half of the columns within r + 2). */
	private static void debris(WorldGenLevel level, Region region, Substrate rock, long seed, int x, int z, int radius) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState scree = scree(rock).defaultBlockState();
		int reach = radius + 2;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (dx * dx + dz * dz > reach * reach || WaterPlan.unit(seed ^ 0x3A9D, x + dx, z + dz) < 0.5) {
					continue;
				}
				int wx = x + dx;
				int wz = z + dz;
				if (!region.inWindow(wx, wz) || !region.inWindow(wx - 1, wz - 1) || !region.inWindow(wx + 1, wz + 1)) {
					continue;
				}
				int top = region.top(wx, wz);
				pos.set(wx, top, wz);
				BlockState ground = level.getBlockState(pos);
				if (ground.is(BlockTags.SUBSTRATE_OVERWORLD) || ground.is(BlockTags.SAND)) {
					level.setBlock(pos, scree, 2);
					pos.set(wx, top + 1, wz);
					BlockState above = level.getBlockState(pos);
					if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ the ground of the window

	/**
	 * The ground the planners read ({@link TerrainView}): the planned tops of the columns of the chunk and its eight
	 * neighbors (the attachments of the chunk habitats; a neighbor without one, from a proto-chunk of an older version,
	 * computed again), and the blocks of the region. A read outside the window is an error ({@link OutOfWindow}): the anchors
	 * keep the extent of their kind inside it.
	 */
	static final class Region implements TerrainView {
		/** A read or write outside the 3 x 3 chunks of the window. */
		static final class OutOfWindow extends RuntimeException {
			OutOfWindow() {
				super("outside the write window", null, false, false);
			}
		}

		private final WorldGenLevel level;
		private final int chunkX;
		private final int chunkZ;
		private final short[][] tops = new short[9][];
		private final ChunkAccess[] chunks = new ChunkAccess[9];
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		Region(WorldGenLevel level, ChunkAccess chunk, PolandChunkGenerator generator, long seed, ChunkHabitats center) {
			this.level = level;
			this.chunkX = chunk.getPos().x();
			this.chunkZ = chunk.getPos().z();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int k = (dx + 1) * 3 + dz + 1;
					ChunkAccess c = dx == 0 && dz == 0 ? chunk : level.getChunk(chunkX + dx, chunkZ + dz);
					chunks[k] = c;
					ChunkHabitats h = dx == 0 && dz == 0 ? center : c.getAttached(ModFeatures.CHUNK_HABITATS);
					tops[k] = h != null ? h.top() : generator.computeHabitats(c.getPos(), c.getMinY(), c.getMaxY(), seed).top();
				}
			}
		}

		boolean inWindow(int x, int z) {
			int dx = (x >> 4) - chunkX;
			int dz = (z >> 4) - chunkZ;
			return dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1;
		}

		private int index(int x, int z) {
			int dx = (x >> 4) - chunkX;
			int dz = (z >> 4) - chunkZ;
			if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
				throw new OutOfWindow();
			}
			return (dx + 1) * 3 + dz + 1;
		}

		@Override
		public int top(int x, int z) {
			return tops[index(x, z)][ChunkHabitats.index(x & 15, z & 15)];
		}

		private BlockState state(int x, int y, int z) {
			index(x, z);
			return level.getBlockState(pos.set(x, y, z));
		}

		@Override
		public boolean solid(int x, int y, int z) {
			BlockState s = state(x, y, z);
			return s.isSolid() && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES);
		}

		@Override
		public boolean fluid(int x, int y, int z) {
			return !state(x, y, z).getFluidState().isEmpty();
		}

		@Override
		public int minY() {
			return level.getMinY();
		}
	}
}
