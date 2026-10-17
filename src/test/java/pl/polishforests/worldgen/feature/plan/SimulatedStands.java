package pl.polishforests.worldgen.feature.plan;

import java.util.Arrays;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.CrownTerritories;
import pl.polishforests.worldgen.tree.CrownTerritory;
import pl.polishforests.worldgen.tree.ForestStandMap;
import pl.polishforests.worldgen.tree.StandMap;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Stands of the vertical slice planned without the game (step V9 of docs/04-pionowy-wycinek.md): an area of uniform
 * oak-hornbeam or fresh pine forest in one stand (managed or natural, one age class, the canopy gaps of the stand map in
 * a natural stand) on level ground, with the tree stand plan, the vacancies and the crown territories of the world; only
 * the procedural trees (the placeholder shrubs and small trees are not planned). Shared by the calibration report
 * ({@link StandCensusSimulationTest}) and the stand views ({@link SliceStandPreview}).
 */
final class SimulatedStands {
	static final long SEED = 20260927L;
	static final int GROUND = 64;
	private static final GeneratedTreePalette GENERATED = GeneratedTreePalette.load();
	private static final float[] RAMP = {1, 1, 1, 1};

	/** A planned tree. */
	@FunctionalInterface
	interface Sink {
		void tree(TreeVoxels voxels, Species species, CrownLayer layer);
	}

	private SimulatedStands() {
	}

	/** A stand map of one stand everywhere, with the canopy gaps of the world's stand map when natural. */
	static StandMap stand(AgeClass age, boolean natural) {
		ForestStandMap world = new ForestStandMap(SEED, 1, 0.85);
		return new StandMap() {
			@Override
			public AgeClass ageClass(int x, int z) {
				return age;
			}

			@Override
			public boolean natural(int x, int z) {
				return natural;
			}

			@Override
			public boolean canopyGap(int x, int z) {
				return natural && world.rawGapDepth(x, z, age) > 0;
			}

			@Override
			public double gapDepth(int x, int z) {
				return natural ? Math.max(0, world.rawGapDepth(x, z, age)) : 0;
			}
		};
	}

	/** Plans the procedural trees of chunks 0 … chunks − 1 (both axes) of a uniform stand of the biome on level ground. */
	static void plant(HabitatBiome biome, boolean natural, AgeClass age, int chunks, Sink sink) {
		plant(biome, natural, age, chunks, Species.FLAG_HORNBEAM, (x, z) -> GROUND, sink);
	}

	/** Rolling ground: {@link #GROUND} ± amplitude blocks, a wavelength of 40 blocks. */
	static TreeShapePlan.Ground relief(double amplitude) {
		Noise n = new Noise(SEED).derive("test.relief");
		return (x, z) -> GROUND + (int) Math.round(amplitude * n.at(x, z, 40));
	}

	/**
	 * Plans the procedural trees of chunks 0 … chunks − 1 (both axes) of a uniform stand of the biome on the ground, within
	 * the ranges of the species flags ({@link Species#FLAG_HORNBEAM}: the lowlands of the plots of the slice).
	 */
	static void plant(HabitatBiome biome, boolean natural, AgeClass age, int chunks, int ranges, TreeShapePlan.Ground ground,
			Sink sink) {
		int code = Habitat.pack(biome, Zone.NONE, ForestSiteType.NONE, Association.TYPICAL, LandCover.forBiome(biome),
				ranges, Soil.forBiome(biome, Zone.NONE));
		StandMap map = stand(age, natural);
		CrownTerritories crowns = new CrownTerritories(SEED, map);
		TreeStandPlan.Stand stand = new TreeStandPlan.Stand(RAMP, new Noise(SEED).derive("feature.tree_stand.gaps"), 1, map, crowns);
		int[] codes = new int[256];
		Arrays.fill(codes, code);
		int[] region = new int[Ecotone.SPAN * Ecotone.SPAN];
		Arrays.fill(region, code);
		CrownTerritories.Session session = crowns.session();
		for (int a = 0; a < chunks; a++) {
			for (int b = 0; b < chunks; b++) {
				int[] plan = TreeStandPlan.of(codes, new int[256], GENERATED.palette, SEED, a, b, stand);
				Ecotone.Region r = new Ecotone.Region(a, b, region, 1);
				StandVacancies v = new StandVacancies(a, b, plan, r, GENERATED.palette, stand, new StandVacancies.Neighbors() {
					@Override
					public int waterDepth(int x, int z) {
						return 0;
					}

					@Override
					public float[] ramp(int chunkX, int chunkZ) {
						return RAMP;
					}
				}, new TreeStandPlan.Candidates(SEED), SEED);
				for (int p : plan) {
					int t = TreeStandPlan.tree(p);
					if (!GENERATED.procedural(t)) {
						continue;
					}
					Species species = GENERATED.species.get(t);
					int c = TreeStandPlan.column(p);
					int x = (a << 4) + (c >> 4);
					int z = (b << 4) + (c & 15);
					CrownLayer layer = TreeStandPlan.layer(p);
					CrownTerritory territory = session.territory(x, z, v);
					AgeClass treeAge = layer == CrownLayer.REGENERATION && map.canopyGap(x, z) ? AgeClass.POLE_STAGE : age;
					TreeVoxels vox = TreeShapePlan.plan(new TreeRequest(species, layer, treeAge, SEED, x, ground.top(x, z) + 1, z, territory,
							session::candidate, map, ground));
					sink.tree(vox, species, layer);
				}
			}
		}
	}
}
