package pl.polishforests.worldgen.feature.plan;

import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Ecotones between biomes and habitats (rule Z10, docs/03-m2-biomy.md §1 and §8.8, step S8b): the decoration does not
 * switch palettes on the line where the habitat code changes, but in a transition belt, whose width depends on the pair
 * of habitats. A pure function of the habitat codes of a chunk and its 8 neighbors ({@code ChunkHabitats}, all past
 * {@code fill()} when a chunk is decorated), the world seed and a layer salt; no new samples of the landscape model.
 *
 * <p><b>Mixing.</b> Each column (or each small patch, {@link #effective}) takes a random vector u with an isotropic
 * distribution whose projection on every direction is uniform in [−1, 1] (the horizontal part of a uniform point on the
 * unit sphere). The column then takes the code of the column at u·w for the largest width class w ({@link #WIDTHS}) at
 * which that code differs from its own and the half-width of the pair ({@link #halfWidth}) is at least w. At a straight
 * border between two habitats with half-width H the share of the other side's code is thus (1 − d/H) / 2 at distance d
 * from the border: a linear ramp from 50% at the border to 0 at H on both sides, so the transition belt is 2H wide.
 *
 * <p><b>Meander</b> (round 1 of the S8b review): the ramp is centered on the border of the codes, so a straight contour of
 * the classifier would give a straight transition. Each column therefore first moves by a smooth vector field W of at
 * most {@value #WARP} blocks (wavelength {@value #WARP_WAVELENGTH} m·k, at least {@value #WARP_MIN_WAVELENGTH} blocks,
 * shared by all layers and the forest edges) and mixes around the moved point when the codes there form a mixing pair
 * with its own; the 50% line follows the contour moved by W, so it meanders by up to {@value #WARP} blocks. The mixing
 * widths around the moved point shrink by |W| (the region holds {@value #REACH} blocks around the chunk).
 *
 * <p><b>Codes that keep their own.</b> Water, the beach, the bare sand of white dunes against forests and the narrow
 * zones (Z4) never take another code. The waterside zones of the land ({@link #SOFT_ZONES}) are taken by their land
 * neighbors in a narrow one-sided belt: a land column at distance d from such a zone takes its code with probability
 * 1 − d/H (searching along u and −u), so its herbs and its lower tree density fade into the forest or meadow. The same
 * one-sided belt applies to the biomes of the density rule of §4.6 in the plant layers. The water edge, the beach and
 * the cliff stay sharp.
 *
 * <p><b>Reach.</b> A decorated chunk can read the codes of its 8 neighbors only, so every lookup stays within
 * {@value #REACH} blocks of the column: the half-widths are in meters·k (Z7) and capped at {@value #REACH} blocks (in the
 * realistic scale the widest ecotones are 32 m instead of 48 m).
 *
 * <p><b>Forest edges</b> ({@link #edges}): where a forest meets open land (meadow, field, heath, gray dune, alpine
 * grassland, peatland, reedbed), the forest columns within {@value #MANTLE} m·k of the open land and the open columns
 * within {@value #MANTLE_OUT} m·k of the forest are the mantle (shrubs, in clumps with gaps and with single shrubs
 * deeper), the open columns up to {@value #FRINGE} m·k from the forest the fringe (tall herbs), which starts up to
 * {@value #FRINGE_IN} m·k under the canopy and meets the forest floor in patches; the widths vary by a noise of
 * wavelength {@value #EDGE_WAVELENGTH} m·k between half and one and a half of these.
 */
public final class Ecotone {
	/** Greatest distance (blocks) of a column whose code a column may take: one chunk. */
	public static final int REACH = 16;
	/** Side of the region of codes read around a chunk: the chunk and its 8 neighbors. */
	public static final int SPAN = 16 + 2 * REACH;
	/** Code of a column whose habitats are not known (a neighbor without {@code ChunkHabitats}). */
	public static final int UNKNOWN = -1;
	/** Width classes in blocks, from the widest: the effective half-width of a pair is the largest class within it. */
	static final int[] WIDTHS = {16, 14, 12, 10, 8, 7, 6, 5, 4, 3, 2, 1};

	/**
	 * Salt of the ecotone vectors of the tree stand (round 1 of the S8b review: its own salt; in S8b the tree stand's
	 * candidate salt was reused).
	 */
	public static final long TREES_SALT = 0x3E07_7EE5_EC07L;
	/** Salt of the ecotone vectors of the deadwood and the understory (one draw for both layers). */
	public static final long PLANTS_SALT = 0x9A1D_E77EL;
	/** Salt and patch size of the ecotone vectors of the ground layer (clumps of the other side's ground layer). */
	public static final long GROUND_SALT = 0x6B0D_1A7EL;
	public static final int GROUND_PATCH = 3;

	/** Largest displacement of the meander field (blocks). */
	public static final int WARP = 6;
	/** Wavelength of the meander field (m·k), and its least value in blocks. */
	public static final double WARP_WAVELENGTH = 48;
	public static final int WARP_MIN_WAVELENGTH = 32;
	/** Salt of the meander field (a new field, a new salt; round 1 of the S8b review). */
	public static final String WARP_SALT = "feature.ecotone.warp";

	/**
	 * Half-widths (m·k: trees, plants, soil) of the one-sided belt in which a land column takes the code of a neighboring
	 * waterside zone of the land ({@link #SOFT_ZONES}).
	 */
	public static final double ZONE_TREES = 4;
	public static final double ZONE_PLANTS = 4;
	public static final double ZONE_SOIL = 2;
	/** Half-width of the trees of the alder row (m·k): the row stays a row, with a ragged edge. */
	public static final double TREE_ROW_TREES = 2;
	/**
	 * Least half-widths (blocks, any scale) of the trees and of the soil at a forest edge (round 1 of the S8b review: with
	 * k = 0.5 the edges of the gameplay scale were only 6–8 blocks wide and read as a line from above).
	 */
	public static final int EDGE_TREES_MIN = 5;
	public static final int EDGE_SOIL_MIN = 3;

	/** Mantle depth into the forest (m·k) and its least value (blocks). */
	public static final double MANTLE = 4;
	public static final double MANTLE_MIN = 2;
	/** Mantle reach into the open land (m·k): shrubs grow out of the forest a little. */
	public static final double MANTLE_OUT = 1.5;
	/** Fringe width in the open land (m·k) and its least value (blocks). */
	public static final double FRINGE = 6;
	public static final double FRINGE_MIN = 3;
	/** Greatest depth of the fringe under the canopy (m·k, at least one block). */
	public static final double FRINGE_IN = 1.5;
	/** Wavelength of the noise of the edge widths (m·k). */
	public static final double EDGE_WAVELENGTH = 16;
	/** Wavelength (m·k, least blocks) of the clumps and gaps of the mantle and share of the edge length in gaps. */
	public static final double GAP_WAVELENGTH = 12;
	public static final double GAP_MIN_WAVELENGTH = 8;
	public static final double GAP_SHARE = 0.4;
	/** Wavelength (m·k, least blocks) of the patches where the fringe meets the forest floor. */
	public static final double PATCH_WAVELENGTH = 5;
	public static final double PATCH_MIN_WAVELENGTH = 4;
	/** Chance of a single mantle shrub just beyond the mantle (falling to 0 at twice its width). */
	public static final double SPORADIC = 0.3;
	/** Chance of a mantle column in a gap of the mantle. */
	public static final double GAP_CHANCE = 0.1;
	/** Salts of the noises of the edge widths (step S8b) and of the clumps and patches (round 1 of the S8b review). */
	public static final String EDGE_SALT = "feature.ecotone.edges";
	public static final String EDGE_PATCH_SALT = "feature.ecotone.edge_patches";

	/** Decoration layer, each with its own half-widths. */
	public enum Layer {
		/** The tree stand: composition and density. */
		TREES,
		/** Deadwood, understory and ground layer. */
		PLANTS,
		/** The top ground block (soil). */
		SOIL
	}

	/** Forest edge class of a column (new constants only at the end: palettes store the names). */
	public enum Edge {
		/** No forest edge. */
		NONE,
		/** The mantle of shrubs: the outer meters of the forest and the first meter or two of the open land. */
		MANTLE,
		/** The fringe of tall herbs in the open land in front of the mantle (and a little under the canopy). */
		FRINGE
	}

	/** Kind of a biome for the ecotone widths. */
	enum Kind {
		DRY_WOODLAND, WET_WOODLAND, SCRUB, OPEN_DRY, OPEN_WET, SAND, WATER
	}

	private static final Kind[] KINDS = new Kind[HabitatBiome.values().length];

	static {
		for (HabitatBiome b : HabitatBiome.values()) {
			KINDS[b.ordinal()] = switch (b) {
				case BOG_WOODLAND, ALDER_CARR, ASH_ALDER_FOREST, WILLOW_POPLAR_FOREST, ELM_ASH_FOREST, GRAY_ALDER_FOREST ->
						Kind.WET_WOODLAND;
				case DWARF_PINE_SCRUB, WILLOW_SCRUB -> Kind.SCRUB;
				case HEATH, HAY_MEADOW, ARABLE_LAND, GRAY_DUNE, ALPINE_GRASSLAND -> Kind.OPEN_DRY;
				case WET_MEADOW, FEN, RAISED_BOG, REEDBED -> Kind.OPEN_WET;
				case BEACH, WHITE_DUNE -> Kind.SAND;
				default -> b.isWater() ? Kind.WATER : Kind.DRY_WOODLAND;
			};
		}
	}

	/**
	 * Biomes of the density rule of §4.6 (floodplain forests, alder carr, willow scrub, reedbed): in the plant layers they
	 * never take the code of another biome (their tall herbs stay), only the other side takes theirs.
	 */
	private static final long DENSE = mask(HabitatBiome.ALDER_CARR, HabitatBiome.ASH_ALDER_FOREST,
			HabitatBiome.WILLOW_POPLAR_FOREST, HabitatBiome.ELM_ASH_FOREST, HabitatBiome.GRAY_ALDER_FOREST,
			HabitatBiome.WILLOW_SCRUB, HabitatBiome.REEDBED);

	/**
	 * Waterside zones of the land and the alder row of the PRESENT_DAY mode (round 1 of the S8b review): they keep their
	 * code, and their land neighbors take it in a narrow belt. The zones in the water, the bars, the floating mat and the
	 * beach and cliff zones stay sharp, and so do the borders between two zones (their own sequence, Z4).
	 */
	static final int SOFT_ZONES = zones(Zone.SHORE_REEDBED, Zone.WILLOW_SCRUB, Zone.HERB_FRINGE, Zone.TALL_HERBS,
			Zone.RIVERSIDE_WILLOWS, Zone.WILLOW_CARR, Zone.MONTANE_TALL_HERBS, Zone.SPRING_AREA, Zone.SHORE_ALDERS, Zone.TREE_ROW);

	private static long mask(HabitatBiome... biomes) {
		long m = 0;
		for (HabitatBiome b : biomes) {
			m |= 1L << b.ordinal();
		}
		return m;
	}

	private static int zones(Zone... zones) {
		int m = 0;
		for (Zone z : zones) {
			m |= 1 << z.ordinal();
		}
		return m;
	}

	private Ecotone() {
	}

	static Kind kind(int code) {
		return KINDS[code & 63];
	}

	private static boolean woodland(Kind k) {
		return k == Kind.DRY_WOODLAND || k == Kind.WET_WOODLAND;
	}

	private static boolean open(Kind k) {
		return k == Kind.OPEN_DRY || k == Kind.OPEN_WET;
	}

	private static boolean dense(int code) {
		return (DENSE >>> (code & 63) & 1) != 0;
	}

	/** Whether the zone of the code is one of the land (no zone, or the stunted spruces of the timberline). */
	private static boolean landZone(int code) {
		Zone z = Habitat.zone(code);
		return z == Zone.NONE || z == Zone.TIMBERLINE;
	}

	private static boolean softZone(int code) {
		return (SOFT_ZONES >>> Habitat.zone(code).ordinal() & 1) != 0;
	}

	/** Whether a column of this code may take another code at all: a land code, not water. */
	private static boolean takes(int code) {
		return code != UNKNOWN && landZone(code) && kind(code) != Kind.WATER;
	}

	/** Whether the pair is a forest edge: a forest (dry or wet) and open land, both without a zone. */
	static boolean edgePair(int a, int b) {
		Kind x = kind(a);
		Kind y = kind(b);
		return (woodland(x) && open(y) || open(x) && woodland(y)) && landZone(a) && landZone(b);
	}

	/**
	 * Half-width (meters at the realistic scale, times k in the world, Z7) of the ecotone between the habitat code
	 * {@code own} of a column and the code {@code other} of a column beyond the border, for a layer; 0: a sharp border.
	 * Similar forests 24 m (soil 8 m), dry and wet forests 12 m (6 m), forest and scrub 12 m (plants 8 m, soil 4 m),
	 * forest and open land 8 m for the trees (6 m by peatlands) and the soil 4 m (3 m), the plants none (the mantle and the
	 * fringe instead), open land of one kind 12 m (6 m), dry and wet open land and scrub and open land 8 m (4 m), white and
		 * gray dunes 4 m (2 m); a land column against a waterside zone of the land {@value #ZONE_TREES} m (the alder row
	 * {@value #TREE_ROW_TREES} m; soil {@value #ZONE_SOIL} m), the zone against the land 0 (one-sided); water, beaches, the
	 * other narrow zones and their neighbors 0. In the plant layers the biomes of the density rule of §4.6 do not take the code of another biome.
	 */
	public static double halfWidth(int own, int other, Layer layer) {
		Kind a = kind(own);
		Kind b = kind(other);
		if (a == Kind.WATER || b == Kind.WATER) {
			return 0;
		}
		int t = layer.ordinal();
		if (!landZone(own) || !landZone(other)) {
			if (!landZone(own) || !softZone(other) || a == Kind.SAND) {
				return 0;
			}
			return pick(t, Habitat.zone(other) == Zone.TREE_ROW ? TREE_ROW_TREES : ZONE_TREES, ZONE_PLANTS, ZONE_SOIL);
		}
		if (layer == Layer.PLANTS && dense(own) && (own & 63) != (other & 63) && !dense(other)) {
			return 0;
		}
		if (a == Kind.SAND || b == Kind.SAND) {
			HabitatBiome x = Habitat.biome(own);
			HabitatBiome y = Habitat.biome(other);
			boolean dunes = (x == HabitatBiome.WHITE_DUNE || x == HabitatBiome.GRAY_DUNE)
					&& (y == HabitatBiome.WHITE_DUNE || y == HabitatBiome.GRAY_DUNE);
			return dunes ? pick(t, 4, 4, 2) : 0;
		}
		if (woodland(a) && woodland(b)) {
			return a == b ? pick(t, 24, 24, 8) : pick(t, 12, 12, 6);
		}
		if (woodland(a) && b == Kind.SCRUB || a == Kind.SCRUB && woodland(b)) {
			return pick(t, 12, 8, 4);
		}
		if (woodland(a) && open(b) || open(a) && woodland(b)) {
			boolean wet = a == Kind.OPEN_WET || b == Kind.OPEN_WET;
			return wet ? pick(t, 6, 0, 3) : pick(t, 8, 0, 4);
		}
		if (open(a) && open(b)) {
			return a == b ? pick(t, 12, 12, 6) : pick(t, 8, 8, 4);
		}
		// Scrub and open land, scrub and scrub.
		return pick(t, 8, 8, 4);
	}

	private static double pick(int layer, double trees, double plants, double soil) {
		return layer == 0 ? trees : layer == 1 ? plants : soil;
	}

	/**
	 * Half-width in blocks: {@link #halfWidth} times k, at a forest edge at least {@value #EDGE_TREES_MIN} blocks for the
	 * trees and {@value #EDGE_SOIL_MIN} for the soil, at most {@link #REACH}.
	 */
	public static int halfWidthBlocks(int own, int other, Layer layer, double k) {
		double h = halfWidth(own, other, layer) * k;
		if (h <= 0) {
			return 0;
		}
		if (layer != Layer.PLANTS && edgePair(own, other)) {
			h = Math.max(h, layer == Layer.TREES ? EDGE_TREES_MIN : EDGE_SOIL_MIN);
		}
		return (int) Math.min(REACH, Math.max(1, Math.round(h)));
	}

	/** Whether the other side's code is taken in a one-sided belt: it mixes into own, but own never into it. */
	static boolean sticky(int own, int other, Layer layer) {
		return halfWidth(own, other, layer) > 0 && halfWidth(other, own, layer) <= 0;
	}

	/** Whether the pair mixes both ways in the layer. */
	private static boolean symmetric(int own, int other, Layer layer) {
		return halfWidth(own, other, layer) > 0 && halfWidth(other, own, layer) > 0;
	}

	/**
	 * Whether the meander may move a column of code {@code own} onto a column of code {@code c}: the pair mixes both ways,
	 * or (in the plant layers) it is a forest edge, whose plants follow the same moved border as the trees and the mantle;
	 * a biome of §4.6 never gives up its plants to another biome this way.
	 */
	private static boolean follows(int own, int c, Layer layer) {
		if (layer == Layer.PLANTS) {
			if (dense(own) && !dense(c) && (own & 63) != (c & 63)) {
				return false;
			}
			if (edgePair(own, c)) {
				return true;
			}
		}
		return symmetric(own, c, layer);
	}

	/** Noises of the ecotones of one world seed (meander, edge widths, clumps and patches of the edges). */
	public record Noises(long seed, Noise warp, Noise widths, Noise patches) {
		public static Noises of(long seed) {
			Noises n = cached;
			if (n == null || n.seed() != seed) {
				Noise root = new Noise(seed);
				n = new Noises(seed, root.derive(WARP_SALT), root.derive(EDGE_SALT), root.derive(EDGE_PATCH_SALT));
				cached = n;
			}
			return n;
		}
	}

	private static volatile @Nullable Noises cached;

	/**
	 * Codes of a chunk and its 8 neighbors, with the derived values all layers of the chunk share (the meander of the
	 * chunk's columns, the table of the uniform areas). Used by one thread at a time.
	 */
	public static final class Region {
		private final int chunkX;
		private final int chunkZ;
		private final int[] codes;
		private final double k;
		/** Summed-area table of the cells whose code differs from the next cell in x or z ((SPAN + 1)², lazily). */
		private int @Nullable [] borders;
		/** Whether the region holds a code another code takes one-sided (a waterside zone of the land, a §4.6 biome). */
		private int stickyState = -1;
		private long warpSeed;
		/** Meander of the chunk's columns, x and z in blocks per column (lazily, per world seed). */
		private byte @Nullable [] warp;

		/**
		 * @param chunkX chunk coordinates
		 * @param codes  codes of the region, index {@code (x + REACH) * SPAN + z + REACH} for the chunk-local coordinates
		 *               x, z in [−{@value #REACH}, 15 + {@value #REACH}], {@link #UNKNOWN} where not known
		 * @param k      the scale's k (Z7)
		 */
		public Region(int chunkX, int chunkZ, int[] codes, double k) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.codes = codes;
			this.k = k;
		}

		/**
		 * Region from the codes of the chunk and its neighbors: {@code chunks[(dx + 1) * 3 + dz + 1]} are the 256 codes
		 * (index {@code x * 16 + z}) of the chunk at offset (dx, dz), or null when not known; index 4 is the chunk itself.
		 */
		public static Region of(int chunkX, int chunkZ, int[][] chunks, double k) {
			int[] codes = new int[SPAN * SPAN];
			java.util.Arrays.fill(codes, UNKNOWN);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int[] c = chunks[(dx + 1) * 3 + dz + 1];
					if (c == null) {
						continue;
					}
					for (int x = 0; x < 16; x++) {
						int rx = dx * 16 + x + REACH;
						System.arraycopy(c, x * 16, codes, rx * SPAN + dz * 16 + REACH, 16);
					}
				}
			}
			return new Region(chunkX, chunkZ, codes, k);
		}

		public int chunkX() {
			return chunkX;
		}

		public int chunkZ() {
			return chunkZ;
		}

		public double k() {
			return k;
		}

		/** Code at chunk-local (x, z), or {@link #UNKNOWN}. */
		public int at(int x, int z) {
			if (x < -REACH || x >= 16 + REACH || z < -REACH || z >= 16 + REACH) {
				return UNKNOWN;
			}
			return codes[(x + REACH) * SPAN + z + REACH];
		}

		/** Codes of the chunk itself (index {@code x * 16 + z}). */
		public int[] own() {
			int[] out = new int[256];
			for (int x = 0; x < 16; x++) {
				System.arraycopy(codes, (x + REACH) * SPAN + REACH, out, x * 16, 16);
			}
			return out;
		}

		/** Whether all cells within {@code r} blocks (square) of the chunk-local column hold one code. */
		boolean uniform(int x, int z, int r) {
			int[] s = borders;
			if (s == null) {
				s = borders = borderTable();
			}
			int a0 = Math.max(0, x - r + REACH);
			int a1 = Math.min(SPAN - 1, x + r + REACH);
			int b0 = Math.max(0, z - r + REACH);
			int b1 = Math.min(SPAN - 1, z + r + REACH);
			int w = SPAN + 1;
			return s[(a1 + 1) * w + b1 + 1] - s[a0 * w + b1 + 1] - s[(a1 + 1) * w + b0] + s[a0 * w + b0] == 0;
		}

		/** Whether the whole region holds one code. */
		boolean uniform() {
			int[] s = borders;
			if (s == null) {
				s = borders = borderTable();
			}
			return s[s.length - 1] == 0;
		}

		private int[] borderTable() {
			int w = SPAN + 1;
			int[] s = new int[w * w];
			for (int a = 0; a < SPAN; a++) {
				int row = 0;
				for (int b = 0; b < SPAN; b++) {
					int c = codes[a * SPAN + b];
					boolean d = a + 1 < SPAN && codes[(a + 1) * SPAN + b] != c || b + 1 < SPAN && codes[a * SPAN + b + 1] != c;
					row += d ? 1 : 0;
					s[(a + 1) * w + b + 1] = s[a * w + b + 1] + row;
				}
			}
			return s;
		}

		/** Whether the region holds a waterside zone of the land or a biome of §4.6 (one-sided belts). */
		boolean anySticky() {
			if (stickyState < 0) {
				boolean any = false;
				for (int i = 0; i < codes.length && !any; i++) {
					int c = codes[i];
					any = c != UNKNOWN && (softZone(c) || dense(c));
				}
				stickyState = any ? 1 : 0;
			}
			return stickyState == 1;
		}

		/**
		 * Meander of the chunk's columns (blocks; x at index 2i, z at 2i + 1): a smooth field of at most {@value #WARP}
		 * blocks from two noise components, evaluated every 4 blocks of the world grid and interpolated.
		 */
		byte[] warp(long worldSeed) {
			byte[] w = warp;
			if (w != null && warpSeed == worldSeed) {
				return w;
			}
			w = new byte[512];
			Noise n = Noises.of(worldSeed).warp();
			double wavelength = Math.max(WARP_MIN_WAVELENGTH, WARP_WAVELENGTH * k);
			double[] gx = new double[25];
			double[] gz = new double[25];
			int x0 = chunkX << 4;
			int z0 = chunkZ << 4;
			for (int a = 0; a < 5; a++) {
				for (int b = 0; b < 5; b++) {
					double wx = WARP * Math.clamp(n.at(x0 + 4 * a, z0 + 4 * b, wavelength) / 0.5, -1, 1);
					double wz = WARP * Math.clamp(n.at(x0 + 4 * a + 5_003.0, z0 + 4 * b - 3_001.0, wavelength) / 0.5, -1, 1);
					double len = Math.sqrt(wx * wx + wz * wz);
					if (len > WARP) {
						wx *= WARP / len;
						wz *= WARP / len;
					}
					gx[a * 5 + b] = wx;
					gz[a * 5 + b] = wz;
				}
			}
			for (int i = 0; i < 256; i++) {
				int x = i >> 4;
				int z = i & 15;
				int a = x >> 2;
				int b = z >> 2;
				double fx = (x & 3) / 4.0;
				double fz = (z & 3) / 4.0;
				int c = a * 5 + b;
				w[2 * i] = (byte) Math.round(bilinear(gx, c, fx, fz));
				w[2 * i + 1] = (byte) Math.round(bilinear(gz, c, fx, fz));
			}
			warpSeed = worldSeed;
			warp = w;
			return w;
		}

		private static double bilinear(double[] g, int c, double fx, double fz) {
			double top = g[c] + (g[c + 5] - g[c]) * fx;
			double bottom = g[c + 1] + (g[c + 6] - g[c + 1]) * fx;
			return top + (bottom - top) * fz;
		}
	}

	/**
	 * Effective codes of the 256 columns of the chunk for a layer, with the meander: the code each column's palette is
	 * chosen by (see the class description). Columns of a patch size above 1 share their random vector with their patch,
	 * a cell of that size whose border is shifted by up to one block per column, so the codes of the other side come in
	 * small clumps (soil patches rather than single blocks).
	 *
	 * @param salt  salt of the layer (each layer draws its own vectors)
	 * @param patch patch size in blocks (1: each column its own vector)
	 */
	public static int[] effective(Region region, Layer layer, long worldSeed, long salt, int patch) {
		return effective(region, layer, worldSeed, salt, patch, true);
	}

	/** {@link #effective} with or without the meander (tests of the plain ramps). */
	static int[] effective(Region region, Layer layer, long worldSeed, long salt, int patch, boolean meander) {
		int[] out = region.own();
		if (region.uniform()) {
			return out;
		}
		long seed = Noise.mix(worldSeed ^ Noise.mix(salt ^ 0xEC07_0E5AL));
		byte[] warp = meander ? region.warp(worldSeed) : null;
		boolean sticky = region.anySticky();
		int x0 = region.chunkX() << 4;
		int z0 = region.chunkZ() << 4;
		double[] u = new double[2];
		for (int i = 0; i < 256; i++) {
			int own = out[i];
			int x = i >> 4;
			int z = i & 15;
			if (!takes(own) || region.uniform(x, z, REACH)) {
				continue;
			}
			vector(seed, x0 + x, z0 + z, patch, u);
			int wx = warp == null ? 0 : warp[2 * i];
			int wz = warp == null ? 0 : warp[2 * i + 1];
			out[i] = pick(region, layer, own, x, z, u[0], u[1], wx, wz, sticky);
		}
		return out;
	}

	/** Effective code of one column at chunk-local (x, z) with the random vector (ux, uz) and the meander (wx, wz). */
	private static int pick(Region region, Layer layer, int own, int x, int z, double ux, double uz, int wx, int wz,
			boolean anySticky) {
		double k = region.k();
		if (anySticky) {
			// One-sided belts from the column itself (in both directions of the vector, so the share falls from 1 at the
			// border to 0 at H), the width varying along the border with the meander field.
			double f = 1 + 0.4 * wx / WARP;
			for (int w : WIDTHS) {
				for (int s = 1; s >= -1; s -= 2) {
					int dx = (int) Math.round(s * ux * w);
					int dz = (int) Math.round(s * uz * w);
					if (dx == 0 && dz == 0) {
						continue;
					}
					int other = region.at(x + dx, z + dz);
					if (other == UNKNOWN || other == own || !sticky(own, other, layer)) {
						continue;
					}
					if (Math.max(1, Math.round(halfWidthBlocks(own, other, layer, k) * f)) >= w) {
						return other;
					}
				}
			}
		}
		// Symmetric belts around the column moved by the meander.
		int bx = x;
		int bz = z;
		int base = own;
		int cap = REACH;
		if (wx != 0 || wz != 0) {
			int c = region.at(x + wx, z + wz);
			if (c != UNKNOWN && (c == own || follows(own, c, layer))) {
				bx += wx;
				bz += wz;
				base = c;
				cap = REACH - Math.max(Math.abs(wx), Math.abs(wz));
			}
		}
		for (int w : WIDTHS) {
			if (w > cap) {
				continue;
			}
			int dx = (int) Math.round(ux * w);
			int dz = (int) Math.round(uz * w);
			if (dx == 0 && dz == 0) {
				continue;
			}
			int other = region.at(bx + dx, bz + dz);
			if (other == UNKNOWN || other == base || !symmetric(base, other, layer)) {
				continue;
			}
			if (halfWidthBlocks(base, other, layer, k) >= w) {
				return other == own || follows(own, other, layer) ? other : base;
			}
		}
		return base;
	}

	private static final int ANGLES = 1024;
	private static final double[] COS = new double[ANGLES];
	private static final double[] SIN = new double[ANGLES];

	static {
		for (int i = 0; i < ANGLES; i++) {
			COS[i] = Math.cos(2 * Math.PI * i / ANGLES);
			SIN[i] = Math.sin(2 * Math.PI * i / ANGLES);
		}
	}

	/**
	 * Random vector of the column at world (x, z): the horizontal part of a uniform point on the unit sphere, whose
	 * projection on any direction is uniform in [−1, 1] (the angle in 1024 steps).
	 */
	static double[] vector(long seed, int x, int z, int patch) {
		double[] u = new double[2];
		vector(seed, x, z, patch, u);
		return u;
	}

	private static void vector(long seed, int x, int z, int patch, double[] out) {
		long h;
		if (patch <= 1) {
			h = hash(seed, x, z);
		} else {
			long j = hash(seed ^ 0x2545_F491_4F6C_DD1DL, x, z);
			int jx = (int) ((j & 0xFF) % 3) - 1;
			int jz = (int) ((j >>> 8 & 0xFF) % 3) - 1;
			h = hash(seed ^ 0x7A7C_4E5BL, Math.floorDiv(x + jx, patch), Math.floorDiv(z + jz, patch));
		}
		double s = 2 * TreeStandPlan.unit(h) - 1;
		int phi = (int) (Noise.mix(h) >>> 54);
		double r = Math.sqrt(Math.max(0, 1 - s * s));
		out[0] = r * COS[phi];
		out[1] = r * SIN[phi];
	}

	private static long hash(long seed, int x, int z) {
		return Noise.mix(seed ^ Noise.mix(x * 0x9E37_79B9_7F4A_7C15L + z * 0xC2B2_AE3D_27D4_EB4FL));
	}

	/**
	 * Forest edge classes of a chunk, per layer ({@link Edge} ordinals): the mantle for the shrubs of the understory, the
	 * fringe for the ground layer.
	 */
	public record Edges(byte[] shrubs, byte[] ground) {
		static final Edges NONE = new Edges(new byte[256], new byte[256]);
	}

	/**
	 * Forest edges of the 256 columns of the chunk where a forest (woodland of the dry or wet kind) meets open land
	 * (meadows, fields, heath, gray dunes, alpine grassland, peatlands and reedbeds), by the distance to the nearest column
	 * of the other side (3-4 chamfer distance within the region) at the column moved by the meander:
	 * <ul>
	 * <li>shrubs (the understory): the mantle on the outer {@value #MANTLE} m·k of the forest and the first
	 * {@value #MANTLE_OUT} m·k of the open land, in clumps with gaps on about {@value #GAP_SHARE} of the edge, and single
	 * shrubs up to twice as far; an open column of the mantle takes the code of the nearest forest column in
	 * {@code shrubCodes}, so the forest's mantle shrubs grow on it;</li>
	 * <li>ground layer: the fringe from up to {@value #FRINGE_IN} m·k under the canopy to {@value #FRINGE} m·k into the
	 * open land; its border with the forest floor runs in patches between {@value #FRINGE_IN} m·k under the canopy and as
	 * far out, so a forest column of the fringe takes the code of the nearest open column in {@code groundCodes} (the open
	 * land's fringe rule) and an open column before the fringe the code of the nearest forest column (forest floor).</li>
	 * </ul>
	 *
	 * @param noises the world's noises, or null for the nominal widths without the meander, clumps and patches (tests)
	 */
	public static Edges edges(Region region, @Nullable Noises noises, int[] shrubCodes, int[] groundCodes) {
		double k = region.k();
		double depth = Math.max(MANTLE_MIN, MANTLE * k);
		double out = Math.max(1, MANTLE_OUT * k);
		double fringe = Math.max(FRINGE_MIN, FRINGE * k);
		double inner = Math.max(1, FRINGE_IN * k);
		double widest = 1.5 * Math.max(2 * depth, Math.max(fringe, out + 3)) + 2;
		int warpReach = noises == null ? 0 : WARP;
		int reach = Math.min(REACH, (int) Math.ceil(widest) + warpReach + 1);
		int lo = -reach;
		int n = 16 + 2 * reach;
		// Most chunks have no forest edge within reach: no distance transform for them.
		if (region.uniform()) {
			return Edges.NONE;
		}
		boolean anyForest = false;
		boolean anyOpen = false;
		for (int a = 0; a < n && !(anyForest && anyOpen); a++) {
			for (int b = 0; b < n; b++) {
				int code = region.at(lo + a, lo + b);
				if (code != UNKNOWN && landZone(code)) {
					Kind kd = kind(code);
					anyForest |= woodland(kd);
					anyOpen |= open(kd);
				}
			}
		}
		if (!anyForest || !anyOpen) {
			return Edges.NONE;
		}
		// Chamfer distances (thirds of a block) to the nearest forest and open column, and those columns.
		int[] toForest = new int[n * n];
		int[] forest = new int[n * n];
		int[] toOpen = new int[n * n];
		int[] openSrc = new int[n * n];
		int far = Integer.MAX_VALUE / 4;
		for (int a = 0; a < n; a++) {
			for (int b = 0; b < n; b++) {
				int code = region.at(lo + a, lo + b);
				Kind kd = code == UNKNOWN || !landZone(code) ? Kind.WATER : kind(code);
				int c = a * n + b;
				toForest[c] = woodland(kd) ? 0 : far;
				forest[c] = woodland(kd) ? c : -1;
				toOpen[c] = open(kd) ? 0 : far;
				openSrc[c] = open(kd) ? c : -1;
			}
		}
		chamfer(toForest, forest, n);
		chamfer(toOpen, openSrc, n);
		byte[] shrubs = new byte[256];
		byte[] ground = new byte[256];
		byte[] warp = noises == null ? null : region.warp(noises.seed());
		long seed = noises == null ? 0 : Noise.mix(noises.seed() ^ 0x5B0B_ED6EL);
		int x0 = region.chunkX() << 4;
		int z0 = region.chunkZ() << 4;
		double gapWave = Math.max(GAP_MIN_WAVELENGTH, GAP_WAVELENGTH * k);
		double patchWave = Math.max(PATCH_MIN_WAVELENGTH, PATCH_WAVELENGTH * k);
		for (int i = 0; i < 256; i++) {
			int x = i >> 4;
			int z = i & 15;
			int own = region.at(x, z);
			if (!takes(own)) {
				continue;
			}
			int qx = x;
			int qz = z;
			int code = own;
			if (warp != null && (warp[2 * i] != 0 || warp[2 * i + 1] != 0)) {
				int c = region.at(x + warp[2 * i], z + warp[2 * i + 1]);
				if (c != UNKNOWN && landZone(c) && (c == own || edgePair(own, c) || symmetric(own, c, Layer.TREES))) {
					qx += warp[2 * i];
					qz += warp[2 * i + 1];
					code = c;
				}
			}
			Kind kd = kind(code);
			boolean isForest = woodland(kd);
			if (!isForest && !open(kd)) {
				continue;
			}
			int c = (qx - lo) * n + qz - lo;
			double d = (isForest ? toOpen[c] : toForest[c]) / 3.0;
			if (d > widest) {
				continue;
			}
			int wx = x0 + x;
			int wz = z0 + z;
			double f = 1;
			boolean clump = true;
			double patch = 0.5;
			// Nominal widths: no single shrubs beyond the mantle.
			double chance = 1;
			if (noises != null) {
				f = 1 + 0.5 * Math.clamp(noises.widths().at(wx, wz, EDGE_WAVELENGTH * k) / 0.7, -1, 1);
				clump = LandscapeModel.noiseQuantile(noises.patches().at(wx, wz, gapWave)) >= GAP_SHARE;
				patch = LandscapeModel.noiseQuantile(noises.patches().at(wx + 7_717.0, wz - 2_903.0, patchWave));
				chance = TreeStandPlan.unit(hash(seed, wx, wz));
			}
			// Signed distance from the forest edge into the open land, and the border of the forest floor and the fringe.
			double s = isForest ? -d : d;
			double floorEdge = inner * (2 * patch - 1);
			if (noises == null) {
				floorEdge = -inner;
			}
			// The mantle in clumps (a few single shrubs in the gaps), single shrubs up to twice as deep into the forest and up
			// to 3 blocks farther onto the open land.
			double mantleIn = depth * f;
			double mantleOut = out * f;
			boolean mantle;
			if (s <= 0) {
				mantle = d <= mantleIn ? clump || chance < GAP_CHANCE : chance < SPORADIC * (2 - d / mantleIn);
			} else {
				mantle = d <= mantleOut ? clump || chance < GAP_CHANCE : chance < SPORADIC * (1 - (d - mantleOut) / 3);
			}
			if (mantle) {
				shrubs[i] = (byte) Edge.MANTLE.ordinal();
				if (!isForest) {
					int src = forest[c];
					shrubCodes[i] = region.at(lo + src / n, lo + src % n);
				}
			}
			if (s >= floorEdge && s <= fringe * f) {
				ground[i] = (byte) Edge.FRINGE.ordinal();
				if (isForest) {
					int src = openSrc[c];
					groundCodes[i] = region.at(lo + src / n, lo + src % n);
				}
			} else if (!isForest && s < floorEdge) {
				int src = forest[c];
				groundCodes[i] = region.at(lo + src / n, lo + src % n);
			}
		}
		return new Edges(shrubs, ground);
	}

	/**
	 * Two-pass 3-4 chamfer distance transform in place (distances in thirds of a block); {@code src}, when given, carries
	 * the index of the nearest source cell along.
	 */
	private static void chamfer(int[] d, int @Nullable [] src, int n) {
		// Forward pass: the neighbors above and to the left; backward pass: the mirrored ones.
		int[][] offsets = {{-1, 0, 3}, {-1, -1, 4}, {-1, 1, 4}, {0, -1, 3}};
		for (int pass = 0; pass < 2; pass++) {
			int sign = pass == 0 ? 1 : -1;
			for (int s = 0; s < n * n; s++) {
				int c = pass == 0 ? s : n * n - 1 - s;
				int a = c / n;
				int b = c % n;
				for (int[] o : offsets) {
					int na = a + sign * o[0];
					int nb = b + sign * o[1];
					if (na < 0 || na >= n || nb < 0 || nb >= n) {
						continue;
					}
					int nc = na * n + nb;
					if (d[nc] + o[2] < d[c]) {
						d[c] = d[nc] + o[2];
						if (src != null) {
							src[c] = src[nc];
						}
					}
				}
			}
		}
	}
}
