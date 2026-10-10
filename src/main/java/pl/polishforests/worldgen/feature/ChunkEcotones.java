package pl.polishforests.worldgen.feature;

import java.util.Arrays;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.feature.plan.Ecotone;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Ecotones of the chunk being decorated (rule Z10, step S8b; {@link Ecotone}), computed once and shared by the soil
 * ecotones and the dispatcher layers of the chunk: the decoration of a chunk runs on one thread, so the last chunk of
 * the thread is kept. The values are pure functions of the habitats of the chunk and its neighbors and the world seed,
 * so the cache never changes a result.
 */
public final class ChunkEcotones {
	private static final ThreadLocal<ChunkEcotones> LAST = new ThreadLocal<>();
	/** Salt of the noise of the forest edge widths (a new field, a new salt; step S8b). */
	private static final String EDGE_SALT = "feature.ecotone.edges";

	private final long seed;
	private final ChunkHabitats habitats;
	private final Ecotone.Region region;
	private int @Nullable [] trees;
	private int @Nullable [] plants;
	private int @Nullable [] ground;
	private byte @Nullable [] edges;
	private int @Nullable [] mantle;
	private static volatile @Nullable Widths widths;

	/** Noise of the forest edge widths of a world seed. */
	private record Widths(long seed, Noise noise) {
	}

	private ChunkEcotones(long seed, ChunkHabitats habitats, Ecotone.Region region) {
		this.seed = seed;
		this.habitats = habitats;
		this.region = region;
	}

	/** Ecotones of the chunk with these habitats. */
	public static ChunkEcotones of(WorldGenLevel level, ChunkAccess chunk, ChunkHabitats habitats, double k) {
		ChunkEcotones e = LAST.get();
		long seed = level.getSeed();
		if (e != null && e.habitats == habitats && e.seed == seed) {
			return e;
		}
		e = new ChunkEcotones(seed, habitats, VegetationColumns.region(level, chunk, habitats, k));
		LAST.set(e);
		return e;
	}

	public Ecotone.Region region() {
		return region;
	}

	/** Ecotone codes of the tree stand. */
	int[] trees() {
		if (trees == null) {
			trees = Ecotone.effective(region, Ecotone.Layer.TREES, seed, Ecotone.TREES_SALT, 1);
		}
		return trees;
	}

	/** Ecotone codes of the deadwood and the understory, the open columns of the forest mantle with the forest's code. */
	int[] plants() {
		if (plants == null) {
			plants = withMantle(Ecotone.effective(region, Ecotone.Layer.PLANTS, seed, Ecotone.PLANTS_SALT, 1));
		}
		return plants;
	}

	/** Ecotone codes of the ground layer (in patches), the open columns of the forest mantle with the forest's code. */
	int[] ground() {
		if (ground == null) {
			ground = withMantle(Ecotone.effective(region, Ecotone.Layer.PLANTS, seed, Ecotone.GROUND_SALT,
					Ecotone.GROUND_PATCH));
		}
		return ground;
	}

	/** Forest edge classes of the columns ({@link Ecotone.Edge} ordinals). */
	byte[] edges() {
		if (edges == null) {
			int[] m = new int[256];
			Arrays.fill(m, Ecotone.UNKNOWN);
			edges = Ecotone.edges(region, widths(seed), m);
			mantle = m;
		}
		return edges;
	}

	private int[] withMantle(int[] codes) {
		edges();
		for (int i = 0; i < 256; i++) {
			if (mantle[i] != Ecotone.UNKNOWN) {
				codes[i] = mantle[i];
			}
		}
		return codes;
	}

	private static Noise widths(long seed) {
		Widths w = widths;
		if (w == null || w.seed() != seed) {
			w = new Widths(seed, new Noise(seed).derive(EDGE_SALT));
			widths = w;
		}
		return w.noise();
	}
}
