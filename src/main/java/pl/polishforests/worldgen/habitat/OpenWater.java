package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Classifier step 1: columns with water (§2.3, §4.4). Sea, lagoon, river or stream, lake or dystrophic
 * lake; in shallow eutrophic water a reedbed (a biome in a belt ≥ 12 m, narrower as the REEDBED zone),
 * in lakes the SUBMERGED_PLANTS and FLOATING_LEAVED_PLANTS zones, in a dystrophic lake a floating mat.
 */
final class OpenWater {
	private OpenWater() {
	}

	/** Trophic state of standing water. */
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
		// Distance from the shore into the water (m); s is negative in water.
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
		// Oxbow lake: reedbed only in a belt by the shore (2–15 m), beyond it floating-leaved plants on 60–90% of the surface.
		boolean nearShore = !oxbow
				|| waterDepth <= Math.max(Calibration.MIN_REEDBED, Calibration.LAKE_OXBOW_REEDBED_K * c.k) * c.jitter();
		if (z <= Calibration.LAKE_REEDBED_Z && nearShore) {
			if (trophic == TrophicState.EUTROPHIC) {
				// A biome in water bodies with room for a belt ≥ 12 m (the whole shallows, without inverted rings by the shore);
				// in oxbow lakes the belt is 15 m·k, in small kettle ponds only a zone.
				double radius = c.w.standingWaterRadius();
				double band = oxbow ? Calibration.LAKE_OXBOW_REEDBED_K * c.k
						: Double.isNaN(radius) || radius >= Calibration.LAKE_REEDBED_BIOME_RADIUS_K * c.k ? Calibration.BIOME_BAND : 0;
				return band >= Calibration.BIOME_BAND ? HabitatClassifier.Result.of(HabitatBiome.REEDBED, Zone.REEDBED, association)
						: HabitatClassifier.Result.of(biome, Zone.REEDBED, association);
			}
			// Lobelia lake: reedbed only in patches.
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
	 * Trophic state of the nearest standing water: oxbow lakes and waters outside the outwash plain are eutrophic;
	 * tunnel valley lakes and kettle ponds on the outwash plain follow the water body hash: dystrophic (&lt; 0.3),
	 * oligotrophic (lobelia lakes) or eutrophic.
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
