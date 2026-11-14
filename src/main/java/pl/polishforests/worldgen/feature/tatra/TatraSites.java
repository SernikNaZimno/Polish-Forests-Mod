package pl.polishforests.worldgen.feature.tatra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Step TT6: where the Tatra details are (docs/04-pionowy-wycinek.md, "Projekt TT6"). A site is an anchor chosen from the
 * landscape model alone: a grid of cells, in each at most one anchor of each kind at a hashed place (new salts, so nothing
 * of the old terrain or forests changes), kept where the model says the place suits the kind (the rock, the height, the
 * slope in blocks, for a hut the habitat). The commands search these anchors ({@code find waterfall} and the others), and
 * the feature ({@code TatraDetailsFeature}) builds the detail within a few blocks of the anchor from the ground of the
 * region, which can differ by a few blocks from the model (micro-relief, banks): the plan of the kind decides there
 * whether the ground really holds the detail, so a share of the anchors stays without one.
 *
 * <p>Every site is a pure function of the world seed, the settings and the model.
 */
public final class TatraSites {
	/** Slope window: the model is sampled this far from the anchor (m). */
	private static final double WINDOW = 3;
	/**
	 * The anchor lies this far from the edges of its chunk (blocks) at least, so a plan that reads and writes up to
	 * {@value #EXTENT} blocks from it stays in the window of the chunk's own decoration (16 beyond its edges).
	 */
	public static final int MARGIN = 4;
	/** Greatest distance from the anchor of a block a plan of any kind reads or writes. */
	public static final int EXTENT = MARGIN + 16;

	/** The habitat code of a column of the world (the classifier of its mode), or null to skip the habitat test of a hut. */
	@FunctionalInterface
	public interface Codes {
		int code(double x, double z);
	}

	/** The kinds of detail the anchors stand for. */
	public enum Kind {
		/** A waterfall on a steep slope, falling into a plunge pool cut into the foot (a drop of at least {@link WaterPlan#MIN_FALL} blocks). */
		WATERFALL(24, 0.40, 0x7A1F_C5E3_91B2_0D47L),
		/** A karst spring (wywierzysko) at the foot of the regle on the limestone and dolomite. */
		SPRING(20, 0.55, 0x2C94_E017_B6D5_3A8FL),
		/** The mouth of a cave in a limestone or dolomite slope: an open cut into the slope and a tunnel under it. */
		CAVE_MOUTH(8, 0.80, 0x58D0_1B6E_F3A9_C472L),
		/** A shepherd's hut on a hala or a glade (only in the present-day mode). */
		SHEPHERD_HUT(10, 0.12, 0x13E7_A94C_0F82_D5B6L);

		private final int chunks;
		private final double chance;
		private final long salt;

		Kind(int chunks, double chance, long salt) {
			this.chunks = chunks;
			this.chance = chance;
			this.salt = salt;
		}

		/** Side of a grid cell in chunks at the gameplay scale (it grows with the forms of the realistic scale). */
		public int chunks() {
			return chunks;
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** Id of the {@code find} target that searches the kind ({@code waterfall}, {@code karst_spring}, ...). */
		public String targetId() {
			return switch (this) {
				case WATERFALL -> "waterfall";
				case SPRING -> "karst_spring";
				case CAVE_MOUTH -> "cave_mouth";
				case SHEPHERD_HUT -> "shepherd_hut";
			};
		}
	}

	/** An anchor: the kind, the column, a seed for the plan and the rock of the column. */
	public record Site(Kind kind, int x, int z, long seed, Substrate rock) {
	}

	private TatraSites() {
	}

	/** Whether the kind exists in the mode (a hut only in the present-day one). */
	public static boolean exists(Kind kind, boolean presentDay) {
		return kind != Kind.SHEPHERD_HUT || presentDay;
	}

	/** Cell side in chunks of the kind in the model's scale: the realistic forms are 3.33 times the gameplay ones. */
	public static int cellChunks(LandscapeModel m, Kind kind) {
		return Math.max(kind.chunks, (int) Math.round(kind.chunks * m.scale().mountainSpacing() / LandscapeScale.GAMEPLAY.mountainSpacing()));
	}

