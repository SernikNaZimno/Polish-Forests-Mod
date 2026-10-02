package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Krok 1 klasyfikatora: kolumny z wodą (§2.3, §4.4). Morze, zalew, rzeka lub potok, jezioro albo jezioro
 * dystroficzne; w płytkiej wodzie eutroficznej szuwar (biom w pasie ≥ 12 m, węższy jako strefa SZUWAR),
 * w jeziorach strefy ELODEIDY i NYMFEIDY, w jeziorze dystroficznym pło.
 */
final class OpenWater {
	private OpenWater() {
	}

	/** Troficzność wody stojącej. */
	enum TrophicState {
		EUTROPHIC, OLIGOTROPHIC, DYSTROPHIC
	}

	static int classify(HabitatClassifier.Column c) {
		ColumnSample s = c.s;
		if (!s.hasWater()) {
			return HabitatClassifier.Result.NONE;
		}
		double z = s.waterLevel() - s.surface();
		return switch (s.waterKind()) {
			case SEA -> {
				if (c.t.coastD() < 0) {
					yield HabitatClassifier.Result.of(HabitatBiome.SEA, Zone.NONE, Association.TYPICAL);
				}
				if (z > Calibration.LAGOON_Z) {
					yield HabitatClassifier.Result.of(HabitatBiome.LAGOON, z <= Calibration.LAKE_DEPTH ? Zone.SUBMERGED_PLANTS : Zone.NONE,
							Association.TYPICAL);
				}
				yield HabitatClassifier.Result.of(HabitatBiome.REEDBED, Zone.REEDBED, Association.TYPICAL);
			}
			case RIVER -> HabitatClassifier.Result.of(WatersideZones.streamClass(c) == WatersideZones.StreamClass.C ? HabitatBiome.STREAM : HabitatBiome.RIVER,
					Zone.CHANNEL, Association.TYPICAL);
			case LAKE, KETTLE, OXBOW -> lake(c, z);
			case NONE -> HabitatClassifier.Result.NONE;
		};
	}

	private static int lake(HabitatClassifier.Column c, double z) {
		boolean oxbow = c.s.waterKind() == WaterKind.OXBOW;
		TrophicState trophic = trophicState(c);
		HabitatBiome biome = trophic == TrophicState.DYSTROPHIC ? HabitatBiome.DYSTROPHIC_LAKE : HabitatBiome.LAKE;
		Association association = trophic == TrophicState.OLIGOTROPHIC ? Association.LOBELIA_LAKE : Association.TYPICAL;
		// Odległość od brzegu w głąb wody (m); s jest ujemne w wodzie.
		double waterDepth = Math.max(0, -c.w.s());
		if (!Double.isFinite(waterDepth)) {
			waterDepth = 0;
		}
		if (trophic == TrophicState.DYSTROPHIC) {
			if (waterDepth <= Math.max(1, Calibration.LAKE_FLOATING_MAT_K * c.k * c.jitter())) {
				return HabitatClassifier.Result.of(biome, Zone.FLOATING_MAT, association);
			}
			return HabitatClassifier.Result.of(biome, z <= Calibration.LAKE_DEPTH ? Zone.SUBMERGED_PLANTS : Zone.NONE, association);
		}
		// Starorzecze: szuwar tylko w pasie przy brzegu (2–15 m), dalej nymfeidy na 60–90% lustra.
		boolean nearShore = !oxbow
				|| waterDepth <= Math.max(Calibration.MIN_REEDBED, Calibration.LAKE_OXBOW_REEDBED_K * c.k) * c.jitter();
		if (z <= Calibration.LAKE_REEDBED_Z && nearShore) {
			if (trophic == TrophicState.EUTROPHIC) {
				// Biom w zbiornikach z miejscem na pas ≥ 12 m (całą płyciznę, bez odwróconych pierścieni przy brzegu);
				// w starorzeczach pas ma 15 m·k, w małych oczkach tylko strefa.
				double radius = c.w.standingWaterRadius();
				double band = oxbow ? Calibration.LAKE_OXBOW_REEDBED_K * c.k
						: Double.isNaN(radius) || radius >= Calibration.LAKE_REEDBED_BIOME_RADIUS_K * c.k ? Calibration.BIOME_BAND : 0;
				return band >= Calibration.BIOME_BAND ? HabitatClassifier.Result.of(HabitatBiome.REEDBED, Zone.REEDBED, association)
						: HabitatClassifier.Result.of(biome, Zone.REEDBED, association);
			}
			// Jezioro lobeliowe: szuwar tylko w płatach.
			if (c.patchQ(1) > Calibration.LAKE_LOBELIA_REEDBED) {
				return HabitatClassifier.Result.of(biome, Zone.REEDBED, association);
			}
		}
		double depth = oxbow ? Calibration.LAKE_DEPTH_OXBOW : Calibration.LAKE_DEPTH;
		if (z > depth) {
			return HabitatClassifier.Result.of(biome, Zone.NONE, association);
		}
		if (z >= Calibration.LAKE_FLOATING_LEAVED_FROM && z <= Calibration.LAKE_FLOATING_LEAVED_TO && trophic == TrophicState.EUTROPHIC) {
			double share = oxbow ? Calibration.LAKE_FLOATING_LEAVED_OXBOW : Calibration.LAKE_FLOATING_LEAVED_LAKE;
			if (c.patchQ(2) < share) {
				return HabitatClassifier.Result.of(biome, Zone.FLOATING_LEAVED_PLANTS, association);
			}
		}
		return HabitatClassifier.Result.of(biome, z >= Calibration.LAKE_SUBMERGED ? Zone.SUBMERGED_PLANTS : Zone.NONE, association);
	}

	/**
	 * Troficzność najbliższej wody stojącej: starorzecza i wody poza sandrem eutroficzne; jeziora rynnowe
	 * i oczka na sandrze według skrótu zbiornika: dystroficzne (&lt; 0,3), oligotroficzne (lobeliowe) albo
	 * eutroficzne.
	 */
	static TrophicState trophicState(HabitatClassifier.Column c) {
		ColumnSample.StandingWaterKind r = c.w.standingWaterKind();
		if (c.wOutwashPlain <= 0.5 || r != ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE && r != ColumnSample.StandingWaterKind.KETTLE_POND) {
			return TrophicState.EUTROPHIC;
		}
		double h = (Noise.mix(c.w.lakeId() ^ 0x7A3E_11C5_2B9DL) >>> 11) * 0x1.0p-53;
		if (h < Calibration.LAKE_DYSTROPHIC) {
			return TrophicState.DYSTROPHIC;
		}
		return h < Calibration.LAKE_OLIGOTROPHIC ? TrophicState.OLIGOTROPHIC : TrophicState.EUTROPHIC;
	}
}
