package pl.polishforests.worldgen.cave;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.surface.ChunkSurface;
import pl.polishforests.worldgen.surface.Material;

/**
 * Vanilla caves in the "Poland" worlds (step J1 of docs/04-pionowy-wycinek.md; user decision CP-C.4, docs/00 G.26): the
 * noise caves of the vanilla overworld router ({@link CaveDensity}: cheese, spaghetti, noodles, pillars, entrances), the
 * vanilla carvers {@code minecraft:cave}, {@code cave_extra_underground} and {@code canyon} (the carvers of every vanilla
 * overworld biome, as one explicit list for all habitats) and the vanilla overworld aquifers (water and lava levels,
 * barriers, lava below Y -54), all by id from the registries of the world. The caves are a separate stage after the
 * surface plan: the landscape model and the plan do not change, the caves only turn ground blocks of the plan into air,
 * water or lava.
 *
 * <p>Additions to vanilla, needed by the 2.5D terrain: the depth below the surface comes from the model tops
 * ({@link ColumnField}); no cave comes within {@link #WATER_ROOF} blocks of the bed of the water of a column or of its 8
 * neighbors (the rivers and lakes of Poland lie above the sea level, where vanilla has no water, so its aquifers would let
 * caves drain them); and the ceilings and floors that a cave exposes follow the vanilla material rules (sand ceilings
 * turn to sandstone, gravel ceilings to stone, dirt floors under an opened grass top to the grass of the top).
 *
 * <p>Every result is a pure function of the world seed, the position and the settings: the density functions are
 * compiled by the {@link RandomState} of the world, the aquifer and the carvers are seeded by position as in vanilla.
 */
public final class PolandCaves {
	/** Least number of ground blocks between a cave and the bed of the water of its column or its neighbors. */
	public static final int WATER_ROOF = 4;
	/** Height of the volumes in which the density is sampled (a multiple of the vanilla cell height 8). */
	private static final int SLAB = 64;
	/** The vanilla overworld fluid levels (NoiseBasedChunkGenerator.createFluidPicker): lava below Y -54, the sea below Y 63. */
	private static final int LAVA_LEVEL = -54;
	private static final Identifier AQUIFER_RANDOM = Identifier.withDefaultNamespace("aquifer");

	/** Time of the caves in the chunk fill (ns) and number of chunks (diagnostics, budget of the TERRAIN stage). */
	public static final LongAdder NANOS = new LongAdder();
	public static final LongAdder CHUNKS = new LongAdder();
	/** Blocks carved by the noise caves and by the carvers (diagnostics). */
	public static final LongAdder NOISE_BLOCKS = new LongAdder();
	public static final LongAdder CARVER_BLOCKS = new LongAdder();

	private static volatile HolderLookup.@Nullable Provider serverRegistries;

	/**
	 * The model columns of the world, beyond the chunk too: Y of the top ground block and of the top water block
	 * ({@link ChunkSurface#NO_WATER} if none). Pure functions of the column.
	 */
	public interface Columns {
		int top(int x, int z);

		int water(int x, int z);
	}

	private final DensityFunction density;
	private final Aquifer.Config aquifers;
	private final List<Holder<WorldCarver>> carvers;
	private final Aquifer.FluidPicker fluids;
	private final int seaLevel;

	private PolandCaves(DensityFunction density, Aquifer.Config aquifers, List<Holder<WorldCarver>> carvers, int seaLevel) {
		this.density = density;
		this.aquifers = aquifers;
		this.carvers = carvers;
		this.seaLevel = seaLevel;
		Aquifer.FluidStatus lava = new Aquifer.FluidStatus(LAVA_LEVEL, Blocks.LAVA.defaultBlockState());
		Aquifer.FluidStatus sea = new Aquifer.FluidStatus(seaLevel, Blocks.WATER.defaultBlockState());
		this.fluids = (x, y, z) -> y < Math.min(LAVA_LEVEL, seaLevel) ? lava : sea;
	}