	/**
	 * The anchor of the grid cell (cellX, cellZ) of the kind, or null: no anchor, or the place does not suit the kind. The
	 * cell holds at most one anchor, in a hashed chunk of it, {@value #MARGIN} to {@code MARGIN + 7} blocks from the chunk's
	 * west and north edges.
	 */
	public static @Nullable Site site(LandscapeModel m, Kind kind, int cellX, int cellZ, boolean presentDay,
			@Nullable Codes codes) {
		if (!m.hasTatra() || !exists(kind, presentDay)) {
			return null;
		}
		long[] a = anchor(m, kind, cellX, cellZ);
		return a == null ? null : suited(m, kind, a, codes);
	}

	/** The hashed anchor of the cell, {x, z, hash}, without the test of the place (cheap), or null: this cell has none. */
	private static long @Nullable [] anchor(LandscapeModel m, Kind kind, int cellX, int cellZ) {
		long h = Noise.mix(m.seed() ^ kind.salt ^ Noise.mix(cellX * 0x632BE59BD9B4E019L + cellZ * 0x85157AF5L));
		if ((h >>> 11) * 0x1.0p-53 >= kind.chance) {
			return null;
		}
		int n = cellChunks(m, kind);
		long h2 = Noise.mix(h ^ 0x6C07_8965_D1B3_F4E9L);
		long x = (cellX * (long) n + Math.floorMod(h2 >>> 3, (long) n)) * 16 + MARGIN + Math.floorMod(h2 >>> 40, 8L);
		long z = (cellZ * (long) n + Math.floorMod(h2 >>> 22, (long) n)) * 16 + MARGIN + Math.floorMod(h2 >>> 50, 8L);
		return new long[] {x, z, h};
	}

	/** The site at the anchor if the model says the place suits the kind. */
	private static @Nullable Site suited(LandscapeModel m, Kind kind, long[] a, @Nullable Codes codes) {
		int x = (int) a[0];
		int z = (int) a[1];
		if (!m.tatraRock(x, z)) {
			return null;
		}
		ColumnSample s = m.sample(x, z);
		Substrate rock = s.terrain().rock();
		if (rock == null || s.hasWater()) {
			return null;
		}
		double height = s.surface();
		double slope = blockSlope(m, x, z, height);
		boolean carbonate = rock == Substrate.LIMESTONE || rock == Substrate.DOLOMITE;
		boolean ok = switch (kind) {
			case WATERFALL -> height >= 800 && height <= 2300 && slope >= 0.45 && slope <= 1.6;
			case SPRING -> carbonate && height >= 650 && height <= 1150 && slope <= 0.4;
			case CAVE_MOUTH -> carbonate && height >= 800 && height <= 2000 && slope >= 0.8;
			case SHEPHERD_HUT -> height >= 700 && height <= 2100 && slope <= 0.3 && openLand(codes, x, z);
		};
		return ok ? new Site(kind, x, z, Noise.mix(a[2] ^ 0x4F1B_BCDC_BCD9_5C2DL), rock) : null;
	}

	/** Whether the column is open land of the mountains (a hala or a glade): the habitat of a hut; true without codes. */
	static boolean openLand(@Nullable Codes codes, int x, int z) {
		if (codes == null) {
			return true;
		}
		HabitatBiome b = Habitat.biome(codes.code(x, z));
		return b == HabitatBiome.ALPINE_GRASSLAND || b == HabitatBiome.HAY_MEADOW;
	}

	/** Slope of the model ground at the point in blocks per block (tan of the angle in blocks). */
	static double blockSlope(LandscapeModel m, double x, double z, double height) {
		double dx = m.sample(x + WINDOW, z).surface() - m.sample(x - WINDOW, z).surface();
		double dz = m.sample(x, z + WINDOW).surface() - m.sample(x, z - WINDOW).surface();
		return Math.hypot(dx, dz) / (2 * WINDOW) * m.tatraBlocksPerMeter(height);
	}

