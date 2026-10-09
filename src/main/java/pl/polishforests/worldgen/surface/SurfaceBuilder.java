package pl.polishforests.worldgen.surface;

import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Builds the surface plan of a chunk ({@link ChunkSurface}, docs/03-m2-biomy.md §7) from the 256 column samples and
 * habitat codes that {@code fill()} already has: soil profile once per column ({@link SoilBlocks}), the bank shelf and
 * the shore belt of the bed ({@link BankShelf}), the micro-relief ({@link Microrelief}) and the loose cover above the
 * rock. Pure Java and deterministic: a pure function of the world seed, the settings and the samples; the only samples it
 * takes itself are columns outside the chunk within the shelf's reach of the border, read from a cache shared by the
 * chunks of the seed ({@link ColumnCache}: each plan publishes its border columns, and the generator reuses the samples
 * that a neighbor's plan took, so each column is sampled about once).
 *
 * <p><b>Cover in blocks (§7.1).</b> The cover of loose deposits is {@code coverDepth} meters thick. Before S6 the generator
 * compared it with the depth in blocks, so in the gameplay scale (about 0.4 block per meter in the lowland) the cover was
 * 2.5–4 times too thick. With {@code coverInBlocks} the rock starts at {@code vertical.topBlockY(surface − coverDepth)}
 * (world setting {@code cover_in_blocks}; missing in older worlds, which keep the old cover).
 *
 * <p><b>Diagnostic mode.</b> With {@code -D}{@value #DEBUG_PROPERTY}{@code =true} the top block of every column shows
 * its zone, or its biome when it has no zone, so narrow belts can be checked in the game at a glance; every zone and
 * every biome has its own block ({@link Material#zoneColor}, {@link Material#biomeColor}).
 */
public final class SurfaceBuilder {
	/** System property of the diagnostic mode. */
	public static final String DEBUG_PROPERTY = "polishforests.debug.habitats";
	/** Wavelengths of the soil patch noises (blocks). */
	static final double SOIL_WAVELENGTH = 7;
	static final double SOIL_MINOR_WAVELENGTH = 5;
	/** Noise for small gravel patches of the channel deposit; independent of the world seed (as before S6). */
	private static final Noise DETAIL = new Noise(0x5EED_DE7A_11L);
	private static final WaterKind[] KINDS = WaterKind.values();
	/** Least loose cover of a dry column in blocks: its soil top block. */
	static final int MIN_SOIL = 1;

	/** Number of neighbor columns outside the chunk sampled from the model for the shelf (diagnostics). */
	public static final java.util.concurrent.atomic.LongAdder OUTSIDE_SAMPLES = ColumnCache.MISSES;
	/** Number of neighbor columns outside the chunk read from the shared cache instead (diagnostics). */
	public static final java.util.concurrent.atomic.LongAdder OUTSIDE_HITS = ColumnCache.HITS;
	/** Number of columns whose sample the generator took from the cache instead of sampling them again (diagnostics). */
	public static final java.util.concurrent.atomic.LongAdder REUSED_SAMPLES = ColumnCache.REUSED;

	/** Samples a column of the landscape model (for neighbors outside the chunk). */
	@FunctionalInterface
	public interface Sampler {
		ColumnSample sample(int x, int z);
	}

	private final VerticalScale vertical;
	/** Summaries of the columns near chunk borders, shared by the chunks of the world seed (review of S6, round 1). */
	private final ColumnCache cache = new ColumnCache(1 << 18);
	private final boolean coverInBlocks;
	private final boolean debug;
	private final Noise soil;
	private final Noise micro;

	/**
	 * @param seed          world seed
	 * @param coverInBlocks the loose cover in blocks ({@code cover_in_blocks}); false: the pre-S6 cover
	 * @param debug         diagnostic mode (zones and biomes painted on the top)
	 */
	public SurfaceBuilder(long seed, VerticalScale vertical, boolean coverInBlocks, boolean debug) {
		this.vertical = vertical;
		this.coverInBlocks = coverInBlocks;
		this.debug = debug;
		Noise root = new Noise(seed);
		this.soil = root.derive("surface.soil");
		this.micro = root.derive("surface.microrelief");
	}

	/** Whether the diagnostic mode is on in this process. */
	public static boolean debugFromSystem() {
		return Boolean.getBoolean(DEBUG_PROPERTY);
	}

	public VerticalScale vertical() {
		return vertical;
	}

	/** Y of the top ground block for a sample (the model top). */
	public int topY(ColumnSample s, int minY, int maxY) {
		return Math.clamp(vertical.topBlockY(s.surface()), minY + 1, maxY);
	}

	/** Y of the top water block for a sample, or {@link ChunkSurface#NO_WATER}. */
	public int waterTopY(ColumnSample s, int maxY) {
		return s.hasWater() ? Math.min(vertical.topBlockY(s.waterLevel()), maxY) : ChunkSurface.NO_WATER;
	}

	/**
	 * Packed summary of a column for the plans of its neighbors: model top and water top (blocks, +64), water kind and
	 * whether its own fields put water near ({@link BankShelf#waterMayBeNear}).
	 */
	int summary(ColumnSample s, int minY, int maxY) {
		int top = topY(s, minY, maxY);
		int water = waterTopY(s, maxY);
		int w = water > top ? water + Y_OFFSET : NO_WATER_CODE;
		return (top + Y_OFFSET) | w << 12 | s.waterKind().ordinal() << 24 | (BankShelf.waterMayBeNear(s) ? 1 << 27 : 0);
	}

	private static final int Y_OFFSET = 64;
	private static final int NO_WATER_CODE = 0xFFF;

	static int summaryTop(int summary) {
		return (summary & 0xFFF) - Y_OFFSET;
	}

	static int summaryWater(int summary) {
		int w = summary >>> 12 & 0xFFF;
		return w == NO_WATER_CODE ? ChunkSurface.NO_WATER : w - Y_OFFSET;
	}

	static WaterKind summaryKind(int summary) {
		return KINDS[summary >>> 24 & 7];
	}

	static boolean summaryNear(int summary) {
		return (summary & 1 << 27) != 0;
	}

	/**
	 * Sample of a column that the plan of a neighboring chunk already took from the model (and kept), or null; the
	 * generator uses it instead of sampling the column again.
	 */
	public ColumnSample reuse(int x, int z) {
		return cache.takeSample(x, z);
	}

	/**
	 * Surface plan of a chunk.
	 *
	 * @param columns   samples of the 256 columns, index {@code x * 16 + z}
	 * @param codes     habitat codes of the columns
	 * @param deepRockY Y below which the rock is deepslate (plus 0–7 by the column)
	 * @param outside   sampler for neighbors outside the chunk
	 */
	public ChunkSurface build(ColumnSample[] columns, int[] codes, int minX, int minZ, int minY, int maxY, int deepRockY,
			Sampler outside) {
		ChunkSurface out = new ChunkSurface();
		Work w = new Work(this, columns, codes, minX, minZ, minY, maxY, outside);
		for (int i = 0; i < 256; i++) {
			ColumnSample s = columns[i];
			int wx = minX + (i >> 4);
			int wz = minZ + (i & 15);
			int top = w.modelTop[i];
			out.top[i] = top;
			out.modelTop[i] = top;
			out.modelWater[i] = w.modelWater[i];
			out.waterTop[i] = w.modelWater[i];
			out.rockTop[i] = coverInBlocks ? Math.min(top, vertical.topBlockY(s.surface() - s.coverDepth()))
					: top - (int) Math.ceil(s.coverDepth());
			out.bedrockTop[i] = minY + (int) (Noise.mix(wx * 341873128712L + wz * 132897987541L) >>> 62);
			out.deepRockY[i] = deepRockY + ((wx * 31 + wz * 17) & 7);
			out.substrate[i] = (byte) s.substrate().ordinal();
			out.bandOffset[i] = (wx >> 5) - (wz >> 6);
			out.gravelPatch[i] = s.substrate() == Substrate.RIVERBED && DETAIL.at(wx, wz, 7) > 0.15;
		}
		BankShelf.apply(w, out);
		Microrelief.apply(w, out, micro, minX, minZ, maxY);
		for (int i = 0; i < 256; i++) {
			// Every column always has its soil or bed top, also where the model has no loose cover (thin regolith on the
			// ridges of the Beskids, mountain stream beds in the gameplay scale): before S6 such a dry column had bare
			// stone, so trees could not grow there, and with the cover in blocks (S6) a stream bed could be stone.
			out.rockTop[i] = Math.min(out.rockTop[i], out.top[i] - MIN_SOIL);
		}
		for (int i = 0; i < 256; i++) {
			ColumnSample s = columns[i];
			int code = codes[i];
			int wx = minX + (i >> 4);
			int wz = minZ + (i & 15);
			int flags = out.flags[i];
			double q2 = LandscapeModel.noiseQuantile(soil.at(wx + 1_013.0, wz - 517.0, SOIL_MINOR_WAVELENGTH));
			int profile;
			if ((flags & ChunkSurface.PUDDLE) != 0 || (flags & ChunkSurface.PUDDLE_MUD) != 0) {
				profile = SoilBlocks.bed(SoilBlocks.Bed.PUDDLE, false, q2, 1);
			} else if (w.wet(i)) {
				boolean belt = BankShelf.shoreBed(w, i);
				if (belt) {
					out.flags[i] |= ChunkSurface.SHORE_BED;
				}
				profile = SoilBlocks.bed(bed(s, code), belt, q2, s.waterLevel() - s.surface());
			} else {
				double q1 = LandscapeModel.noiseQuantile(soil.at(wx, wz, SOIL_WAVELENGTH));
				Soil soilType = Habitat.soil(code);
				profile = SoilBlocks.dry(soilType, Habitat.zone(code), Habitat.biome(code), q1, q2, s.terrain().lowShore());
				if (soilType == Soil.BOG_PEAT || soilType == Soil.FEN_PEAT) {
					// The whole peat profile lies on the loose cover, also where the model cover is thin.
					out.rockTop[i] = Math.min(out.rockTop[i], out.top[i] - SoilBlocks.depth(profile));
				}
				if ((flags & ChunkSurface.HUMMOCK) != 0) {
					// The hummock on top, the former top block under it.
					profile = SoilBlocks.profile(Microrelief.hummock(Habitat.biome(code)), SoilBlocks.top(profile), 1,
							SoilBlocks.layer1(profile), SoilBlocks.layer1Depth(profile));
				}
			}
			if (debug) {
				Zone zone = Habitat.zone(code);
				Material paint = zone != Zone.NONE ? Material.zoneColor(zone.ordinal() - 1)
						: Material.biomeColor(Habitat.biome(code).ordinal());
				profile = SoilBlocks.profile(paint, SoilBlocks.layer1(profile), SoilBlocks.layer1Depth(profile),
						SoilBlocks.layer2(profile), SoilBlocks.layer2Depth(profile));
				out.rockTop[i] = Math.min(out.rockTop[i], out.top[i] - 1);
			}
			out.profile[i] = profile;
		}
		// The columns that the plans of the neighboring chunks may look up: within the ramp's reach of the border, and
		// near water also farther, within the guard's reach (BankShelf.GUARD_REACH covers the whole chunk).
		int r = BankShelf.RAMP;
		for (int i = 0; i < 256; i++) {
			int x = i >> 4;
			int z = i & 15;
			if (x < r || x > 15 - r || z < r || z > 15 - r
					|| BankShelf.waterMayBeWithin(columns[i], 2 * BankShelf.GUARD_REACH)) {
				cache.publish(minX + x, minZ + z, summary(columns[i], minY, maxY));
			}
		}
		return out;
	}

	/** Bed kind of a water column. */
	private static SoilBlocks.Bed bed(ColumnSample s, int code) {
		HabitatBiome biome = Habitat.biome(code);
		return switch (s.waterKind()) {
			case RIVER -> biome == HabitatBiome.STREAM ? SoilBlocks.Bed.STREAM : SoilBlocks.Bed.CHANNEL;
			case SEA -> biome == HabitatBiome.LAGOON ? SoilBlocks.Bed.LAGOON : SoilBlocks.Bed.SEA;
			default -> biome == HabitatBiome.DYSTROPHIC_LAKE ? SoilBlocks.Bed.DYSTROPHIC_LAKE : SoilBlocks.Bed.LAKE;
		};
	}

	/** Working data of one build: model tops and water, and the neighbors outside the chunk sampled on demand. */
	static final class Work {
		final SurfaceBuilder builder;
		final ColumnSample[] columns;
		final int[] codes;
		final int minX;
		final int minZ;
		final int minY;
		final int maxY;
		final Sampler outside;
		final int[] modelTop = new int[256];
		final int[] modelWater = new int[256];
		final double[] microQ = new double[256];
		/**
		 * Result of {@link #nearestWater}: Chebyshev distance of the nearest water, the ramp top and the fade term
		 * ({@link BankShelf#drop}).
		 */
		int nearestDist;
		int rampTop;
		int fadeTerm;

		Work(SurfaceBuilder builder, ColumnSample[] columns, int[] codes, int minX, int minZ, int minY, int maxY,
				Sampler outside) {
			this.builder = builder;
			this.columns = columns;
			this.codes = codes;
			this.minX = minX;
			this.minZ = minZ;
			this.minY = minY;
			this.maxY = maxY;
			this.outside = outside;
			for (int i = 0; i < 256; i++) {
				modelTop[i] = builder.topY(columns[i], minY, maxY);
				int water = builder.waterTopY(columns[i], maxY);
				modelWater[i] = water > modelTop[i] ? water : ChunkSurface.NO_WATER;
			}
		}

		/** Whether the column has water on its model top. */
		boolean wet(int i) {
			return modelWater[i] != ChunkSurface.NO_WATER;
		}

		/**
		 * Water top of the neighbor of column {@code i} in direction {@code dir} (0: −x, 1: +x, 2: −z, 3: +z), or
		 * {@link ChunkSurface#NO_WATER} when it is dry.
		 */
		int neighborWater(int i, int dir) {
			int n = neighbor(i, dir);
			if (n >= 0) {
				return modelWater[n];
			}
			return summaryWater(outsideSummary(i, dir));
		}

		WaterKind neighborKind(int i, int dir) {
			int n = neighbor(i, dir);
			if (n >= 0) {
				return columns[n].waterKind();
			}
			return summaryKind(outsideSummary(i, dir));
		}

		/**
		 * Final top of the neighbor of column {@code i} in direction {@code dir} outside the chunk when it is dry and
		 * surely not lowered by the shelf: no water within the ramp's reach in the chunk and none near by its own fields;
		 * otherwise {@link Integer#MIN_VALUE}. Its micro-relief can only keep or raise the wall (a puddle there holds
		 * water only at its model top, a hummock is higher). For the puddles at the chunk edge ({@link Microrelief}).
		 */
		int outsideSafeTop(int i, int dir) {
			int summary = outsideSummary(i, dir);
			if (summaryWater(summary) != ChunkSurface.NO_WATER || summaryNear(summary)) {
				return Integer.MIN_VALUE;
			}
			int x = (i >> 4) + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
			int z = (i & 15) + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
			int r = BankShelf.RAMP;
			for (int nx = Math.max(0, x - r); nx <= Math.min(15, x + r); nx++) {
				for (int nz = Math.max(0, z - r); nz <= Math.min(15, z + r); nz++) {
					if (freshWater(nx << 4 | nz) != ChunkSurface.NO_WATER) {
						return Integer.MIN_VALUE;
					}
				}
			}
			return summaryTop(summary);
		}

		/** Model water top of an in-chunk column when it is not sea water, else {@link ChunkSurface#NO_WATER}. */
		int freshWater(int n) {
			return modelWater[n] != ChunkSurface.NO_WATER && columns[n].waterKind() != WaterKind.SEA ? modelWater[n]
					: ChunkSurface.NO_WATER;
		}

		/**
		 * Looks for fresh (not sea) model water within Chebyshev {@link BankShelf#RAMP} of a column: sets
		 * {@link #nearestDist} (the distance of the nearest water), {@link #rampTop}, the lowest {@code W + k − 1} over the
		 * water columns within reach (water top {@code W}, distance {@code k}: a ramp of one block per column from every
		 * water, 1-Lipschitz also where the water level steps) and {@link #fadeTerm}, the lowest {@code k − W} (the fade of
		 * high banks, {@link BankShelf#drop}), and returns true, or returns false without water. Columns outside the chunk
		 * are sampled when the window reaches beyond the chunk and the column's own fields put water near
		 * ({@link BankShelf#waterMayBeNear}), so the result is the same whichever chunk computes it.
		 */
		boolean nearestWater(int i) {
			int x = i >> 4;
			int z = i & 15;
			// The rings up to the chunk border lie in the chunk; when water lies in one of them, the nearest distance is
			// known and the outside is not sampled (a lower water across the border could lower the ramp by a block).
			int border = Math.min(Math.min(x, 15 - x), Math.min(z, 15 - z));
			boolean outsideToo = border < BankShelf.RAMP && !waterInside(x, z, border)
					&& BankShelf.waterMayBeNear(columns[i]);
			nearestDist = 0;
			rampTop = Integer.MAX_VALUE;
			fadeTerm = Integer.MAX_VALUE;
			for (int k = 1; k <= BankShelf.RAMP; k++) {
				int best = ChunkSurface.NO_WATER;
				int lowest = Integer.MAX_VALUE;
				for (int dx = -k; dx <= k; dx++) {
					int step = Math.abs(dx) == k ? 1 : 2 * k;
					for (int dz = -k; dz <= k; dz += step) {
						int nx = x + dx;
						int nz = z + dz;
						int water;
						if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
							water = freshWater(nx << 4 | nz);
						} else if (outsideToo) {
							water = farFreshWater(minX + nx, minZ + nz);
						} else {
							continue;
						}
						if (water != ChunkSurface.NO_WATER) {
							best = Math.max(best, water);
							lowest = Math.min(lowest, water);
						}
					}
				}
				if (best == ChunkSurface.NO_WATER) {
					continue;
				}
				if (nearestDist == 0) {
					nearestDist = k;
				}
				rampTop = Math.min(rampTop, lowest + k - 1);
				fadeTerm = Math.min(fadeTerm, k - best);
			}
			return nearestDist > 0;
		}

		/** Whether fresh model water lies within Chebyshev {@code r} of the in-chunk column (x, z) (all in the chunk). */
		private boolean waterInside(int x, int z, int r) {
			for (int nx = x - r; nx <= x + r; nx++) {
				for (int nz = z - r; nz <= z + r; nz++) {
					if (freshWater(nx << 4 | nz) != ChunkSurface.NO_WATER) {
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Highest fresh model water top within Chebyshev {@code r} of a column, also outside the chunk (sampled as in
		 * {@link #nearestWater}), or {@link ChunkSurface#NO_WATER}.
		 */
		int waterNear(int i, int r) {
			int x = i >> 4;
			int z = i & 15;
			boolean outsideToo = BankShelf.waterMayBeNear(columns[i]);
			int best = ChunkSurface.NO_WATER;
			for (int nx = x - r; nx <= x + r; nx++) {
				for (int nz = z - r; nz <= z + r; nz++) {
					if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
						best = Math.max(best, freshWater(nx << 4 | nz));
					} else if (outsideToo) {
						best = Math.max(best, farFreshWater(minX + nx, minZ + nz));
					}
				}
			}
			return best;
		}

		/** Model water top of a column outside the chunk when it is not sea water, else {@link ChunkSurface#NO_WATER}. */
		private int farFreshWater(int x, int z) {
			int summary = builder.cache.summary(x, z, builder, outside, minY, maxY);
			return summaryKind(summary) != WaterKind.SEA ? summaryWater(summary) : ChunkSurface.NO_WATER;
		}

		/** Summary of the neighbor of column {@code i} outside the chunk in direction {@code dir}. */
		private int outsideSummary(int i, int dir) {
			int x = minX + (i >> 4) + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
			int z = minZ + (i & 15) + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
			return builder.cache.summary(x, z, builder, outside, minY, maxY);
		}

		/**
		 * Guard of the shelf ({@link BankShelf}, review of S6, round 2) for the columns of the chunk marked in
		 * {@code candidates}: the largest {@code min(W, a) − max(0, m − GUARD_FULL)} over the fresh (not sea) model water
		 * within Manhattan distance {@code m ≤ GUARD_REACH} of the column, also outside the chunk (water top {@code W},
		 * model top {@code a} of the column), or {@link ChunkSurface#NO_WATER}. Within {@code GUARD_FULL} blocks of water
		 * the shelf thus never goes below it (water flowing from an open edge of the model spreads at most 7 blocks), and
		 * beyond it the guard fades by one block per block. Every term changes by at most one block between two neighbors
		 * on flat model ground, and the terms cut off at {@code GUARD_REACH} are at most {@code a − MAX_DROP}, so the guard
		 * never makes a step there, also where the nearest channel switches (confluences, braided channels, seams between
		 * streams), and it is the same whichever chunk computes it. A column outside the chunk is sampled only when it lies
		 * within reach of a candidate and the nearest column of the chunk puts water near it by its own fields
		 * ({@link BankShelf#waterMayBeWithin}).
		 */
		int[] guardField(boolean[] candidates) {
			final int reach = BankShelf.GUARD_REACH;
			final int full = BankShelf.GUARD_FULL;
			final int e = 16 + 2 * reach;
			// Columns outside the chunk within reach of a candidate near the border.
			boolean[] needed = new boolean[e * e];
			for (int i = 0; i < 256; i++) {
				if (!candidates[i]) {
					continue;
				}
				int x = i >> 4;
				int z = i & 15;
				if (Math.min(Math.min(x, 15 - x), Math.min(z, 15 - z)) >= reach) {
					continue;
				}
				for (int dx = -reach; dx <= reach; dx++) {
					int nx = x + dx;
					int rz = reach - Math.abs(dx);
					for (int dz = -rz; dz <= rz; dz++) {
						int nz = z + dz;
						if (nx < 0 || nx > 15 || nz < 0 || nz > 15) {
							needed[(nx + reach) * e + nz + reach] = true;
						}
					}
				}
			}
			int[] f = new int[e * e];
			for (int ex = 0; ex < e; ex++) {
				int x = ex - reach;
				for (int ez = 0; ez < e; ez++) {
					int z = ez - reach;
					int water = ChunkSurface.NO_WATER;
					if (x >= 0 && x < 16 && z >= 0 && z < 16) {
						water = freshWater(x << 4 | z);
					} else if (needed[ex * e + ez]) {
						int nx = Math.clamp(x, 0, 15);
						int nz = Math.clamp(z, 0, 15);
						if (BankShelf.waterMayBeWithin(columns[nx << 4 | nz], Math.hypot(x - nx, z - nz))) {
							water = farFreshWater(minX + x, minZ + z);
						}
					}
					f[ex * e + ez] = water;
				}
			}
			// Maximum within Manhattan distance k: k cross dilations, the valid region shrinking by one per dilation.
			int[][] m = new int[reach - full + 1][];
			int[] g = f;
			for (int k = 1; k <= reach; k++) {
				int[] next = new int[e * e];
				for (int ex = k; ex < e - k; ex++) {
					for (int ez = k; ez < e - k; ez++) {
						int c = ex * e + ez;
						next[c] = Math.max(Math.max(g[c], Math.max(g[c - e], g[c + e])), Math.max(g[c - 1], g[c + 1]));
					}
				}
				g = next;
				if (k >= full) {
					m[k - full] = g;
				}
			}
			int[] out = new int[256];
			for (int i = 0; i < 256; i++) {
				int c = ((i >> 4) + reach) * e + (i & 15) + reach;
				int best = ChunkSurface.NO_WATER;
				for (int k = 0; k < m.length; k++) {
					int water = m[k][c];
					if (water != ChunkSurface.NO_WATER) {
						best = Math.max(best, Math.min(water, modelTop[i]) - k);
					}
				}
				out[i] = best;
			}
			return out;
		}

		/** Whether an in-chunk neighbor is a water column of a lake, kettle pond or oxbow lake. */
		boolean mayTouchLake(int i) {
			for (int dir = 0; dir < 4; dir++) {
				int n = neighbor(i, dir);
				if (n >= 0 && wet(n) && columns[n].waterKind().isLake()) {
					return true;
				}
			}
			return false;
		}

		static int neighbor(int i, int dir) {
			int x = i >> 4;
			int z = i & 15;
			return switch (dir) {
				case 0 -> x > 0 ? i - 16 : -1;
				case 1 -> x < 15 ? i + 16 : -1;
				case 2 -> z > 0 ? i - 1 : -1;
				default -> z < 15 ? i + 1 : -1;
			};
		}
	}
}
