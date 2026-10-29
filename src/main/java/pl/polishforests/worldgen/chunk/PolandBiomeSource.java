package pl.polishforests.worldgen.chunk;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.climate.PolandClimate;
import pl.polishforests.worldgen.cave.CaveBiomes;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;

/**
 * Biome source of the "Poland" world (docs/03-m2-biomy.md §3.5): the 36 habitat biomes of the mod
 * ({@link HabitatBiome}), chosen per column by {@link HabitatClassifier} (Z1, Z2: the habitat depends only on the
 * column, not on the quart Y), and in worlds with caves the vanilla cave biomes below the surface ({@link CaveBiomes}, step
 * J2: lush caves, dripstone caves, sulfur caves, deep dark, chosen by the vanilla climate and the depth below the model
 * top; never within {@link CaveBiomes#MIN_BLOCKS_BELOW_TOP} blocks of the top, so the biome at the surface, the colors of
 * the grass and the decoration of the surface stay those of the habitat). The holders come from the biome registry
 * ({@link RegistryOps#retrieveGetter}), so the world preset only names the type:
 * {@code "biome_source": {"type": "polishforests:poland"}}; the fields of the M1 source in old {@code level.dat} files are
 * ignored, because a {@code MapCodec} skips unknown keys. The cave biomes are always among the possible biomes (the source
 * is decoded without the settings of the generator); a world without caves never places them.
 *
 * <p>The landscape model, the classifier and the vertical scale are bound by {@link PolandChunkGenerator} in
 * {@code createState}, once the world seed is known, i.e. before {@code ServerLevel} creates its uncached resolver; the
 * cave biomes are bound by the generator of a world with caves as soon as the registries of the world are known (also in
 * {@code createState}). Every resolver reads the bindings inside the lambda, so a resolver created before binding does not
 * return a fallback biome forever. A column is classified at its quart center, the same point at which {@code fill()}
 * classifies the block column, so the biome, the soil and the vegetation cannot diverge.
 */
