package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.feature.tatra.HutPlan;
import pl.polishforests.worldgen.feature.tatra.MouthPlan;
import pl.polishforests.worldgen.feature.tatra.TatraSites;
import pl.polishforests.worldgen.feature.tatra.TerrainView;
import pl.polishforests.worldgen.feature.tatra.WaterPlan;

/**
 * Step TT6: the plans of the Tatra details on the ground of the landscape model (the top of a column is the model top, solid
 * under it, no fluid): how many anchors of each kind the plan of the kind accepts. The game has the same model ground with
 * a few blocks of difference (micro-relief), so this is the fast estimate of the share of the anchors without a place
 * ({@code TatraDetailsFeature#REJECTED}) that the game test measures in a world.
 */
class DetailPlansOnModelTest {
	/** The model ground as a {@link TerrainView}; a plan may write only into the chunk (chunkX, chunkZ), as in the game. */
	private record ModelGround(LandscapeModel m, VerticalScale v, int chunkX, int chunkZ) implements TerrainView {
		@Override
		public boolean writable(int x, int z) {
			return x >> 4 == chunkX && z >> 4 == chunkZ;
		}

		@Override
		public int top(int x, int z) {
			return v.topBlockY(m.sample(x, z).surface());
		}

		@Override
		public boolean solid(int x, int y, int z) {
			return y <= top(x, z);
		}

		@Override
		public boolean fluid(int x, int y, int z) {
			return false;
		}
	}

	@ParameterizedTest
	@MethodSource("pl.polishforests.worldgen.landscape.TatraSurvey#scales")
	void planAcceptanceOnTheModelGround(LandscapeScale scale) {
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(scale, seed);
			LandscapeModel.Tatra t = TatraSurvey.nearest(scale, seed, 1).getFirst();
			Map<TatraSites.Kind, int[]> counts = new EnumMap<>(TatraSites.Kind.class);
			for (TatraSites.Kind kind : TatraSites.Kind.values()) {
				int[] c = new int[2];
				counts.put(kind, c);
				int cell = TatraSites.cellChunks(m, kind) * 16;
				int cx = Math.floorDiv((int) Math.round(t.x()), cell);
				int cz = Math.floorDiv((int) Math.round(t.z()), cell);
				int n = (int) Math.ceil(t.reach() / cell);
				for (int x = cx - n; x <= cx + n; x++) {
					for (int z = cz - n; z <= cz + n; z++) {
						TatraSites.Site s = TatraSites.site(m, kind, x, z, true, null);
						if (s == null || Math.hypot(s.x() - t.x(), s.z() - t.z()) > t.reach()) {
							continue;
						}
						c[0]++;
						TerrainView ground = new ModelGround(m, TatraSurvey.vertical(scale), s.x() >> 4, s.z() >> 4);
						boolean ok = switch (kind) {
							case WATERFALL -> WaterPlan.waterfall(ground, s.x(), s.z(), s.seed()) != null;
							case SPRING -> WaterPlan.spring(ground, s.x(), s.z(), s.seed()) != null;
							case CAVE_MOUTH -> MouthPlan.mouth(ground, s.x(), s.z(), s.seed()) != null;
							case SHEPHERD_HUT -> HutPlan.hut(ground, s.x(), s.z(), s.seed()) != null;
						};
						c[1] += ok ? 1 : 0;
					}
				}
			}
			List<String> out = new ArrayList<>();
			counts.forEach((k, c) -> out.add(k + " " + c[1] + "/" + c[0]));
			System.out.println("ACCEPT " + TatraSurvey.label(scale, seed, t) + " " + out);
			for (TatraSites.Kind kind : new TatraSites.Kind[] {TatraSites.Kind.WATERFALL, TatraSites.Kind.CAVE_MOUTH}) {
				int[] c = counts.get(kind);
				assertTrue(c[0] == 0 || c[1] > 0 || scale == LandscapeScale.GAMEPLAY && seed != TatraSurvey.SEEDS[0],
						kind + " has anchors but the plan accepts none on the model ground (" + scale + ", " + seed + ")");
			}
		}
	}
}
