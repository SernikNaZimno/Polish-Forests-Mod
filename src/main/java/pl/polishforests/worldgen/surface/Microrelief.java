package pl.polishforests.worldgen.surface;

import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Micro-relief of wet habitats (docs/03-m2-biomy.md §7.3): puddles (the top lowered by one block, a water source in
 * its place) and hummocks (one block higher) from a deterministic noise with a wavelength of {@link #WAVELENGTH} blocks,
 * low quantiles giving puddles and high ones hummocks.
 *
 * <table>
 * <caption>Shares of the columns</caption>
 * <tr><th>Biome / zone</th><th>Puddles</th><th>Hummocks</th></tr>
 * <tr><td>alder_carr</td><td>20%</td><td>25% (rooted dirt)</td></tr>
 * <tr><td>raised_bog</td><td>15%</td><td>30% (moss block: sphagnum)</td></tr>
 * <tr><td>fen</td><td>25%</td><td>–</td></tr>
 * <tr><td>bog_woodland</td><td>8%</td><td>20% (moss block)</td></tr>
 * <tr><td>land reedbed, zone WILLOW_CARR</td><td>35%</td><td>–</td></tr>
 * <tr><td>willow_poplar_forest</td><td>5%</td><td>–</td></tr>
 * </table>
 *
 * <p>A puddle holds water only when the four neighboring columns lie in the chunk, are dry and have their top at least as
 * high as the puddle's water; elsewhere (the chunk edge, a lower or wet neighbor) the place gets mud instead. Any two
 * puddles side by side then either share the water level or the higher one fails, so no water can flow. The model's
 * {@code hasWater} does not change; the chunk habitats get the puddle water, so no trees stand in puddles.
 */
final class Microrelief {
	private Microrelief() {
	}

	/** Wavelength of the micro-relief noise in blocks (3–6 m in §7.3, the same in both scales). */
	static final double WAVELENGTH = 4.5;

	static double puddleShare(HabitatBiome biome, Zone zone) {
		if (zone == Zone.WILLOW_CARR) {
			return 0.35;
		}
		return switch (biome) {
			case ALDER_CARR -> 0.20;
			case RAISED_BOG -> 0.15;
			case FEN -> 0.25;
			case BOG_WOODLAND -> 0.08;
			case REEDBED -> 0.35;
			case WILLOW_POPLAR_FOREST -> 0.05;
			default -> 0;
		};
	}

	static double hummockShare(HabitatBiome biome, Zone zone) {
		if (zone == Zone.WILLOW_CARR) {
			return 0;
		}
		return switch (biome) {
			case ALDER_CARR -> 0.25;
			case RAISED_BOG -> 0.30;
			case BOG_WOODLAND -> 0.20;
			default -> 0;
		};
	}

	static Material hummock(HabitatBiome biome) {
		return biome == HabitatBiome.ALDER_CARR ? Material.ROOTED_DIRT : Material.MOSS_BLOCK;
	}

	/** Quantile 0–1 (uniform) of the micro-relief noise at a column. */
	static double quantile(Noise noise, int x, int z) {
		return LandscapeModel.noiseQuantile(noise.at(x, z, WAVELENGTH));
	}

	/**
	 * Marks puddles, puddle mud and hummocks (flags), after the shelf; lowers the puddles and raises the hummocks. The
	 * profile of a hummock is set later by the builder ({@link SurfaceBuilder}).
	 *
	 * @param maxY highest Y of the chunk (a hummock does not go above it)
	 */
	static void apply(SurfaceBuilder.Work w, ChunkSurface out, Noise noise, int minX, int minZ, int maxY) {
		boolean any = false;
		double[] q = w.microQ;
		for (int i = 0; i < 256; i++) {
			q[i] = -1;
			if (w.wet(i) || (out.flags[i] & (ChunkSurface.SHELF | ChunkSurface.SHORE)) != 0) {
				continue;
			}
			int code = w.codes[i];
			HabitatBiome biome = Habitat.biome(code);
			Zone zone = Habitat.zone(code);
			double puddle = puddleShare(biome, zone);
			double hummock = hummockShare(biome, zone);
			if (puddle == 0 && hummock == 0) {
				continue;
			}
			double v = quantile(noise, minX + (i >> 4), minZ + (i & 15));
			if (v < puddle) {
				q[i] = v;
				any = true;
			} else if (v > 1 - hummock && out.top[i] < maxY) {
				out.flags[i] |= ChunkSurface.HUMMOCK;
			}
		}
		if (any) {
			// Validity from the tops before any puddle is lowered.
			for (int i = 0; i < 256; i++) {
				if (q[i] < 0) {
					continue;
				}
				out.flags[i] |= canHold(w, out, i) ? ChunkSurface.PUDDLE : ChunkSurface.PUDDLE_MUD;
			}
			for (int i = 0; i < 256; i++) {
				if ((out.flags[i] & ChunkSurface.PUDDLE) != 0) {
					out.waterTop[i] = out.top[i];
					out.top[i]--;
				}
			}
		}
		for (int i = 0; i < 256; i++) {
			if ((out.flags[i] & ChunkSurface.HUMMOCK) != 0) {
				out.top[i]++;
			}
		}
	}

	private static boolean canHold(SurfaceBuilder.Work w, ChunkSurface out, int i) {
		int x = i >> 4;
		int z = i & 15;
		if (x == 0 || x == 15 || z == 0 || z == 15) {
			return false;
		}
		int t = out.top[i];
		return holds(w, out, i - 16, t) && holds(w, out, i + 16, t) && holds(w, out, i - 1, t) && holds(w, out, i + 1, t);
	}

	/** The neighbor {@code n} is a wall for water at height {@code t}: dry, with its top at {@code t} or higher. */
	private static boolean holds(SurfaceBuilder.Work w, ChunkSurface out, int n, int t) {
		return !w.wet(n) && out.top[n] >= t;
	}
}