public final class PolandBiomeSource extends BiomeSource {
	public static final MapCodec<PolandBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			RegistryOps.retrieveGetter(Registries.BIOME)
	).apply(i, i.stable(PolandBiomeSource::create)));

	/** Entries of the per-thread column cache, mapped directly by (qx, qz): structures ask the same column at several Y. */
	private static final int CACHE_SIZE = 256;
	private static final int CAVES = CaveBiomes.KEYS.size();

	/** Holders of the biomes in the order of {@link HabitatBiome}. */
	private final List<Holder<Biome>> biomes;
	/** Holders of the vanilla cave biomes in the order of {@link CaveBiomes#KEYS}. */
	private final List<Holder<Biome>> caveBiomes;
	private volatile @Nullable Binding binding;
	/** The cave biomes of a world with caves (step J2), or null. */
	private volatile @Nullable CaveBiomes caves;
	private final ThreadLocal<ColumnCache> cache = ThreadLocal.withInitial(ColumnCache::new);

	/** What the source needs to classify a column; one immutable object, so a reader sees a consistent state. */
	private record Binding(long seed, LandscapeModel model, HabitatClassifier classifier, VerticalScale vertical) {
	}

	/**
	 * Per-thread cache of columns, direct-mapped by (qx, qz) and tied to one binding and one cave binding: the habitat, the
	 * Y of the model top and, with caves, the parts of the cave biome distances that do not depend on Y.
	 */
	private static final class ColumnCache {
		final long[] keys = new long[CACHE_SIZE];
		final byte[] biomes = new byte[CACHE_SIZE];
		final int[] tops = new int[CACHE_SIZE];
		final float[] smoothTops = new float[CACHE_SIZE];
		final long[] costs = new long[CACHE_SIZE * CAVES];
		/** Model tops at the points of the lattice of {@link CaveBiomes#smoothTop}, direct-mapped by the lattice cell. */
		final long[] latticeKeys = new long[LATTICE_CACHE];
		final int[] latticeTops = new int[LATTICE_CACHE];
		final long[] climate = new long[CaveBiomes.CLIMATE];
		@Nullable Binding binding;
		@Nullable CaveBiomes caves;

		ColumnCache() {
			java.util.Arrays.fill(keys, Long.MIN_VALUE);
			java.util.Arrays.fill(latticeKeys, Long.MIN_VALUE);
		}

		/** The level of the cave biomes at block (x, z) ({@link CaveBiomes.Relief#level}) from the cached lattice tops. */
		float level(Binding b, CaveBiomes c, int x, int z) {
			return c.relief().level((lx, lz) -> {
				int slot = (Math.floorDiv(lx, CaveBiomes.LATTICE) & 7) << 3 | (Math.floorDiv(lz, CaveBiomes.LATTICE) & 7);
				long key = (long) lx << 32 | (lz & 0xFFFF_FFFFL);
				if (latticeKeys[slot] != key) {
					latticeTops[slot] = top(b, b.model.sample(lx, lz));
					latticeKeys[slot] = key;
				}
				return latticeTops[slot];
			}, x, z);
		}
	}

	/** Entries of the per-thread cache of lattice tops (8 × 8 lattice cells). */
	private static final int LATTICE_CACHE = 64;

	private PolandBiomeSource(List<Holder<Biome>> biomes, List<Holder<Biome>> caveBiomes) {
		this.biomes = biomes;
		this.caveBiomes = caveBiomes;
	}

	/** A new source with the biomes from the registry (also for a new generator in the preset editor). */
	public static PolandBiomeSource create(HolderGetter<Biome> registry) {
		return new PolandBiomeSource(ModBiomeKeys.all().stream().<Holder<Biome>>map(registry::getOrThrow).toList(),
				CaveBiomes.KEYS.stream().<Holder<Biome>>map(registry::getOrThrow).toList());
	}

	/**
	 * Binds the landscape model and the classifier of the world. A second binding with other parameters (another
	 * seed, scale or mode) is logged, because the instance belongs to one generator.
	 */
	void bind(long seed, LandscapeModel model, HabitatClassifier classifier, VerticalScale vertical) {
		Binding old = binding;
		if (old != null && (old.seed != seed || old.model.scale() != model.scale()
				|| old.classifier.mode() != classifier.mode() || old.vertical != vertical)) {
			PolishForests.LOG.warn("Poland biome source bound again with other parameters (seed {} -> {}, scale {} -> {}, mode {} -> {})",
					old.seed, seed, old.model.scale(), model.scale(), old.classifier.mode(), classifier.mode());
		}
		binding = new Binding(seed, model, classifier, vertical);
	}

	/** Binds the cave biomes of a world with caves (step J2); null for none. */
	void bindCaves(@Nullable CaveBiomes caves) {
		this.caves = caves;
	}

	/** The bound cave biomes, or null in a world without caves (or before the registries of the world are known). */
	public @Nullable CaveBiomes caves() {
		return caves;
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	/** The 36 habitat biomes and the 4 vanilla cave biomes, fixed from decoding, independent of the mode and the seed. */
	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return Stream.concat(biomes.stream(), caveBiomes.stream());
	}

	/** Holder of a habitat biome. */
	public Holder<Biome> holder(HabitatBiome biome) {
		return biomes.get(biome.ordinal());
	}

	/** Holder of cave biome {@code k} ({@link CaveBiomes#KEYS}). */
	public Holder<Biome> caveHolder(int k) {
		return caveBiomes.get(k);
	}

	/** Whether the biome is one of the vanilla cave biomes. */
	public boolean isCaveBiome(Holder<Biome> biome) {
		return caveBiomes.contains(biome);
	}

	/** Y of the top block of the model at the quart center of (qx, qz), as {@code fill()} clamps it. */
	private static int top(Binding b, ColumnSample s) {
		return Mth.clamp(b.vertical.topBlockY(s.surface()), b.vertical.minY() + 1, b.vertical.maxY());
	}

	/** The cache slot of the quart column (qx, qz), filled for the binding (and the cave climate when {@code c}). */
	private static int slot(ColumnCache cc, Binding b, @Nullable CaveBiomes c, int qx, int qz) {
		if (cc.binding != b || cc.caves != c) {
			java.util.Arrays.fill(cc.keys, Long.MIN_VALUE);
			java.util.Arrays.fill(cc.latticeKeys, Long.MIN_VALUE);
			cc.binding = b;
			cc.caves = c;
		}
		int slot = (qx & 15) << 4 | (qz & 15);
		long key = (long) qx << 32 | (qz & 0xFFFF_FFFFL);
		if (cc.keys[slot] != key) {
			int x = QuartPos.toBlock(qx) + 2;
			int z = QuartPos.toBlock(qz) + 2;
			ColumnSample s = b.model.sample(x, z);
			cc.biomes[slot] = (byte) Habitat.biome(b.classifier.classify(s, x, z)).ordinal();
			cc.tops[slot] = top(b, s);
			if (c != null) {
				c.climate(QuartPos.toBlock(qx), QuartPos.toBlock(qz), cc.climate, 0);
				CaveBiomes.costs(cc.climate, 0, cc.costs, slot * CAVES);
				cc.smoothTops[slot] = cc.level(b, c, x, z);
			}
			cc.keys[slot] = key;
		}
		return slot;
	}

	/** Biome of the quart cell through the per-thread column cache: a cave biome below the surface, else the habitat. */
	private Holder<Biome> biome(Binding b, @Nullable CaveBiomes c, int qx, int qy, int qz) {
		ColumnCache cc = cache.get();
		int slot = slot(cc, b, c, qx, qz);
		if (c != null) {
			int k = CaveBiomes.chooseByCosts(cc.costs, slot * CAVES, cc.tops[slot], cc.smoothTops[slot], QuartPos.toBlock(qy));
			if (k != CaveBiomes.NONE) {
				return caveBiomes.get(k);
			}
		}
		return biomes.get(cc.biomes[slot]);
	}

	/** Habitat biome of the quart column (qx, qz) through the per-thread cache, whatever lies below the surface. */
	public Holder<Biome> columnBiome(int qx, int qz) {
		Binding b = binding;
		if (b == null) {
			return unbound();
		}
		ColumnCache cc = cache.get();
		return biomes.get(cc.biomes[slot(cc, b, caves, qx, qz)]);
	}

	/** Biome before binding: only for a resolver used before {@code createState}, which should not happen. */
	private Holder<Biome> unbound() {
		return holder(HabitatBiome.HAY_MEADOW);
	}

	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) {
		return (qx, qy, qz) -> {
			Binding b = binding;
			return b == null ? unbound() : biome(b, caves, qx, qy, qz);
		};
	}

	/**
	 * Resolver for one chunk: the columns are classified up front (one sample and classification each, and with caves the
	 * cave climate of each column); the quart Y only decides between the cave biomes and the habitat.
	 */
	@Override
	public BiomeResolver createResolverForChunk(Climate.Sampler sampler, int minQuartX, int minQuartY, int minQuartZ,
			int quartSizeX, int quartSizeY, int quartSizeZ) {
		Binding b = binding;
		if (b == null) {
			return createResolver(sampler);
		}
		CaveBiomes c = caves;
		int n = quartSizeX * quartSizeZ;
		@SuppressWarnings("unchecked")
		Holder<Biome>[] columns = new Holder[n];
		int[] tops = new int[n];
		float[] smoothTops = new float[n];
		long[] costs = c == null ? null : new long[n * CAVES];
		long[] climate = c == null ? null : new long[CaveBiomes.CLIMATE];
		ColumnCache cc = c == null ? null : cache.get();
		if (cc != null && (cc.binding != b || cc.caves != c)) {
			java.util.Arrays.fill(cc.keys, Long.MIN_VALUE);
			java.util.Arrays.fill(cc.latticeKeys, Long.MIN_VALUE);
			cc.binding = b;
			cc.caves = c;
		}
		for (int ix = 0; ix < quartSizeX; ix++) {
			for (int iz = 0; iz < quartSizeZ; iz++) {
				int qx = minQuartX + ix;
				int qz = minQuartZ + iz;
				int x = QuartPos.toBlock(qx) + 2;
				int z = QuartPos.toBlock(qz) + 2;
				ColumnSample s = b.model.sample(x, z);
				int i = ix * quartSizeZ + iz;
				columns[i] = holder(Habitat.biome(b.classifier.classify(s, x, z)));
				tops[i] = top(b, s);
				if (c != null) {
					c.climate(QuartPos.toBlock(qx), QuartPos.toBlock(qz), climate, 0);
					CaveBiomes.costs(climate, 0, costs, i * CAVES);
					smoothTops[i] = cc.level(b, c, x, z);
				}
			}
		}
		return (qx, qy, qz) -> {
			int ix = qx - minQuartX;
			int iz = qz - minQuartZ;
			if (ix < 0 || iz < 0 || ix >= quartSizeX || iz >= quartSizeZ) {
				return biome(b, c, qx, qy, qz);
			}
			int i = ix * quartSizeZ + iz;
			if (c != null) {
				int k = CaveBiomes.chooseByCosts(costs, i * CAVES, tops[i], smoothTops[i], QuartPos.toBlock(qy));
				if (k != CaveBiomes.NONE) {
					return caveBiomes.get(k);
				}
			}
			return columns[i];
		};
	}

	/**
	 * {@code /locate biome}: for a habitat, one sample per column instead of one per sampled Y level (about 33 for the
	 * vanilla 64-block resolution), since the habitat does not depend on Y, classified in parallel batches; the Y of the
	 * result is the origin's. For a cave biome (step J2) the column's sampled Y levels in the vanilla order
	 * ({@code Mth.outFromOrigin}), the first one with a wanted biome. The spiral and the order are as in vanilla.
	 */
	@Override
	public @Nullable Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos origin, int searchRadius,
			int sampleResolutionHorizontal, int sampleResolutionVertical, Predicate<Holder<Biome>> allowed,
			RandomState randomState, LevelReader level) {
		Set<Holder<Biome>> candidates = possibleBiomes().stream().filter(allowed).collect(Collectors.toUnmodifiableSet());
		Binding b = binding;
		if (candidates.isEmpty() || b == null) {
			return b == null ? super.findClosestBiome3d(origin, searchRadius, sampleResolutionHorizontal,
					sampleResolutionVertical, allowed, randomState, level) : null;
		}
		boolean[] wanted = new boolean[biomes.size()];
		boolean anyHabitat = false;
		for (int i = 0; i < wanted.length; i++) {
			wanted[i] = candidates.contains(biomes.get(i));
			anyHabitat |= wanted[i];
		}
		CaveBiomes c = caves;
		boolean[] wantedCaves = new boolean[CAVES];
		boolean anyCave = false;
		for (int k = 0; k < CAVES; k++) {
			wantedCaves[k] = c != null && candidates.contains(caveBiomes.get(k));
			anyCave |= wantedCaves[k];
		}
		if (!anyHabitat && !anyCave) {
			return null;
		}
		int[] sampleYs = anyCave ? Mth.outFromOrigin(origin.getY(), level.getMinY() + 1, level.getMaxY() + 1,
				sampleResolutionVertical).toArray() : null;
		Search search = new Search(b, c, wanted, wantedCaves, anyCave, sampleYs, origin.getY());
		// The columns of the vanilla spiral in batches; a batch is classified in parallel (the model, the classifier and the
		// compiled climate are thread-safe), and the first hit in spiral order wins, so the result is the same as a
		// sequential search.
		int sampleRadius = Math.floorDiv(searchRadius, sampleResolutionHorizontal);
		int[] xs = new int[LOCATE_BATCH];
		int[] zs = new int[LOCATE_BATCH];
		int n = 0;
		for (BlockPos.MutableBlockPos column : BlockPos.spiralAround(BlockPos.ZERO, sampleRadius, Direction.EAST,
				Direction.SOUTH)) {
			xs[n] = origin.getX() + column.getX() * sampleResolutionHorizontal;
			zs[n] = origin.getZ() + column.getZ() * sampleResolutionHorizontal;
			if (++n == LOCATE_BATCH) {
				Pair<BlockPos, Holder<Biome>> hit = firstHit(search, xs, zs, n);
				if (hit != null) {
					return hit;
				}
				n = 0;
			}
		}
		return n > 0 ? firstHit(search, xs, zs, n) : null;
	}

	/** Number of spiral columns classified together in {@link #findClosestBiome3d}. */
	private static final int LOCATE_BATCH = 2_048;

	/** What {@link #findClosestBiome3d} looks for. */
	private record Search(Binding b, @Nullable CaveBiomes caves, boolean[] wanted, boolean[] wantedCaves, boolean anyCave,
			int @Nullable [] sampleYs, int originY) {
	}

	/** First column of the batch (in order) with a wanted biome, or null. */
	private @Nullable Pair<BlockPos, Holder<Biome>> firstHit(Search s, int[] xs, int[] zs, int n) {
		// Per column: the Y of the hit (Integer.MIN_VALUE for none) and the biome (habitat ordinal, or -1 - cave index).
		int[] hitY = new int[n];
		int[] hitBiome = new int[n];
		java.util.stream.IntStream.range(0, n).parallel().forEach(i -> {
			hitY[i] = Integer.MIN_VALUE;
			int qx = QuartPos.fromBlock(xs[i]);
			int qz = QuartPos.fromBlock(zs[i]);
			int x = QuartPos.toBlock(qx) + 2;
			int z = QuartPos.toBlock(qz) + 2;
			ColumnSample sample = s.b.model.sample(x, z);
			int habitat = Habitat.biome(s.b.classifier.classify(sample, x, z)).ordinal();
			if (!s.anyCave) {
				if (s.wanted[habitat]) {
					hitY[i] = s.originY;
					hitBiome[i] = habitat;
				}
				return;
			}
			int top = top(s.b, sample);
			long[] climate = new long[CaveBiomes.CLIMATE];
			long[] costs = new long[CAVES];
			s.caves.climate(QuartPos.toBlock(qx), QuartPos.toBlock(qz), climate, 0);
			CaveBiomes.costs(climate, 0, costs, 0);
			ColumnCache cc = cache.get();
			if (cc.binding != s.b || cc.caves != s.caves) {
				java.util.Arrays.fill(cc.keys, Long.MIN_VALUE);
				java.util.Arrays.fill(cc.latticeKeys, Long.MIN_VALUE);
				cc.binding = s.b;
				cc.caves = s.caves;
			}
			float smoothTop = cc.level(s.b, s.caves, x, z);
			for (int y : s.sampleYs) {
				int k = CaveBiomes.chooseByCosts(costs, 0, top, smoothTop, QuartPos.toBlock(QuartPos.fromBlock(y)));
				if (k != CaveBiomes.NONE ? s.wantedCaves[k] : s.wanted[habitat]) {
					hitY[i] = y;
					hitBiome[i] = k != CaveBiomes.NONE ? -1 - k : habitat;
					return;
				}
			}
		});
		for (int i = 0; i < n; i++) {
			if (hitY[i] != Integer.MIN_VALUE) {
				Holder<Biome> biome = hitBiome[i] >= 0 ? biomes.get(hitBiome[i]) : caveBiomes.get(-1 - hitBiome[i]);
				return Pair.of(new BlockPos(xs[i], hitY[i], zs[i]), biome);
			}
		}
		return null;
	}

	/** F3 (server side): habitat of the column at the player's feet (biome, site type, zone, association, soil, DGW, O, P, T). */
	@Override
	public void addDebugInfo(List<String> result, BlockPos feetPos, Climate.Sampler sampler) {
		Binding b = binding;
		if (b == null) {
			return;
		}
		int x = feetPos.getX();
		int z = feetPos.getZ();
		ColumnSample s = b.model.sample(x, z);
		Habitat h = b.classifier.habitat(s, x, z);
		result.add(String.format(Locale.ROOT, "Habitat: %s, site %s, zone %s, %s, soil %s",
				PolishForests.id(h.biome().id()), h.siteType().code(), h.zone().id(),
				h.association().name().toLowerCase(Locale.ROOT), h.soil().name().toLowerCase(Locale.ROOT)));
		result.add(String.format(Locale.ROOT, "DGW %.1f m, O %.2f, P %.2f, T %.3f (%s mode)", b.classifier.dgw(s, x, z),
				s.region().oceanicity(), s.region().mountainInfluence(),
				PolandClimate.temperature(h.biome().temperature(), b.vertical, x, feetPos.getY(), z),
				b.classifier.mode().name().toLowerCase(Locale.ROOT)));
	}
}
