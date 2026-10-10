package pl.polishforests.worldgen.feature;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.feature.plan.Ecotone;

/**
 * Ecotones of the chunk being decorated (rule Z10, step S8b; {@link Ecotone}), computed once and shared by the soil
 * ecotones and the dispatcher layers of the chunk: the decoration of a chunk runs on one thread, so the last chunk of
 * the thread is kept. The values are pure functions of the habitats of the chunk and its neighbors and the world seed,
 * so the cache never changes a result.
 */
public final class ChunkEcotones {
	private static final ThreadLocal<ChunkEcotones> LAST = new ThreadLocal<>();

	private final long seed;
	private final ChunkHabitats habitats;
	private final Ecotone.Region region;
	private int @Nullable [] trees;
	private int @Nullable [] plants;
	private int @Nullable [] ground;
	private Ecotone.@Nullable Edges edges;

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
		edges();
		return plants;
	}

	/**
	 * Ecotone codes of the ground layer (in patches), the forest columns of the fringe with the open land's code and the
	 * open columns before the fringe with the forest's code.
	 */
	int[] ground() {
		edges();
		return ground;
	}

	/** Forest edge classes of the understory (the mantle). */
	byte[] shrubEdges() {
		return edges().shrubs();
	}

	/** Forest edge classes of the ground layer (the fringe). */
	byte[] groundEdges() {
		return edges().ground();
	}

	private Ecotone.Edges edges() {
		Ecotone.Edges e = edges;
		if (e == null) {
			plants = Ecotone.effective(region, Ecotone.Layer.PLANTS, seed, Ecotone.PLANTS_SALT, 1);
			ground = Ecotone.effective(region, Ecotone.Layer.PLANTS, seed, Ecotone.GROUND_SALT, Ecotone.GROUND_PATCH);
			e = edges = Ecotone.edges(region, Ecotone.Noises.of(seed), plants, ground);
		}
		return e;
	}
}
