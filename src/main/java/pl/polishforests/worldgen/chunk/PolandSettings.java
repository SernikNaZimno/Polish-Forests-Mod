package pl.polishforests.worldgen.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import pl.polishforests.worldgen.habitat.HabitatClassifier;

/**
 * Settings of the "Poland" world, stored in the dimension data.
 *
 * @param scale              world scale: realistic or gameplay-friendly
 * @param regionScale        size multiplier for macroregions (1.0 = default size for the scale, decision A8)
 * @param agriculture        "present-day Poland" mode with fields; false = natural vegetation (decision A5; the default
 *                           until M8 is natural vegetation, decision M2-B)
 * @param managedForestShare share of managed forests, 0.85 by default (decision D9)
 * @param alienSpecies       alien and invasive species in realistic proportions (decisions C4 and D10)
 * @param version            version of the world generation: 1 = M1 (missing in the settings of worlds from M1), 2 = M2
 *                           with the mod's biomes (docs/03-m2-biomy.md §11; worlds are not migrated, decision M2-7)
 */
public record PolandSettings(PolandScale scale, double regionScale, boolean agriculture, double managedForestShare,
		boolean alienSpecies, int version) {
	/** Version of the presets and of new worlds: M2 (habitat biomes). */
	public static final int CURRENT_VERSION = 2;

	public static final PolandSettings DEFAULT = new PolandSettings(PolandScale.REALISTIC, 1.0, false, 0.85, true,
			CURRENT_VERSION);

	public static final Codec<PolandSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			PolandScale.CODEC.optionalFieldOf("scale", PolandScale.REALISTIC).forGetter(PolandSettings::scale),
			Codec.doubleRange(0.05, 4.0).optionalFieldOf("region_scale", 1.0).forGetter(PolandSettings::regionScale),
			Codec.BOOL.optionalFieldOf("agriculture", false).forGetter(PolandSettings::agriculture),
			Codec.doubleRange(0.0, 1.0).optionalFieldOf("managed_forest_share", 0.85)
					.forGetter(PolandSettings::managedForestShare),
			Codec.BOOL.optionalFieldOf("alien_species", true).forGetter(PolandSettings::alienSpecies),
			Codec.intRange(1, 1_000).optionalFieldOf("version", 1).forGetter(PolandSettings::version)
	).apply(i, PolandSettings::new));

	public PolandSettings withScale(PolandScale newScale) {
		return new PolandSettings(newScale, regionScale, agriculture, managedForestShare, alienSpecies, version);
	}

	/** Vegetation mode of the habitat classifier: "present-day Poland" or natural vegetation. */
	public HabitatClassifier.Mode mode() {
		return agriculture ? HabitatClassifier.Mode.PRESENT_DAY : HabitatClassifier.Mode.NATURAL;
	}
}
