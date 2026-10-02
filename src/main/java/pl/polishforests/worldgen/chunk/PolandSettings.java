package pl.polishforests.worldgen.chunk;

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
