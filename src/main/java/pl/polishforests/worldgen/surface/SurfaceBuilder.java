package pl.polishforests.worldgen.surface;

import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
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
 * takes itself are the neighbors outside the chunk of the shelf columns at the chunk edge.
 *
 * <p><b>Cover in blocks (§7.1).</b> The cover of loose deposits is {@code coverDepth} meters thick. Before S6 the generator
 * compared it with the depth in blocks, so in the gameplay scale (about 0.4 block per meter in the lowland) the cover was
 * 2.5–4 times too thick. With {@code coverInBlocks} the rock starts at {@code vertical.topBlockY(surface − coverDepth)}
 * (world setting {@code cover_in_blocks}; missing in older worlds, which keep the old cover).
 *
 * <p><b>Diagnostic mode.</b> With {@code -D}{@value #DEBUG_PROPERTY}{@code =true} the top block of every column is concrete
 * in the color of its zone, or terracotta in the color of its biome when it has no zone, so narrow belts can be checked
 * in the game at a glance.
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

	/** Number of neighbor columns outside the chunk sampled for the shelf (diagnostics). */
	public static final java.util.concurrent.atomic.LongAdder OUTSIDE_SAMPLES = new java.util.concurrent.atomic.LongAdder();

	/** Samples a column of the landscape model (for neighbors outside the chunk). */
	@FunctionalInterface
	public interface Sampler {
		ColumnSample sample(int x, int z);
	}

	private final VerticalScale vertical;
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
			if (!out.wet(i)) {
				// A dry column always has its soil top, also where the model has no loose cover (thin regolith on the
				// ridges of the Beskids): before S6 such a column had bare stone, so trees could not grow there.
				out.rockTop[i] = Math.min(out.rockTop[i], out.top[i] - MIN_SOIL);
			}
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
				profile = SoilBlocks.dry(Habitat.soil(code), Habitat.zone(code), Habitat.biome(code), q1, q2,
						s.terrain().lowShore());
				if ((flags & ChunkSurface.HUMMOCK) != 0) {
					// The hummock on top, the former top block under it.
					profile = SoilBlocks.profile(Microrelief.hummock(Habitat.biome(code)), SoilBlocks.top(profile), 1,
							SoilBlocks.layer1(profile), SoilBlocks.layer1Depth(profile));
				}
			}
			if (debug) {
				Zone zone = Habitat.zone(code);
				Material paint = zone != Zone.NONE ? Material.concrete(zone.ordinal() - 1)
						: Material.terracotta(Habitat.biome(code).ordinal());
				profile = SoilBlocks.profile(paint, SoilBlocks.layer1(profile), SoilBlocks.layer1Depth(profile),
						SoilBlocks.layer2(profile), SoilBlocks.layer2Depth(profile));
				out.rockTop[i] = Math.min(out.rockTop[i], out.top[i] - 1);
			}
			out.profile[i] = profile;
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
		/** Water top and kind of the neighbors outside the chunk: side (0: x − 1, 1: x + 16, 2: z − 1, 3: z + 16) × 16. */
		private final int[] outsideWater = new int[64];
		private final byte[] outsideKind = new byte[64];
		private final boolean[] outsideDone = new boolean[64];

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
			int slot = outsideSlot(i, dir);
			sampleOutside(i, dir, slot);
			return outsideWater[slot];
		}

		WaterKind neighborKind(int i, int dir) {
			int n = neighbor(i, dir);
			if (n >= 0) {
				return columns[n].waterKind();
			}
			int slot = outsideSlot(i, dir);
			sampleOutside(i, dir, slot);
			return KINDS[outsideKind[slot]];
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

		/**
		 * Y of the top water block of the water the column belongs to by the model: the standing water (lake shores and
		 * the shore reedbed of a lake or oxbow lake) or the nearest channel, at the rounded level as in the model.
		 */
		int levelBlock(int i, boolean lake) {
			ColumnSample s = columns[i];
			ColumnSample.Waters w = s.waters();
			boolean standing = (lake || Habitat.zone(codes[i]) == Zone.SHORE_REEDBED)
					&& w.standingWaterKind() != ColumnSample.StandingWaterKind.NONE
					&& w.standingWaterKind() != ColumnSample.StandingWaterKind.KETTLE_BOG
					&& w.shoreLevel() != ColumnSample.NO_WATER;
			if (standing) {
				return Math.min(builder.vertical.topBlockY(w.shoreLevel()), maxY);
			}
			double level = w.channelLevel();
			if (Double.isNaN(level)) {
				return ChunkSurface.NO_WATER;
			}
			return Math.min(builder.vertical.topBlockY(Math.floor(level)), maxY);
		}

		private static int neighbor(int i, int dir) {
			int x = i >> 4;
			int z = i & 15;
			return switch (dir) {
				case 0 -> x > 0 ? i - 16 : -1;
				case 1 -> x < 15 ? i + 16 : -1;
				case 2 -> z > 0 ? i - 1 : -1;
				default -> z < 15 ? i + 1 : -1;
			};
		}

		private static int outsideSlot(int i, int dir) {
			return dir << 4 | (dir < 2 ? i & 15 : i >> 4);
		}

		private void sampleOutside(int i, int dir, int slot) {
			if (outsideDone[slot]) {
				return;
			}
			int x = minX + (i >> 4) + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
			int z = minZ + (i & 15) + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
			ColumnSample s = outside.sample(x, z);
			OUTSIDE_SAMPLES.increment();
			int top = builder.topY(s, minY, maxY);
			int water = builder.waterTopY(s, maxY);
			outsideWater[slot] = water > top ? water : ChunkSurface.NO_WATER;
			outsideKind[slot] = (byte) s.waterKind().ordinal();
			outsideDone[slot] = true;
		}
	}
}