	/** The anchors of every kind that lie in the chunk, in the order of the kinds. */
	public static List<Site> inChunk(LandscapeModel m, int chunkX, int chunkZ, boolean presentDay, @Nullable Codes codes) {
		List<Site> out = new ArrayList<>();
		if (!m.hasTatra()) {
			return out;
		}
		for (Kind kind : Kind.values()) {
			if (!exists(kind, presentDay)) {
				continue;
			}
			// The anchor of a cell lies in a chunk of it: one cell per chunk to look at.
			int n = cellChunks(m, kind);
			long[] a = anchor(m, kind, Math.floorDiv(chunkX, n), Math.floorDiv(chunkZ, n));
			// The anchor lies in one chunk of the cell: only that chunk tests the place (the test costs several samples).
			if (a == null || (int) a[0] >> 4 != chunkX || (int) a[1] >> 4 != chunkZ) {
				continue;
			}
			// The habitat is that of the chunk's own columns, which the codes read.
			Site s = suited(m, kind, a, codes);
			if (s != null) {
				out.add(s);
			}
		}
		return out;
	}

	/** The anchor of any kind nearest the point within {@code radius} blocks (the chunks around it are looked at), or null. */
	public static @Nullable Site nearest(LandscapeModel m, double x, double z, double radius, boolean presentDay, @Nullable Codes codes) {
		if (!m.hasTatra()) {
			return null;
		}
		Site best = null;
		double bestDistance = radius;
		int c0x = (int) Math.floor((x - radius) / 16);
		int c1x = (int) Math.floor((x + radius) / 16);
		int c0z = (int) Math.floor((z - radius) / 16);
		int c1z = (int) Math.floor((z + radius) / 16);
		for (int cx = c0x; cx <= c1x; cx++) {
			for (int cz = c0z; cz <= c1z; cz++) {
				for (Site s : inChunk(m, cx, cz, presentDay, codes)) {
					double d = Math.hypot(s.x() - x, s.z() - z);
					if (d <= bestDistance) {
						bestDistance = d;
						best = s;
					}
				}
			}
		}
		return best;
	}

	/**
	 * The nearest anchor of the kind to the origin of the search: the Tatra massifs within {@code radius} in the order of
	 * the distance of their centers from the point, each searched in square rings of cells around its center up to its
	 * reach; the first anchor found (the one nearest that center in the nearest massif with one), {x, z}, or null.
	 */
	public static double @Nullable [] find(LandscapeModel m, Kind kind, double ox, double oz, double radius, boolean presentDay,
			@Nullable Codes codes, BooleanSupplier cancelled) {
		if (!m.hasTatra() || !exists(kind, presentDay)) {
			return null;
		}
		List<LandscapeModel.Tatra> massifs = new ArrayList<>(m.tatras(ox - radius, oz - radius, ox + radius, oz + radius));
		massifs.removeIf(t -> Math.hypot(t.x() - ox, t.z() - oz) > radius);
		massifs.sort(Comparator.comparingDouble(t -> Math.hypot(t.x() - ox, t.z() - oz)));
		for (LandscapeModel.Tatra t : massifs) {
			int cell = cellChunks(m, kind) * 16;
			int cx = Math.floorDiv((int) Math.round(t.x()), cell);
			int cz = Math.floorDiv((int) Math.round(t.z()), cell);
			int rings = (int) Math.ceil(t.reach() / cell);
			for (int r = 0; r <= rings; r++) {
				if (cancelled.getAsBoolean()) {
					return null;
				}
				Site best = null;
				double bestDistance = Double.MAX_VALUE;
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						Site s = site(m, kind, cx + dx, cz + dz, presentDay, codes);
						if (s != null) {
							double d = Math.hypot(s.x() - t.x(), s.z() - t.z());
							if (d < bestDistance) {
								bestDistance = d;
								best = s;
							}
						}
					}
				}
				if (best != null) {
					return new double[] {best.x(), best.z()};
				}
			}
		}
		return null;
	}
}
