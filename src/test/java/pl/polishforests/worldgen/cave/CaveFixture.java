package pl.polishforests.worldgen.cave;

import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import pl.polishforests.block.GameRegistries;
import pl.polishforests.worldgen.chunk.LooseCover;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandDimension;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.PolandSettings;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.surface.ChunkSurface;
import pl.polishforests.worldgen.surface.SurfaceBuilder;

/**
 * The caves of chunks built as {@code PolandChunkGenerator.fill()} builds them (step J1): the surface plan of the chunk
 * with the thin cover of new worlds, then {@link PolandCaves#carve} with the vanilla registries
 * ({@link VanillaRegistries#createWorldLookup}) and a random state of the world seed as the server makes it for a
 * generator without a noise router. Without structures (no beard).
 */
final class CaveFixture {
	static final long SEED = 20260927L;
	private static HolderLookup.Provider registries;

	/** Plan and caves of one chunk with its samples. */
	record Chunk(int cx, int cz, ColumnSample[] columns, ChunkSurface surface, CaveCarving caves) {
	}

	final PolandScale scale;
	final VerticalScale vertical;
	final LandscapeModel model;
	final HabitatClassifier classifier;
	final SurfaceBuilder builder;
	final PolandCaves caves;
	final RandomState randomState;
	final PolandChunkGenerator generator;
	final WorldGenerationContext carverContext;

	CaveFixture(PolandScale scale) {
		this.scale = scale;
		this.vertical = scale.vertical();
		this.model = new LandscapeModel(SEED, scale.landscape(), 1.0);
		this.classifier = new HabitatClassifier(SEED, model, HabitatClassifier.Mode.NATURAL);
		this.builder = new SurfaceBuilder(SEED, vertical, true, LooseCover.THIN, false);
		HolderLookup.Provider r = registries();
		this.caves = PolandCaves.create(r, vertical);
		this.randomState = RandomState.create(r.lookupOrThrow(Registries.NOISE), SEED, false, Blocks.STONE.defaultBlockState(), 63,
				NoiseRouterData.none());
		this.generator = new PolandChunkGenerator(new FixedBiomeSource(r.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)),
				PolandSettings.DEFAULT.withScale(scale));
		this.carverContext = new WorldGenerationContext(generator, LevelHeightAccessor.create(vertical.minY(), vertical.height()));
	}

	/** The vanilla worldgen registries (built once per test JVM, after the game bootstrap). */
	static synchronized HolderLookup.Provider registries() {
		if (registries == null) {
			GameRegistries.bootstrap();
			registries = VanillaRegistries.createWorldLookup();
		}
		return registries;
	}

	int minY() {
		return vertical.minY();
	}

	int maxY() {
		return vertical.maxY();
	}

	/** The model columns of the world from the shared summaries. */
	PolandCaves.Columns columns() {
		return new PolandCaves.Columns() {
			@Override
			public int top(int x, int z) {
				return SurfaceBuilder.summaryTop(builder.cachedSummary(x, z, model::sample, minY(), maxY()));
			}

			@Override
			public int water(int x, int z) {
				return SurfaceBuilder.summaryWater(builder.cachedSummary(x, z, model::sample, minY(), maxY()));
			}
		};
	}

	/** Plan and caves of chunk (cx, cz), with the vanilla carvers when {@code carvers}. */
	Chunk chunk(int cx, int cz, boolean carvers) {
		ColumnSample[] columns = new ColumnSample[256];
		ChunkSurface surface = plan(cx, cz, columns);
		CaveCarving carving = caves.carve(new ChunkPos(cx, cz), surface, minY(), maxY(), randomState, columns(), null, null,
				carvers ? carverContext : null);
		return new Chunk(cx, cz, columns, surface, carving);
	}

	/** The surface plan of chunk (cx, cz) as the generator builds it. */
	ChunkSurface chunkPlan(int cx, int cz) {
		return plan(cx, cz, new ColumnSample[256]);
	}

	private ChunkSurface plan(int cx, int cz, ColumnSample[] columns) {
		int minX = cx << 4;
		int minZ = cz << 4;
		int[] codes = new int[256];
		for (int i = 0; i < 256; i++) {
			columns[i] = model.sample(minX + (i >> 4), minZ + (i & 15));
			codes[i] = classifier.classify(columns[i], minX + (i >> 4), minZ + (i & 15));
		}
		return builder.build(columns, codes, minX, minZ, minY(), maxY(), PolandDimension.DEEP_ROCK_Y, model::sample);
	}

	/** First point of a square spiral around (x0, z0) with the given step that passes {@code test}, or null. */
	static int[] find(int x0, int z0, int step, int steps, Predicate<int[]> test) {
		int x = 0;
		int z = 0;
		int dx = 1;
		int dz = 0;
		int leg = 1;
		int inLeg = 0;
		int turns = 0;
		for (int i = 0; i < steps; i++) {
			int[] p = {x0 + x * step, z0 + z * step};
			if (test.test(p)) {
				return p;
			}
			x += dx;
			z += dz;
			if (++inLeg == leg) {
				inLeg = 0;
				int t = dx;
				dx = -dz;
				dz = t;
				if (++turns % 2 == 0) {
					leg++;
				}
			}
		}
		return null;
	}
}
