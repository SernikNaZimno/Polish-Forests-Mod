package pl.polishforests.worldgen.feature.plan;

import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Ecotones between biomes and habitats (rule Z10, docs/03-m2-biomy.md §1, step S8b): the decoration does not switch
 * palettes on the line where the habitat code changes, but in a transition belt, whose width depends on the pair of
 * habitats. A pure function of the habitat codes of a chunk and its 8 neighbors ({@code ChunkHabitats}, all past
 * {@code fill()} when a chunk is decorated), the world seed and a layer salt; no new samples of the landscape model.
 *
 * <p><b>Mixing.</b> Each column (or each small patch, {@link #effective}) takes a random vector u with an isotropic
 * distribution whose projection on every direction is uniform in [−1, 1] (the horizontal part of a uniform point on the
 * unit sphere). The column then takes the code of the column at u·w for the largest width class w ({@link #WIDTHS}) at
 * which that code differs from its own and the half-width of the pair ({@link #halfWidth}) is at least w. At a straight
 * border between two habitats with half-width H the share of the other side's code is thus (1 − d/H) / 2 at distance d
 * from the border: a linear ramp from 50% at the border to 0 at H on both sides, so the transition belt is 2H wide.
 * Water, the narrow zones (waterside zones, beach and cliff zones, the tree row; Z4) and the bare sand of beaches never
 * mix: their borders are sharp in nature too, and the zones have their own belts.
 *
 * <p><b>Reach.</b> A decorated chunk can read the codes of its 8 neighbors only, so u·w stays within {@value #REACH}
 * blocks of the column: the half-widths are in meters·k (Z7) and capped at {@value #REACH} blocks (in the realistic scale
 * the widest ecotones are 32 m instead of 48 m).
 *
 * <p><b>Forest edges</b> ({@link #edges}): where a forest meets open land (meadow, field, heath, gray dune, peatland),
 * the forest columns within {@value #MANTLE} m·k of the open land and the open columns within {@value #MANTLE_OUT} m·k
 * of the forest are the mantle (shrubs), the open columns within {@value #FRINGE} m·k of the forest the fringe (tall
 * herbs); the widths vary by a noise of wavelength {@value #EDGE_WAVELENGTH} m·k between half and one and a half of
 * these, so the belts follow the edge without straight lines.
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

	/** Salt of the ecotone vectors of the tree stand. */
	public static final long TREES_SALT = TreeStandPlan.SALT;
	/** Salt of the ecotone vectors of the deadwood and the understory (one draw for both layers). */
	public static final long PLANTS_SALT = 0x9A1D_E77EL;
	/** Salt and patch size of the ecotone vectors of the ground layer (clumps of the other side's ground layer). */
	public static final long GROUND_SALT = 0x6B0D_1A7EL;
	public static final int GROUND_PATCH = 3;

	/** Mantle depth into the forest (m·k). */
	public static final double MANTLE = 4;
	/** Mantle reach into the open land (m·k): shrubs grow out of the forest a little. */
	public static final double MANTLE_OUT = 1.5;
	/** Fringe width in the open land (m·k). */
	public static final double FRINGE = 6;
	/** Wavelength of the noise of the edge widths (m·k). */
	public static final double EDGE_WAVELENGTH = 16;

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
		/** The fringe of tall herbs in the open land in front of the mantle. */
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

	private static long mask(HabitatBiome... biomes) {
		long m = 0;
		for (HabitatBiome b : biomes) {
			m |= 1L << b.ordinal();
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

	/** Whether the zone of the code takes part in ecotones: no zone, or the stunted spruces of the timberline. */
	private static boolean mixingZone(int code) {
		Zone z = Habitat.zone(code);
		return z == Zone.NONE || z == Zone.TIMBERLINE;
	}

	/**
	 * Half-width (meters at the realistic scale, times k in the world, Z7) of the ecotone between the habitat code
	 * {@code own} of a column and the code {@code other} of a column beyond the border, for a layer; 0: a sharp border.
	 * Similar forests 24 m (soil 8 m), dry and wet forests 12 m (6 m), forest and scrub 12 m (plants 8 m, soil 4 m),
	 * forest and open land 8 m for the trees (6 m by peatlands) and the soil 4 m (3 m), the plants none (the mantle and the
	 * fringe instead), open land of one kind 12 m (6 m), dry and wet open land and scrub and open land 8 m (4 m), white and
	 * gray dunes 4 m (2 m); water, beaches, the narrow zones and their neighbors 0. In the plant layers the biomes of the
	 * density rule of §4.6 do not take the code of another biome.
	 */
	public static double halfWidth(int own, int other, Layer layer) {
		if (!mixingZone(own) || !mixingZone(other)) {
			return 0;
		}
		Kind a = kind(own);
		Kind b = kind(other);
		if (a == Kind.WATER || b == Kind.WATER) {
			return 0;
		}
		if (layer == Layer.PLANTS && (DENSE >>> (own & 63) & 1) != 0 && (own & 63) != (other & 63)
				&& (DENSE >>> (other & 63) & 1) == 0) {
			return 0;
		}
		int t = layer.ordinal();
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

	/** Half-width in blocks: {@link #halfWidth} times k, at most {@link #REACH}. */
	public static int halfWidthBlocks(int own, int other, Layer layer, double k) {
		double h = halfWidth(own, other, layer) * k;
		return h <= 0 ? 0 : (int) Math.min(REACH, Math.max(1, Math.round(h)));
	}

	/**
	 * Codes of a chunk and its 8 neighbors.
	 *
	 * @param chunkX chunk coordinates
	 * @param codes  codes of the region, index {@code (x + REACH) * SPAN + z + REACH} for the chunk-local coordinates
	 *               x, z in [−{@value #REACH}, 15 + {@value #REACH}], {@link #UNKNOWN} where not known
	 * @param k      the scale's k (Z7)
	 */
	public record Region(int chunkX, int chunkZ, int[] codes, double k) {
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
	}

	/**
	 * Effective codes of the 256 columns of the chunk for a layer: the code each column's palette is chosen by (see the
	 * class description). Columns of a patch size above 1 share their random vector with their patch, a cell of that size
	 * whose border is shifted by up to one block per column, so the codes of the other side come in small clumps (soil
	 * patches rather than single blocks).
	 *
	 * @param salt  salt of the layer (each layer draws its own vectors)
	 * @param patch patch size in blocks (1: each column its own vector)
	 */
	public static int[] effective(Region region, Layer layer, long worldSeed, long salt, int patch) {
		int[] out = new int[256];
		long seed = Noise.mix(worldSeed ^ Noise.mix(salt ^ 0xEC07_0E5AL));
		int x0 = region.chunkX() << 4;
		int z0 = region.chunkZ() << 4;
		for (int i = 0; i < 256; i++) {
			int x = i >> 4;
			int z = i & 15;
			int own = region.at(x, z);
			out[i] = own == UNKNOWN ? own : pick(region, layer, own, x, z, vector(seed, x0 + x, z0 + z, patch));
		}
		return out;
	}

	/** Effective code of one column at chunk-local (x, z) with the random vector {@code u} = {ux, uz}. */
	private static int pick(Region region, Layer layer, int own, int x, int z, double[] u) {
		if (!mixingZone(own) || kind(own) == Kind.WATER) {
			return own;
		}
		for (int w : WIDTHS) {
			int dx = (int) Math.round(u[0] * w);
			int dz = (int) Math.round(u[1] * w);
			if (dx == 0 && dz == 0) {
				continue;
			}
			int other = region.at(x + dx, z + dz);
			if (other == UNKNOWN || other == own) {
				continue;
			}
			if (halfWidthBlocks(own, other, layer, region.k()) >= w) {
				return other;
			}
		}
		return own;
	}

	/**
	 * Random vector of the column at world (x, z): the horizontal part of a uniform point on the unit sphere, whose
	 * projection on any direction is uniform in [−1, 1].
	 */
	static double[] vector(long seed, int x, int z, int patch) {
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
		double phi = 2 * Math.PI * TreeStandPlan.unit(Noise.mix(h));
		double r = Math.sqrt(Math.max(0, 1 - s * s));
		return new double[] {r * Math.cos(phi), r * Math.sin(phi)};
	}

	private static long hash(long seed, int x, int z) {
		return Noise.mix(seed ^ Noise.mix(x * 0x9E37_79B9_7F4A_7C15L + z * 0xC2B2_AE3D_27D4_EB4FL));
	}

	/**
	 * Forest edge classes of the 256 columns of the chunk ({@link Edge} ordinals): mantle and fringe where a forest
	 * (woodland of the dry or wet kind) meets open land (meadows, fields, heath, gray dunes, alpine grassland, peatlands
	 * and reedbeds), by the distance to the nearest column of the other side (3-4 chamfer distance within the region).
	 * An open column of the mantle belongs to the forest's mantle: when {@code codes} is given, its code there becomes
	 * the code of the nearest forest column, so the forest's mantle shrubs grow on it.
	 *
	 * @param widths noise of the edge widths (null: the nominal widths)
	 * @param codes  effective codes of the chunk's columns to update, or null
	 */
	public static byte[] edges(Region region, Noise widths, int[] codes) {
		byte[] out = new byte[256];
		double k = region.k();
		int reach = (int) Math.ceil(Math.max(MANTLE, FRINGE) * 1.5 * k) + 1;
		int lo = -reach;
		int n = 16 + 2 * reach;
		// Most chunks have no forest edge within reach: no distance transform for them.
		boolean anyForest = false;
		boolean anyOpen = false;
		for (int a = 0; a < n && !(anyForest && anyOpen); a++) {
			for (int b = 0; b < n; b++) {
				int code = region.at(lo + a, lo + b);
				if (code != UNKNOWN && mixingZone(code)) {
					Kind kd = kind(code);
					anyForest |= woodland(kd);
					anyOpen |= open(kd);
				}
			}
		}
		if (!anyForest || !anyOpen) {
			return out;
		}
		double widest = 1.5 * k * Math.max(MANTLE, FRINGE) + 2;
		// Chamfer distances (thirds of a block) to the nearest forest and open column, and the nearest forest column.
		int[] toForest = new int[n * n];
		int[] forest = new int[n * n];
		int[] toOpen = new int[n * n];
		int far = Integer.MAX_VALUE / 4;
		for (int a = 0; a < n; a++) {
			for (int b = 0; b < n; b++) {
				int code = region.at(lo + a, lo + b);
				Kind kd = code == UNKNOWN || !mixingZone(code) ? Kind.WATER : kind(code);
				int c = a * n + b;
				toForest[c] = woodland(kd) ? 0 : far;
				forest[c] = woodland(kd) ? c : -1;
				toOpen[c] = open(kd) ? 0 : far;
			}
		}
		chamfer(toForest, forest, n);
		chamfer(toOpen, null, n);
		int x0 = region.chunkX() << 4;
		int z0 = region.chunkZ() << 4;
		for (int i = 0; i < 256; i++) {
			int x = i >> 4;
			int z = i & 15;
			int code = region.at(x, z);
			if (!mixingZone(code)) {
				continue;
			}
			Kind kd = kind(code);
			int c = (x - lo) * n + z - lo;
			int rel = woodland(kd) ? toOpen[c] : open(kd) ? toForest[c] : far;
			if (rel / 3.0 > widest) {
				continue;
			}
			double f = widths == null ? 1 : 1 + 0.5 * Math.clamp(widths.at(x0 + x, z0 + z, EDGE_WAVELENGTH * k) / 0.7, -1, 1);
			if (woodland(kd)) {
				if (toOpen[c] / 3.0 <= Math.max(1, MANTLE * k * f)) {
					out[i] = (byte) Edge.MANTLE.ordinal();
				}
			} else if (open(kd)) {
				double d = toForest[c] / 3.0;
				if (d <= Math.max(1, MANTLE_OUT * k * f)) {
					out[i] = (byte) Edge.MANTLE.ordinal();
					if (codes != null) {
						int src = forest[c];
						codes[i] = region.at(lo + src / n, lo + src % n);
					}
				} else if (d <= Math.max(2, FRINGE * k * f)) {
					out[i] = (byte) Edge.FRINGE.ordinal();
				}
			}
		}
		return out;
	}

	/**
	 * Two-pass 3-4 chamfer distance transform in place (distances in thirds of a block); {@code src}, when given, carries
	 * the index of the nearest source cell along.
	 */
	private static void chamfer(int[] d, int[] src, int n) {
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
