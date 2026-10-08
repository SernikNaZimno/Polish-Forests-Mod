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
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;

/**
 * Biome source of the "Poland" world (docs/03-m2-biomy.md §3.5): the 36 habitat biomes of the mod
 * ({@link HabitatBiome}), chosen per column by {@link HabitatClassifier} (Z1, Z2: the biome depends only on the
 * column, not on the quart Y). The holders come from the biome registry ({@link RegistryOps#retrieveGetter}), so the
 * world preset only names the type: {@code "biome_source": {"type": "polishforests:poland"}}; the fields of the M1
 * source in old {@code level.dat} files are ignored, because a {@code MapCodec} skips unknown keys.
 *
 * <p>The landscape model, the classifier and the vertical scale are bound by {@link PolandChunkGenerator} in
 * {@code createState}, once the world seed is known, i.e. before {@code ServerLevel} creates its uncached resolver.
 * Every resolver reads the binding inside the lambda, so a resolver created before binding does not return a
 * fallback biome forever. A column is classified at its quart center, the same point at which {@code fill()} classifies
 * the block column, so the biome, the soil and the vegetation cannot diverge.
 */
public final class PolandBiomeSource extends BiomeSource {
	public static final MapCodec<PolandBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			RegistryOps.retrieveGetter(Registries.BIOME)
	).apply(i, i.stable(PolandBiomeSource::create)));

	/** Entries of the per-thread column cache, mapped directly by (qx, qz): structures ask the same column at several Y. */
	private static final int CACHE_SIZE = 256;

	/** Holders of the biomes in the order of {@link HabitatBiome}. */
	private final List<Holder<Biome>> biomes;
	private volatile @Nullable Binding binding;
	private final ThreadLocal<ColumnCache> cache = ThreadLocal.withInitial(ColumnCache::new);

	/** What the source needs to classify a column; one immutable object, so a reader sees a consistent state. */
	private record Binding(long seed, LandscapeModel model, HabitatClassifier classifier, VerticalScale vertical) {
	}

	/** Per-thread cache of column biomes, direct-mapped by (qx, qz) and tied to one binding. */
	private static final class ColumnCache {
		final long[] keys = new long[CACHE_SIZE];
		final byte[] biomes = new byte[CACHE_SIZE];
		@Nullable Binding binding;

		ColumnCache() {
			java.util.Arrays.fill(keys, Long.MIN_VALUE);
		}
	}

	private PolandBiomeSource(List<Holder<Biome>> biomes) {
		this.biomes = biomes;
	}

	/** A new source with the biomes from the registry (also for a new generator in the preset editor). */
	public static PolandBiomeSource create(HolderGetter<Biome> registry) {
		return new PolandBiomeSource(ModBiomeKeys.all().stream().<Holder<Biome>>map(registry::getOrThrow).toList());
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

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	/** All 36 biomes, fixed from decoding, independent of the mode and the seed. */
	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return biomes.stream();
	}

	/** Holder of a habitat biome. */
	public Holder<Biome> holder(HabitatBiome biome) {
		return biomes.get(biome.ordinal());
	}

	/** Habitat biome of the quart column (qx, qz), classified at its center. */
	private static HabitatBiome classify(Binding b, int qx, int qz) {
		int x = QuartPos.toBlock(qx) + 2;
		int z = QuartPos.toBlock(qz) + 2;
		ColumnSample s = b.model.sample(x, z);
		return Habitat.biome(b.classifier.classify(s, x, z));
	}

	/** Biome of the quart column through the per-thread cache. */
	private Holder<Biome> column(Binding b, int qx, int qz) {
		ColumnCache c = cache.get();
		if (c.binding != b) {
			java.util.Arrays.fill(c.keys, Long.MIN_VALUE);
			c.binding = b;
		}
		int slot = (qx & 15) << 4 | (qz & 15);
		long key = (long) qx << 32 | (qz & 0xFFFF_FFFFL);
		if (c.keys[slot] != key) {
			c.biomes[slot] = (byte) classify(b, qx, qz).ordinal();
			c.keys[slot] = key;
		}
		return biomes.get(c.biomes[slot]);
	}

	/** Biome before binding: only for a resolver used before {@code createState}, which should not happen. */
	private Holder<Biome> unbound() {
		return holder(HabitatBiome.HAY_MEADOW);
	}

	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) {
		return (qx, qy, qz) -> {
			Binding b = binding;
			return b == null ? unbound() : column(b, qx, qz);
		};
	}

	/**
	 * Resolver for one chunk: the 16 columns are classified up front (16 samples and classifications); the resolver
	 * ignores the quart Y.
	 */
	@Override
	public BiomeResolver createResolverForChunk(Climate.Sampler sampler, int minQuartX, int minQuartY, int minQuartZ,
			int quartSizeX, int quartSizeY, int quartSizeZ) {
		Binding b = binding;
		if (b == null) {
			return createResolver(sampler);
		}
		@SuppressWarnings("unchecked")
		Holder<Biome>[] columns = new Holder[quartSizeX * quartSizeZ];
		for (int ix = 0; ix < quartSizeX; ix++) {
			for (int iz = 0; iz < quartSizeZ; iz++) {
				columns[ix * quartSizeZ + iz] = holder(classify(b, minQuartX + ix, minQuartZ + iz));
			}
		}
		return (qx, qy, qz) -> {
			int ix = qx - minQuartX;
			int iz = qz - minQuartZ;
			if (ix < 0 || iz < 0 || ix >= quartSizeX || iz >= quartSizeZ) {
				return column(b, qx, qz);
			}
			return columns[ix * quartSizeZ + iz];
		};
	}

	/**
	 * {@code /locate biome}: one sample per column instead of one per sampled Y level (about 33 for the vanilla
	 * 64-block resolution), since the biome does not depend on Y, classified in parallel batches. The spiral and the
	 * order are as in vanilla, the Y of the result is the origin's.
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
		for (int i = 0; i < wanted.length; i++) {
			wanted[i] = candidates.contains(biomes.get(i));
		}
		// The columns of the vanilla spiral in batches; a batch is classified in parallel (the model and the classifier
		// are thread-safe), and the first hit in spiral order wins, so the result is the same as a sequential search.
		int sampleRadius = Math.floorDiv(searchRadius, sampleResolutionHorizontal);
		int[] xs = new int[LOCATE_BATCH];
		int[] zs = new int[LOCATE_BATCH];
		int n = 0;
		for (BlockPos.MutableBlockPos column : BlockPos.spiralAround(BlockPos.ZERO, sampleRadius, Direction.EAST,
				Direction.SOUTH)) {
			xs[n] = origin.getX() + column.getX() * sampleResolutionHorizontal;
			zs[n] = origin.getZ() + column.getZ() * sampleResolutionHorizontal;
			if (++n == LOCATE_BATCH) {
				Pair<BlockPos, Holder<Biome>> hit = firstHit(b, xs, zs, n, wanted, origin.getY());
				if (hit != null) {
					return hit;
				}
				n = 0;
			}
		}
		return n > 0 ? firstHit(b, xs, zs, n, wanted, origin.getY()) : null;
	}

	/** Number of spiral columns classified together in {@link #findClosestBiome3d}. */
	private static final int LOCATE_BATCH = 2_048;

	/** First column of the batch (in order) whose biome is wanted, or null. */
	private @Nullable Pair<BlockPos, Holder<Biome>> firstHit(Binding b, int[] xs, int[] zs, int n, boolean[] wanted, int y) {
		int[] found = new int[n];
		java.util.stream.IntStream.range(0, n).parallel().forEach(i ->
				found[i] = classify(b, QuartPos.fromBlock(xs[i]), QuartPos.fromBlock(zs[i])).ordinal());
		for (int i = 0; i < n; i++) {
			if (wanted[found[i]]) {
				return Pair.of(new BlockPos(xs[i], y, zs[i]), biomes.get(found[i]));
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
