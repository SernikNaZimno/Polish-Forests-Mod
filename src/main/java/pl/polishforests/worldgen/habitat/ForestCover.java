package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Maska lasu trybu D „dzisiejsza Polska” (raport ekologii §6). Las, gdy kwantyl gładkiego szumu F
 * (fala 4 km·k z drobnym szumem krawędzi 300 m·k) jest mniejszy niż P_las siedliska, więc przy stałym
 * P_las udział lasu wynosi P_las. Poza lasem biom nieleśny według siedliska (§2.2); strefy przywodne
 * zostają. Wartości P_las są startowe; kalibracja i test udziałów trybu D należą do kroku S8. W trybie N
 * (domyślnym do M8) klasyfikator maski nie używa.
 */
final class ForestCover {
	private static final double WAVELENGTH = 4_000;
	private static final double EDGE_WAVELENGTH = 300;
	private static final double EDGE = 0.08;

	private final Noise noise;
	private final double k;

	ForestCover(Noise noise, double k) {
		this.noise = noise;
		this.k = k;
	}

	/** Kwantyl F w [0, 1]. */
	private double f(double x, double z) {
		double v = noise.at(x, z, WAVELENGTH * k) + EDGE * noise.at(x + 5_151, z - 919, EDGE_WAVELENGTH * k);
		return LandscapeModel.noiseQuantile(Math.clamp(v / (1 + EDGE), -1.0, 1.0));
	}

	/** Czy kolumna biomu leśnego zostaje lasem w trybie D. */
	boolean isForested(HabitatClassifier.Column c, HabitatBiome biome, ForestSiteType siteType) {
		return f(c.x, c.z) < forestProbability(c, biome, siteType);
	}

	/** P_las (raport ekologii §6.3, wartości startowe). */
	static double forestProbability(HabitatClassifier.Column c, HabitatBiome biome, ForestSiteType siteType) {
		switch (biome) {
			case DWARF_PINE_SCRUB:
				return 1;
			case COASTAL_PINE_FOREST:
				return 0.90;
			case WILLOW_POPLAR_FOREST:
				return 0.45;
			case ELM_ASH_FOREST:
				return 0.10;
			case ASH_ALDER_FOREST:
				return 0.50;
			case ALDER_CARR:
				return 0.35;
			case MONTANE_SPRUCE_FOREST:
				return 0.85;
			case MONTANE_BEECH_FOREST:
				return c.onValleyFloor() ? 0.20 : 0.80;
			case GRAY_ALDER_FOREST:
				return 0.45;
			default:
				break;
		}
		if (c.wFoothills > 0.5) {
			return c.slope > 10 ? 0.60 : 0.12;
		}
		return switch (siteType) {
			case DRY_CONIFEROUS -> 0.90;
			case FRESH_CONIFEROUS -> 0.75;
			case MOIST_CONIFEROUS, MOIST_MIXED_CONIFEROUS -> 0.65;
			case BOGGY_CONIFEROUS, BOGGY_MIXED_CONIFEROUS -> 0.85;
			case FRESH_MIXED_CONIFEROUS -> 0.45;
			case FRESH_MIXED_BROADLEAVED -> 0.22;
			case MOIST_MIXED_BROADLEAVED, BOGGY_MIXED_BROADLEAVED -> 0.25;
			case FRESH_BROADLEAVED -> c.t.has(Landform.END_MORAINE) ? 0.50 : c.slope > 15 ? 0.75 : c.slope >= 5 ? 0.35 : 0.08;
			case MOIST_BROADLEAVED -> 0.12;
			default -> 0.5;
		};
	}

	/** Biom nieleśny dla niezalesionego siedliska (§2.2, kolumna D). */
	HabitatBiome nonForest(HabitatClassifier.Column c, HabitatBiome biome, ForestSiteType siteType) {
		double q = c.variant(3);
		switch (biome) {
			case WILLOW_POPLAR_FOREST, ASH_ALDER_FOREST, GRAY_ALDER_FOREST:
				return HabitatBiome.WET_MEADOW;
			case ELM_ASH_FOREST:
				return q < Calibration.D_RIPARIAN_MEADOW ? HabitatBiome.WET_MEADOW : HabitatBiome.ARABLE_LAND;
			case ALDER_CARR:
				return q < Calibration.D_ALDER_CARR_MEADOW ? HabitatBiome.WET_MEADOW : HabitatBiome.FEN;
			case COASTAL_PINE_FOREST:
				return HabitatBiome.GRAY_DUNE;
			case MONTANE_BEECH_FOREST, MONTANE_SPRUCE_FOREST, UPLAND_FIR_FOREST:
				return HabitatBiome.HAY_MEADOW;
			default:
				break;
		}
		return switch (siteType) {
			case DRY_CONIFEROUS -> HabitatBiome.HEATH;
			case FRESH_CONIFEROUS -> q < Calibration.D_PINE_ARABLE ? HabitatBiome.ARABLE_LAND : HabitatBiome.HEATH;
			case MOIST_CONIFEROUS, MOIST_MIXED_CONIFEROUS -> q < Calibration.D_PINE_ARABLE ? HabitatBiome.WET_MEADOW : HabitatBiome.ARABLE_LAND;
			case BOGGY_CONIFEROUS, BOGGY_MIXED_CONIFEROUS -> HabitatBiome.RAISED_BOG;
			case MOIST_BROADLEAVED, MOIST_MIXED_BROADLEAVED, BOGGY_MIXED_BROADLEAVED, ALDER_SWAMP -> HabitatBiome.WET_MEADOW;
			default -> c.slope < Calibration.D_ARABLE_SLOPE ? (q < Calibration.D_ARABLE ? HabitatBiome.ARABLE_LAND : HabitatBiome.HAY_MEADOW)
					: HabitatBiome.HAY_MEADOW;
		};
	}
}