	/** The caves of a world of the given vertical scale with the density functions, noises and carvers of {@code registries}. */
	public static PolandCaves create(HolderLookup.Provider registries, VerticalScale vertical) {
		HolderGetter<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);
		HolderGetter<NormalNoise> noises = registries.lookupOrThrow(Registries.NOISE);
		HolderGetter<WorldCarver> carvers = registries.lookupOrThrow(Registries.CARVER);
		// The order of the vanilla default carvers (BiomeDefaultFeatures.addDefaultCarversAndLakes), which seeds each carver.
		List<Holder<WorldCarver>> list = List.of(carvers.getOrThrow(Carvers.CAVE), carvers.getOrThrow(Carvers.CAVE_EXTRA_UNDERGROUND),
				carvers.getOrThrow(Carvers.CANYON));
		return new PolandCaves(CaveDensity.build(functions, noises, vertical.minY(), vertical.maxY()),
				CaveDensity.aquifers(functions, noises), list, vertical.seaLevelY());
	}

	/** Registries of the running server (bound when it starts), for the generator calls that get no registries. */
	public static HolderLookup.@Nullable Provider serverRegistries() {
		return serverRegistries;
	}

	public static void bindServer(HolderLookup.@Nullable Provider registries) {
		serverRegistries = registries;
	}

	/** The cave density function (in the units of the vanilla final density; a cave where at most 0). */
	public DensityFunction density() {
		return density;
	}

	/**
	 * Highest Y that a cave may reach in a column whose 3 × 3 neighborhood has the given lowest water bed (the top ground
	 * block under water), or {@link Integer#MAX_VALUE} without water.
	 */
	public static int guardBelow(int lowestBed) {
		return lowestBed == Integer.MAX_VALUE ? Integer.MAX_VALUE : lowestBed - WATER_ROOF;
	}

	private static boolean carvable(Material m) {
		return m != Material.AIR && m != Material.WATER && m != Material.LAVA && m != Material.BEDROCK;
	}

	private static Material cave(BlockState state) {
		return state.is(Blocks.WATER) ? Material.WATER : state.is(Blocks.LAVA) ? Material.LAVA : Material.AIR;
	}

	/**
	 * The caves of a chunk over its surface plan.
	 *
	 * @param surface       the final surface plan of the chunk (with the ground of the structures)
	 * @param columns       the model columns of the world
	 * @param edgeNear      for each column of the chunk whether a neighbor beyond the chunk may hold water (then its model
	 *                      water is looked up for the guard of the water beds); null looks up every border neighbor
	 * @param beardifier    the vanilla beard of the structures of the chunk (keeps caves out of their foundations), or null
	 * @param carverContext the context of the vanilla carvers, or null for the noise caves alone
	 */
	public CaveCarving carve(ChunkPos pos, ChunkSurface surface, int minY, int maxY, RandomState randomState, Columns columns,
			boolean @Nullable [] edgeNear, @Nullable Beardifier beardifier, @Nullable WorldGenerationContext carverContext) {
		long t0 = System.nanoTime();
		int minX = pos.getMinBlockX();
		int minZ = pos.getMinBlockZ();
		int[] top = new int[256];
		int highest = minY;
		for (int i = 0; i < 256; i++) {
			top[i] = surface.top(i);
			highest = Math.max(highest, top[i]);
		}
		highest = Math.min(highest, maxY);
		int[] guard = guards(surface, columns, edgeNear, minX, minZ);
		CaveCarving out = new CaveCarving(minY, maxY - minY + 1);
		DensityBufferPool pool = randomState.acquireDensityBufferPool();
		try {
			ContextMap.Builder fields = ContextMap.builder()
					.set(ColumnField.DEPTH.key(), ColumnField.depth(columns))
					.set(ColumnField.LEVEL.key(), ColumnField.level(columns));
			if (beardifier != null) {
				fields.set(Beardifier.CONTEXT_KEY, beardifier);
			}
			SamplerContext context = SamplerContext.builder().setUserFields(fields.build()).useBufferArena(pool).enableCaches()
					.build();
			DensitySamplerSet samplers = randomState.samplersWithContext(context);
			DensitySampler.Bound sampler = samplers.get(density);
			DensityVolume chunkVolume = new DensityVolume(16, highest - minY + 1, 16, minX, minY, minZ);
			Aquifer aquifer = aquifers.create(samplers, randomState.getOrCreateRandomFactory(AQUIFER_RANDOM), chunkVolume, fluids);
			int noise = 0;
			for (int y0 = minY; y0 <= highest; y0 += SLAB) {
				int h = Math.min(SLAB, highest - y0 + 1);
				DensityVolume volume = new DensityVolume(16, h, 16, minX, y0, minZ);
				try (ScopedDensityBuffer buffer = sampler.sampleVolume(volume)) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							int i = x << 4 | z;
							int yTop = Math.min(y0 + h - 1, Math.min(top[i], guard[i]));
							int base = volume.indexUnchecked(x, 0, z);
							for (int y = yTop; y >= y0; y--) {
								float d = buffer.get(base + y - y0);
								if (d > 0.0F || !carvable(surface.material(i, y))) {
									continue;
								}
								BlockState state = aquifer.computeSubstance(minX + x, y, minZ + z, d);
								if (state != null) {
									put(out, aquifer, state, i, minX + x, y, minZ + z);
									noise++;
								}
							}
						}
					}
				}
			}
			NOISE_BLOCKS.add(noise);
			if (carverContext != null) {
				CARVER_BLOCKS.add(carvers(pos, out, surface, top, guard, aquifer, randomState, carverContext, highest));
			}
		} finally {
			randomState.releaseDensityBufferPool(pool);
		}
		finish(out, surface, top);
		NANOS.add(System.nanoTime() - t0);
		CHUNKS.increment();
		return out;
	}

	private static void put(CaveCarving out, Aquifer aquifer, BlockState state, int i, int x, int y, int z) {
		Material m = cave(state);
		out.set(i, y, m);
		if (m != Material.AIR && aquifer.shouldScheduleFluidUpdate()) {
			out.mark(x, y, z);
		}
	}

	/**
	 * The water guard of the 256 columns ({@link #guardBelow}): the lowest water bed of the 3 × 3 neighborhood of each
	 * column, from the plan inside the chunk and from the model columns beyond it.
	 */
	private static int[] guards(ChunkSurface surface, Columns columns, boolean @Nullable [] edgeNear, int minX, int minZ) {
		int[] bed = new int[18 * 18];
		java.util.Arrays.fill(bed, Integer.MAX_VALUE);
		for (int ex = -1; ex <= 16; ex++) {
			for (int ez = -1; ez <= 16; ez++) {
				int b = Integer.MAX_VALUE;
				if (ex >= 0 && ex < 16 && ez >= 0 && ez < 16) {
					int i = ex << 4 | ez;
					if (surface.wet(i)) {
						b = surface.top(i);
					}
				} else if (needsRing(edgeNear, ex, ez)) {
					int x = minX + ex;
					int z = minZ + ez;
					if (columns.water(x, z) != ChunkSurface.NO_WATER) {
						b = columns.top(x, z);
					}
				}
				bed[(ex + 1) * 18 + ez + 1] = b;
			}
		}
		int[] guard = new int[256];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int lowest = Integer.MAX_VALUE;
				for (int dx = 0; dx <= 2; dx++) {
					for (int dz = 0; dz <= 2; dz++) {
						lowest = Math.min(lowest, bed[(x + dx) * 18 + z + dz]);
					}
				}
				guard[x << 4 | z] = guardBelow(lowest);
			}
		}
		return guard;
	}

	/** Whether a column beyond the chunk at (ex, ez) borders a column of the chunk that may have water near. */
	private static boolean needsRing(boolean @Nullable [] edgeNear, int ex, int ez) {
		if (edgeNear == null) {
			return true;
		}
		for (int x = Math.max(0, ex - 1); x <= Math.min(15, ex + 1); x++) {
			for (int z = Math.max(0, ez - 1); z <= Math.min(15, ez + 1); z++) {
				if (edgeNear[x << 4 | z]) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The vanilla carvers ({@code NoiseBasedChunkGenerator.generateCarvers}): every carver of the explicit list started in
	 * the chunks within 8 chunks, seeded as vanilla by the world seed, its index and the source chunk; the carved blocks
	 * get the substance of the aquifer at density 0 (air, water, lava or a barrier), as {@code applyCarvingMask}.
	 */
	private int carvers(ChunkPos pos, CaveCarving out, ChunkSurface surface, int[] top, int[] guard, Aquifer aquifer,
			RandomState randomState, WorldGenerationContext context, int highest) {
		int maskTop = Math.min(context.getMinGenY() + context.getGenDepth() - 1 - 7, highest);
		if (maskTop <= context.getMinGenY() + 1) {
			return 0;
		}
		CarvingMask mask = new CarvingMask(context.getMinGenY() + 1, maskTop);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				ChunkPos source = new ChunkPos(pos.x() + dx, pos.z() + dz);
				for (int index = 0; index < carvers.size(); index++) {
					WorldCarver carver = carvers.get(index).value();
					random.setLargeFeatureSeed(randomState.seed() + index, source.x(), source.z());
					if (carver.isStartChunk(random)) {
						carver.carve(context, random, pos, source, mask);
					}
				}
			}
		}
		if (mask.isEmpty()) {
			return 0;
		}
		int minX = pos.getMinBlockX();
		int minZ = pos.getMinBlockZ();
		int[] count = new int[1];
		mask.visit((x, z, bottomY, topY) -> {
			int i = x << 4 | z;
			int yTop = Math.min(topY, Math.min(top[i], guard[i]));
			for (int y = yTop; y >= bottomY; y--) {
				if (out.carved(i, y)) {
					continue;
				}
				Material m = out.get(i, y);
				if (!carvable(m != null ? m : surface.material(i, y))) {
					continue;
				}
				BlockState state = aquifer.computeSubstance(minX + x, y, minZ + z, 0.0);
				if (state != null) {
					put(out, aquifer, state, i, minX + x, y, minZ + z);
					count[0]++;
				}
			}
		});
		return count[0];
	}

	/**
	 * Ceilings, floors and tops of the carved columns: a sand ceiling turns to sandstone and a gravel ceiling to stone
	 * (vanilla rules {@code sand_or_sandstone_if_ceiling}, {@code gravel_or_stone_if_ceiling}), so no falling block hangs
	 * over a cave; where an entrance opens the top of a column with grass or podzol, its dirt floor open to the sky takes
	 * the top block (as the vanilla carvers do); and the new ground and fluid tops for the heightmaps.
	 */
	private static void finish(CaveCarving out, ChunkSurface surface, int[] top) {
		for (int i = 0; i < 256; i++) {
			int lo = out.lowest(i);
			if (lo == Integer.MAX_VALUE) {
				out.tops(i, top[i], Integer.MIN_VALUE);
				continue;
			}
			int hi = out.highest(i);
			for (int y = lo; y <= hi; y++) {
				if (!out.carved(i, y) || out.carved(i, y + 1) || y + 1 > top[i]) {
					continue;
				}
				Material above = out.get(i, y + 1);
				if (above == null) {
					above = surface.material(i, y + 1);
				}
				if (above == Material.SAND) {
					out.set(i, y + 1, Material.SANDSTONE);
				} else if (above == Material.GRAVEL) {
					out.set(i, y + 1, Material.STONE);
				}
			}
			if (!out.carved(i, top[i])) {
				out.tops(i, top[i], Integer.MIN_VALUE);
				continue;
			}
			int fluid = Integer.MIN_VALUE;
			boolean open = true;
			int y = top[i];
			for (; y > lo - 1 && out.carved(i, y); y--) {
				Material m = out.get(i, y);
				if (m != Material.AIR) {
					open = false;
					fluid = Math.max(fluid, y);
				}
			}
			Material floor = out.get(i, y);
			if (floor == null) {
				floor = surface.material(i, y);
			}
			Material grass = surface.material(i, top[i]);
			if (open && floor == Material.DIRT && (grass == Material.GRASS_BLOCK || grass == Material.PODZOL)) {
				out.set(i, y, grass);
			}
			out.tops(i, y, fluid);
		}
	}

	/**
	 * Substances of the caves in one column between {@code lo} and {@code hi} (index {@code y - lo}): null where the ground
	 * stays, else air, water or lava, as the chunk fill computes them without the carvers and the structures (as the
	 * vanilla {@code getBaseColumn}). Blocks above {@code guardY} stay ground (the water guard).
	 */
	public @Nullable BlockState[] column(int x, int z, int lo, int hi, int guardY, RandomState randomState, Columns columns) {
		BlockState[] states = new BlockState[hi - lo + 1];
		int top = Math.min(hi, guardY);
		if (top < lo) {
			return states;
		}
		DensityBufferPool pool = randomState.acquireDensityBufferPool();
		try {
			ContextMap fields = ContextMap.builder().set(ColumnField.DEPTH.key(), ColumnField.depth(columns))
					.set(ColumnField.LEVEL.key(), ColumnField.level(columns)).build();
			SamplerContext context = SamplerContext.builder().setUserFields(fields).useBufferArena(pool).enableCaches().build();
			DensitySamplerSet samplers = randomState.samplersWithContext(context);
			DensityVolume volume = new DensityVolume(1, hi - lo + 1, 1, x, lo, z);
			Aquifer aquifer = aquifers.create(samplers, randomState.getOrCreateRandomFactory(AQUIFER_RANDOM), volume, fluids);
			try (ScopedDensityBuffer buffer = samplers.get(density).sampleVolume(volume)) {
				for (int y = top; y >= lo; y--) {
					float d = buffer.get(volume.indexUnchecked(0, y - lo, 0));
					if (d <= 0.0F) {
						states[y - lo] = aquifer.computeSubstance(x, y, z, d);
					}
				}
			}
		} finally {
			randomState.releaseDensityBufferPool(pool);
		}
		return states;
	}

	/** Sea level of the aquifers (Y of the top face of the sea). */
	public int seaLevel() {
		return seaLevel;
	}
}
