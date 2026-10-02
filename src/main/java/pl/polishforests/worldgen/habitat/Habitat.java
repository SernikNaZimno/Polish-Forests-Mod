package pl.polishforests.worldgen.habitat;

/**
 * Habitat of a column, packed into a single {@code int} (§3.4 of the M2 plan):
 * biome 6 b | zone 5 b | site type 5 b | association 5 b | cover 2 b | BEECH/FIR/SPRUCE/HORNBEAM flags 4 b | soil 5 b,
 * from the least significant bits. The record serves commands, tests and the preview; generation keeps only the code.
 *
 * @param flags mask of {@link Species#flag()} for species within their range
 */
public record Habitat(HabitatBiome biome, Zone zone, ForestSiteType siteType, Association association, LandCover cover, int flags, Soil soil) {
	private static final int B_BIOME = 0;
	private static final int B_ZONE = 6;
	private static final int B_SITE_TYPE = 11;
	private static final int B_ASSOCIATION = 16;
	private static final int B_COVER = 21;
	private static final int B_FLAGS = 23;
	private static final int B_SOIL = 27;

	static {
		// The fields must fit in their bits (enums are only appended to, so this guards the limits).
		check(HabitatBiome.values().length, 6);
		check(Zone.values().length, 5);
		check(ForestSiteType.values().length, 5);
		check(Association.values().length, 5);
		check(LandCover.values().length, 2);
		check(Soil.values().length, 5);
	}

	private static void check(int n, int bits) {
		if (n > 1 << bits) {
			throw new IllegalStateException("enum does not fit in " + bits + " bits: " + n);
		}
	}

	/** Habitat code. */
	public static int pack(HabitatBiome biome, Zone zone, ForestSiteType siteType, Association association, LandCover cover, int flags, Soil soil) {
		return biome.ordinal() << B_BIOME | zone.ordinal() << B_ZONE | siteType.ordinal() << B_SITE_TYPE
				| association.ordinal() << B_ASSOCIATION | cover.ordinal() << B_COVER | (flags & 15) << B_FLAGS
				| soil.ordinal() << B_SOIL;
	}

	public int code() {
		return pack(biome, zone, siteType, association, cover, flags, soil);
	}

	public static Habitat of(int code) {
		return new Habitat(biome(code), zone(code), siteType(code), association(code), cover(code), flags(code), soil(code));
	}

	public static HabitatBiome biome(int code) {
		return HabitatBiome.of(code >>> B_BIOME & 63);
	}

	public static Zone zone(int code) {
		return Zone.of(code >>> B_ZONE & 31);
	}

	public static ForestSiteType siteType(int code) {
		return ForestSiteType.of(code >>> B_SITE_TYPE & 31);
	}

	public static Association association(int code) {
		return Association.of(code >>> B_ASSOCIATION & 31);
	}

	public static LandCover cover(int code) {
		return LandCover.of(code >>> B_COVER & 3);
	}

	public static int flags(int code) {
		return code >>> B_FLAGS & 15;
	}

	public static Soil soil(int code) {
		return Soil.of(code >>> B_SOIL & 31);
	}

	/** Whether a flagged species is within its range (beech, fir, spruce and hornbeam only). */
	public static boolean has(int code, Species g) {
		return (flags(code) & g.flag()) != 0;
	}
}
