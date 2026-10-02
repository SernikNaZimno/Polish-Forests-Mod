package pl.polskielasy.worldgen.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Ustawienia świata "Polska" zapisywane w danych wymiaru.
 *
 * @param scale              skala świata: realistyczna albo przyjazna rozgrywce
 * @param regionScale        mnożnik rozmiaru makroregionów (1,0 = rozmiar domyślny dla skali, decyzja A8)
 * @param agriculture        tryb "dzisiejsza Polska" z polami; false = roślinność naturalna (decyzja A5)
 * @param managedForestShare udział lasów gospodarczych, domyślnie 0,85 (decyzja D9)
 * @param alienSpecies       gatunki obce i inwazyjne w realistycznych proporcjach (decyzje C4 i D10)
 */
public record PolskaSettings(PolskaScale scale, double regionScale, boolean agriculture, double managedForestShare,
		boolean alienSpecies) {
	public static final PolskaSettings DEFAULT = new PolskaSettings(PolskaScale.REALISTYCZNA, 1.0, true, 0.85, true);

	public static final Codec<PolskaSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			PolskaScale.CODEC.optionalFieldOf("scale", PolskaScale.REALISTYCZNA).forGetter(PolskaSettings::scale),
			Codec.doubleRange(0.05, 4.0).optionalFieldOf("region_scale", 1.0).forGetter(PolskaSettings::regionScale),
			Codec.BOOL.optionalFieldOf("agriculture", true).forGetter(PolskaSettings::agriculture),
			Codec.doubleRange(0.0, 1.0).optionalFieldOf("managed_forest_share", 0.85)
					.forGetter(PolskaSettings::managedForestShare),
			Codec.BOOL.optionalFieldOf("alien_species", true).forGetter(PolskaSettings::alienSpecies)
	).apply(i, PolskaSettings::new));

	public PolskaSettings withScale(PolskaScale newScale) {
		return new PolskaSettings(newScale, regionScale, agriculture, managedForestShare, alienSpecies);
	}
}
