package pl.polishforests.worldgen.feature.plan;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * The plan of the anthills of the red wood ant (Formica rufa group, step FA8 of docs/04-pionowy-wycinek.md, option O4 of
 * CP-D.2): a pure function of the world seed and of what the world tells about a column ({@link Site}: its habitat code and
 * how deep it lies in a canopy gap of the stand map), shared by the feature {@code polishforests:anthill}, the command
 * {@code /polishforests find anthill} and the census of the habitats gametest.
 *
 * <p><b>Candidates.</b> The world is cut into cells of {@value #CELL} × {@value #CELL} blocks; a cell holds a candidate with
 * the chance {@link #CELL_CHANCE}, at a position drawn in the cell (the placement field of the new salt
 * {@value #SALT}: no row, no grid line), of a size drawn from {@link Size}. A candidate is moved inside its chunk so the whole
 * mound lies in one chunk (the feature writes only into the decorated chunk). Of two candidates closer than
 * {@value #MIN_SPACING} blocks (centers) only the one of the higher priority (a hash) stays, so the mounds never touch; the
 * test reads only the candidates of the 3 × 3 cells, not the sites, so it is local and does not depend on the order of the
 * chunks.
 *
 * <p><b>Sites.</b> A candidate is sited ({@link #sited}) when every column of its mound lies in a forest of the ant
 * ({@link #FORESTS}: the dry, fresh and moist pine forests, the mixed pine forest, the mixed forest and the montane spruce
 * forest) and the mound stands either in a canopy gap of the stand plan (its center at least {@value #GAP_MIN_DEPTH} blocks
 * deep in the gap: sun on the mound, as the ants need) or on the forest edge: no open land (the open dry and wet land of the
 * ecotones, {@link Ecotone#openLand}) within {@value #EDGE_MIN} blocks of the center, but some within the edge reach
 * ({@value #EDGE_REACH} blocks at the local scale 1, scaled with it, at least {@value #EDGE_REACH_MIN}) on a ring of
 * {@value #RING} points. The red wood ants keep to the sunny edges and gaps of conifer and mixed stands; the closed shade of
 * the broadleaved forests and the wet forests have none.
 *
 * <p><b>Density.</b> The source gives about {@value #SOURCE_DENSITY} mounds per hectare of the pine forests [Ź]; G.62-g
 * takes five times as many ({@link #DENSITY}, an explicit deviation of the norms card, docs/04 §6: a player meets a mound
 * on a walk, not once in a few kilometers). The chance of a cell is the density over the share of the forest area that is
 * sited ({@link #SITED_SHARE}, measured by the census of the habitats gametest).
 */
public final class AnthillPlan {
	/** The salt of the placement field (new in FA8; the old salts are not touched). */
	public static final String SALT = "feature.ground.anthills";
	/** The cell of one candidate, blocks. */
	public static final int CELL = 32;
	/** The mounds per hectare of the pine forests of the source [Ź]. */
	public static final double SOURCE_DENSITY = 0.13;
	/** The factor of G.62-g over the source (an explicit deviation, card §6). */
	public static final double DENSITY_FACTOR = 5;
	/** The mounds per hectare of the forests of the ant in the game. */
	public static final double DENSITY = SOURCE_DENSITY * DENSITY_FACTOR;
	/** The share of the forest columns of the ant that are sited (edges and gaps), from the habitats census. */
	public static final double SITED_SHARE = 0.3;
	/** The chance of a cell to hold a candidate. */
	public static final double CELL_CHANCE = Math.min(1, DENSITY * CELL * CELL / 10_000.0 / SITED_SHARE);
	/** The least distance of two mound centers, blocks. */
	public static final int MIN_SPACING = 20;
	/** No open land this close to the center of a mound on an edge, blocks. */
	public static final int EDGE_MIN = 3;
	/** The edge reach at the local scale 1, blocks. */
	public static final int EDGE_REACH = 12;
	/** The least edge reach (the small scale), blocks. */
	public static final int EDGE_REACH_MIN = 6;
	/** The points of the ring that looks for open land. */
	public static final int RING = 12;
	/** The least depth of the center of a mound in a canopy gap, blocks. */
	public static final double GAP_MIN_DEPTH = 1.5;
	/** The forests of the red wood ant. */
	public static final Set<HabitatBiome> FORESTS = EnumSet.of(HabitatBiome.DRY_PINE_FOREST, HabitatBiome.FRESH_PINE_FOREST,
			HabitatBiome.MOIST_PINE_FOREST, HabitatBiome.MIXED_PINE_FOREST, HabitatBiome.MIXED_FOREST, HabitatBiome.MONTANE_SPRUCE_FOREST);

	private static final long CHANCE_SALT = 0x7A11L;
	private static final long X_SALT = 0x7A12L;
	private static final long Z_SALT = 0x7A13L;
	private static final long SIZE_SALT = 0x7A14L;
	private static final long PRIORITY_SALT = 0x7A15L;
	private static final long SHAPE_SALT = 0x7A16L;

	private AnthillPlan() {
	}

	/** What the plan reads of the world at a column. */
	public interface Site {
		/** The habitat code of the column, or {@link Ecotone#UNKNOWN}. */
		int code(int x, int z);

		/** How deep the column lies in a canopy gap or regeneration nest of the stand map (blocks; 0 or less outside). */
		double gapDepth(int x, int z);
	}

	/**
	 * The sizes of the mounds: the width of the square footprint and the layers (2 px each) of the center, edge and corner
	 * columns; the shares of the sizes. A small mound is about a block high on 2 × 2 columns, a large one two blocks on 3 × 3.
	 */
	public enum Size {
		SMALL(2, 8, 7, 6, 0.45),
		MEDIUM(3, 12, 7, 4, 0.35),
		LARGE(3, 16, 10, 5, 0.2);

		public final int width;
		final int center;
		final int edge;
		final int corner;
		final double share;

		Size(int width, int center, int edge, int corner, double share) {
			this.width = width;
			this.center = center;
			this.edge = edge;
			this.corner = corner;
			this.share = share;
		}
	}

	/** Why the feature or the plan did not keep a candidate. */
	public enum Reason {
		SPACING, HABITAT, NOT_EDGE_OR_GAP
	}

	/**
	 * A planned mound: the corner of its footprint ({@code x}, {@code z}: the least column), its size, and the layers of
	 * every column ({@code layers[dx * width + dz]}, up to 16: two blocks).
	 */
	public record Anthill(int x, int z, Size size, int[] layers, double priority) {
		public int width() {
			return size.width;
		}

		/** The center of the mound (block coordinates, the middle of the footprint). */
		public double centerX() {
			return x + size.width / 2.0;
		}

		public double centerZ() {
			return z + size.width / 2.0;
		}

		public int layers(int dx, int dz) {
			return layers[dx * size.width + dz];
		}

		/** The highest column, layers. */
		public int height() {
			int h = 0;
			for (int l : layers) {
				h = Math.max(h, l);
			}
			return h;
		}
	}

	/** The candidate of cell (i, j), before the spacing and the sites, or null. */
	public static @Nullable Anthill candidate(long worldSeed, int i, int j) {
		Noise n = noise(worldSeed);
		if (n.unit(i, j, CHANCE_SALT) >= CELL_CHANCE) {
			return null;
		}
		double s = n.unit(i, j, SIZE_SALT);
		Size size = Size.LARGE;
		double acc = 0;
		for (Size v : Size.values()) {
			acc += v.share;
			if (s < acc) {
				size = v;
				break;
			}
		}
		int w = size.width;
		int x = i * CELL + (int) (n.unit(i, j, X_SALT) * CELL);
		int z = j * CELL + (int) (n.unit(i, j, Z_SALT) * CELL);
		// Inside one chunk: the corner at most 16 - w into the chunk.
		x = (x & ~15) + Math.min(x & 15, 16 - w);
		z = (z & ~15) + Math.min(z & 15, 16 - w);
		int[] layers = new int[w * w];
		long shape = n.hash(i, j, SHAPE_SALT);
		for (int dx = 0; dx < w; dx++) {
			for (int dz = 0; dz < w; dz++) {
				int base;
				if (w == 2) {
					// The small mound: one high corner (the center of the dome), two edges, one low corner.
					int k = (int) (shape & 3);
					int c = dx * 2 + dz;
					base = c == k ? size.center : c == (3 - k) ? size.corner : size.edge;
				} else {
					boolean cx = dx == 1;
					boolean cz = dz == 1;
					base = cx && cz ? size.center : cx || cz ? size.edge : size.corner;
				}
				// A layer more or less per column (no two mounds alike).
				int jitter = (int) ((shape >>> (8 + 2 * (dx * w + dz))) & 3) - 1;
				layers[dx * w + dz] = Math.clamp(base + (jitter > 1 ? 0 : jitter), 1, 16);
			}
		}
		return new Anthill(x, z, size, layers, n.unit(i, j, PRIORITY_SALT));
	}

	/** The candidate of cell (i, j) that the spacing keeps, or null. */
	public static @Nullable Anthill spaced(long worldSeed, int i, int j) {
		Anthill a = candidate(worldSeed, i, j);
		if (a == null) {
			return null;
		}
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				if (di == 0 && dj == 0) {
					continue;
				}
				Anthill b = candidate(worldSeed, i + di, j + dj);
				if (b != null && b.priority() > a.priority()
						&& Math.hypot(a.centerX() - b.centerX(), a.centerZ() - b.centerZ()) < MIN_SPACING) {
					return null;
				}
			}
		}
		return a;
	}

	/** The edge reach at the local scale k, blocks. */
	public static int edgeReach(double k) {
		return Math.max(EDGE_REACH_MIN, (int) Math.round(EDGE_REACH * k));
	}

	/** Whether code is a forest of the ant. */
	public static boolean antForest(int code) {
		return code != Ecotone.UNKNOWN && FORESTS.contains(Habitat.biome(code));
	}

	/** Null when the mound is sited, else why not. */
	public static @Nullable Reason siting(Anthill a, Site site, double k) {
		int w = a.width();
		for (int dx = 0; dx < w; dx++) {
			for (int dz = 0; dz < w; dz++) {
				if (!antForest(site.code(a.x() + dx, a.z() + dz))) {
					return Reason.HABITAT;
				}
			}
		}
		int cx = (int) Math.floor(a.centerX());
		int cz = (int) Math.floor(a.centerZ());
		if (site.gapDepth(cx, cz) >= GAP_MIN_DEPTH) {
			return null;
		}
		return edge(site, a.centerX(), a.centerZ(), edgeReach(k)) ? null : Reason.NOT_EDGE_OR_GAP;
	}

	/** Whether the point lies on a forest edge: open land within the reach but not within {@value #EDGE_MIN} blocks. */
	static boolean edge(Site site, double cx, double cz, int reach) {
		boolean open = false;
		for (int r = EDGE_MIN; r <= reach; r += 3) {
			double radius = r;
			for (int p = 0; p < RING; p++) {

				double angle = (p + (r & 1) * 0.5) * 2 * Math.PI / RING;
				int code = site.code((int) Math.floor(cx + Math.cos(angle) * radius), (int) Math.floor(cz + Math.sin(angle) * radius));
				if (Ecotone.openLand(code)) {
					if (r == EDGE_MIN) {
						return false;
					}
					open = true;
				}
			}
			if (open) {
				return true;
			}
		}
		return false;
	}

	/** The sited mounds whose footprint lies in chunk (chunkX, chunkZ). */
	public static List<Anthill> inChunk(long worldSeed, int chunkX, int chunkZ, Site site, double k) {
		List<Anthill> out = new ArrayList<>(1);
		for (Anthill a : candidatesInChunk(worldSeed, chunkX, chunkZ)) {
			if (siting(a, site, k) == null) {
				out.add(a);
			}
		}
		return out;
	}

	/** The spaced candidates whose footprint lies in chunk (chunkX, chunkZ), before the sites. */
	public static List<Anthill> candidatesInChunk(long worldSeed, int chunkX, int chunkZ) {
		int i = Math.floorDiv(chunkX * 16, CELL);
		int j = Math.floorDiv(chunkZ * 16, CELL);
		Anthill a = spaced(worldSeed, i, j);
		if (a == null || a.x() >> 4 != chunkX || a.z() >> 4 != chunkZ) {
			return List.of();
		}
		return List.of(a);
	}

	/** Whether a sited mound has its center at (cx, cz) (a result of {@link #nearest}). */
	public static boolean sitedAt(long worldSeed, Site site, double k, double cx, double cz) {
		Anthill a = spaced(worldSeed, (int) Math.floor(cx / CELL), (int) Math.floor(cz / CELL));
		return a != null && a.centerX() == cx && a.centerZ() == cz && siting(a, site, k) == null;
	}

	/**
	 * The nearest sited mound to (ox, oz) within {@code radius} blocks, searched in rings of cells outward: its center as
	 * {x, z}, or null (also when {@code cancelled} turns true).
	 */
	public static double @Nullable [] nearest(long worldSeed, Site site, double k, double ox, double oz, double radius,
			BooleanSupplier cancelled) {
		int ci = (int) Math.floor(ox / CELL);
		int cj = (int) Math.floor(oz / CELL);
		int rings = (int) Math.ceil(radius / CELL) + 1;
		double[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int ring = 0; ring <= rings; ring++) {
			if (best != null && bestDistance < (ring - 1) * CELL) {
				break;
			}
			if (cancelled.getAsBoolean()) {
				return null;
			}
			for (int di = -ring; di <= ring; di++) {
				for (int dj = -ring; dj <= ring; dj++) {
					if (Math.max(Math.abs(di), Math.abs(dj)) != ring) {
						continue;
					}
					Anthill a = spaced(worldSeed, ci + di, cj + dj);
					if (a == null) {
						continue;
					}
					double d = Math.hypot(a.centerX() - ox, a.centerZ() - oz);
					if (d < bestDistance && d <= radius && siting(a, site, k) == null) {
						best = new double[] {a.centerX(), a.centerZ()};
						bestDistance = d;
					}
				}
			}
		}
		return best;
	}

	private record Field(long worldSeed, Noise noise) {
	}

	private static volatile @Nullable Field cached;

	private static Noise noise(long worldSeed) {
		Field f = cached;
		if (f == null || f.worldSeed() != worldSeed) {
			f = new Field(worldSeed, new Noise(worldSeed).derive(SALT));
			cached = f;
		}
		return f.noise();
	}
}
