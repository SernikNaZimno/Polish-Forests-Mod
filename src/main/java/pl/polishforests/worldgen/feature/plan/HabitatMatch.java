package pl.polishforests.worldgen.feature.plan;

import java.util.Collection;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * Condition of a palette rule on the habitat code of a column (docs/03-m2-biomy.md §8.2): sets of biomes, zones, forest
 * site types, associations and soils as bit masks of the enum ordinals. An empty set matches everything, so a rule with
 * only biomes applies to every zone of those biomes. A pure value, testable without the game.
 *
 * @param biomes       mask of {@link HabitatBiome} ordinals (0: any)
 * @param zones        mask of {@link Zone} ordinals (0: any)
 * @param siteTypes    mask of {@link ForestSiteType} ordinals (0: any)
 * @param associations mask of {@link Association} ordinals (0: any)
 * @param soils        mask of {@link Soil} ordinals (0: any)
 */
public record HabitatMatch(long biomes, int zones, int siteTypes, int associations, int soils) {
	/** Matches every column. */
	public static final HabitatMatch ANY = new HabitatMatch(0, 0, 0, 0, 0);

	static {
		// The masks hold one bit per enum constant (enums are only appended to, so this guards the limits).
		if (HabitatBiome.values().length > 64 || Zone.values().length > 32 || ForestSiteType.values().length > 32
				|| Association.values().length > 32 || Soil.values().length > 32) {
			throw new IllegalStateException("an enum no longer fits in a habitat match mask");
		}
	}

	public static HabitatMatch of(Collection<HabitatBiome> biomes, Collection<Zone> zones,
			Collection<ForestSiteType> siteTypes, Collection<Association> associations, Collection<Soil> soils) {
		long b = 0;
		for (HabitatBiome x : biomes) {
			b |= 1L << x.ordinal();
		}
		return new HabitatMatch(b, mask(zones), mask(siteTypes), mask(associations), mask(soils));
	}

	private static int mask(Collection<? extends Enum<?>> values) {
		int m = 0;
		for (Enum<?> x : values) {
			m |= 1 << x.ordinal();
		}
		return m;
	}

	/** Whether the habitat code satisfies every non-empty set. */
	public boolean matches(int code) {
		return (biomes == 0 || (biomes >>> Habitat.biome(code).ordinal() & 1) != 0)
				&& (zones == 0 || (zones >>> Habitat.zone(code).ordinal() & 1) != 0)
				&& (siteTypes == 0 || (siteTypes >>> Habitat.siteType(code).ordinal() & 1) != 0)
				&& (associations == 0 || (associations >>> Habitat.association(code).ordinal() & 1) != 0)
				&& (soils == 0 || (soils >>> Habitat.soil(code).ordinal() & 1) != 0);
	}
}
