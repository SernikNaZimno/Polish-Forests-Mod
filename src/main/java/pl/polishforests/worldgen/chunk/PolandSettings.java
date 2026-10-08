package pl.polishforests.worldgen.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import pl.polishforests.worldgen.habitat.HabitatClassifier;

/**
 * Settings of the "Poland" world, stored in the dimension data.
 *
 * <p>The codec always writes every field, so a saved world keeps its settings even when a default changes later (for
 * example the default vegetation mode in M8). Missing fields are read with the meaning they had when they could be
 * missing: a world without {@code version} is an M1 world, and in M1 a missing {@code agriculture} meant the
 * "present-day Poland" mode; from version 2 on a missing {@code agriculture} means natural vegetation (the default of
 * M2, decision M2-B). The generator reads a missing {@code settings} object as {@link #LEGACY_M1}.
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
	/** Version of worlds created before the field existed (M1). */
	public static final int LEGACY_VERSION = 1;

	public static final PolandSettings DEFAULT = new PolandSettings(PolandScale.REALISTIC, 1.0, false, 0.85, true,
			CURRENT_VERSION);

	/**
	 * Settings of an M1 world saved without a {@code settings} object: in M1 the codec left out settings equal to the M1
	 * default (realistic scale, "present-day Poland" mode).
	 */
	public static final PolandSettings LEGACY_M1 = new PolandSettings(PolandScale.REALISTIC, 1.0, true, 0.85, true,
			LEGACY_VERSION);

	public static final Codec<PolandSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			PolandScale.CODEC.optionalFieldOf("scale").forGetter(s -> Optional.of(s.scale())),
			Codec.doubleRange(0.05, 4.0).optionalFieldOf("region_scale").forGetter(s -> Optional.of(s.regionScale())),
			Codec.BOOL.optionalFieldOf("agriculture").forGetter(s -> Optional.of(s.agriculture())),
			Codec.doubleRange(0.0, 1.0).optionalFieldOf("managed_forest_share")
					.forGetter(s -> Optional.of(s.managedForestShare())),
			Codec.BOOL.optionalFieldOf("alien_species").forGetter(s -> Optional.of(s.alienSpecies())),
			Codec.intRange(1, 1_000).optionalFieldOf("version").forGetter(s -> Optional.of(s.version()))
	).apply(i, PolandSettings::decode));

	/**
	 * Field {@code settings} of the generator: always written (an optional field with a default would leave out
	 * settings equal to the default, so a later change of the default would silently change saved worlds); missing in
	 * a world from M1 whose settings were the M1 default, read as {@link #LEGACY_M1}.
	 */
	public static final MapCodec<PolandSettings> GENERATOR_FIELD = CODEC.optionalFieldOf("settings")
			.xmap(o -> o.orElse(LEGACY_M1), Optional::of);

	private static PolandSettings decode(Optional<PolandScale> scale, Optional<Double> regionScale,
			Optional<Boolean> agriculture, Optional<Double> managedForestShare, Optional<Boolean> alienSpecies,
			Optional<Integer> version) {
		int v = version.orElse(LEGACY_VERSION);
		return new PolandSettings(scale.orElse(PolandScale.REALISTIC), regionScale.orElse(1.0),
				agriculture.orElse(v < CURRENT_VERSION), managedForestShare.orElse(0.85), alienSpecies.orElse(true), v);
	}

	public PolandSettings withScale(PolandScale newScale) {
		return new PolandSettings(newScale, regionScale, agriculture, managedForestShare, alienSpecies, version);
	}

	/** Vegetation mode of the habitat classifier: "present-day Poland" or natural vegetation. */
	public HabitatClassifier.Mode mode() {
		return agriculture ? HabitatClassifier.Mode.PRESENT_DAY : HabitatClassifier.Mode.NATURAL;
	}
}
