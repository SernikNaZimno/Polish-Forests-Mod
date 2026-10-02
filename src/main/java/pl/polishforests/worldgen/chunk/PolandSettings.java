package pl.polishforests.worldgen.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Settings of the "Poland" world, stored in the dimension data.
 *
 * @param scale              world scale: realistic or gameplay-friendly
 * @param regionScale        size multiplier for macroregions (1.0 = default size for the scale, decision A8)
 * @param agriculture        "present-day Poland" mode with fields; false = natural vegetation (decision A5)
 * @param managedForestShare share of managed forests, 0.85 by default (decision D9)
 * @param alienSpecies       alien and invasive species in realistic proportions (decisions C4 and D10)
 */
public record PolandSettings(PolandScale scale, double regionScale, boolean agriculture, double managedForestShare,
		boolean alienSpecies) {
	public static final PolandSettings DEFAULT = new PolandSettings(PolandScale.REALISTIC, 1.0, true, 0.85, true);

	public static final Codec<PolandSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			PolandScale.CODEC.optionalFieldOf("scale", PolandScale.REALISTIC).forGetter(PolandSettings::scale),
			Codec.doubleRange(0.05, 4.0).optionalFieldOf("region_scale", 1.0).forGetter(PolandSettings::regionScale),
			Codec.BOOL.optionalFieldOf("agriculture", true).forGetter(PolandSettings::agriculture),
			Codec.doubleRange(0.0, 1.0).optionalFieldOf("managed_forest_share", 0.85)
					.forGetter(PolandSettings::managedForestShare),
			Codec.BOOL.optionalFieldOf("alien_species", true).forGetter(PolandSettings::alienSpecies)
	).apply(i, PolandSettings::new));

	public PolandSettings withScale(PolandScale newScale) {
		return new PolandSettings(newScale, regionScale, agriculture, managedForestShare, alienSpecies);
	}
}
