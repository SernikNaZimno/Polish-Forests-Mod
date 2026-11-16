package pl.polishforests.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.GameRegistries;
import pl.polishforests.entity.base.AnimalProfile;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.roedeer.RoeDeer;
import pl.polishforests.entity.spawn.FaunaSpawner;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Step FA1 (docs/04-pionowy-wycinek.md "Projekt FA1"): the animal profiles and the arithmetic of the fauna spawners that
 * needs no world (the counts in the world are the fauna game test's census).
 */
class FaunaProfileTest {
	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void roeDeerProfileIsSane() {
		AnimalProfile p = RoeDeer.PROFILE;
		assertEquals("roe_deer", p.species());
		// PW-15 (b), CP-D.3: the game density is about 20-50 times the real one.
		assertTrue(p.factor() >= 20 && p.factor() <= 50, "factor " + p.factor());
		// Every forest of the mod is a habitat, no water and no open land (open land only as the forest edge).
		for (HabitatBiome b : HabitatBiome.values()) {
			if (b.isForest()) {
				assertTrue(p.weight(b) > 0, b + " has no weight");
			} else {
				assertEquals(0, p.weight(b), b + " has a weight");
			}
		}
		assertTrue(p.edgeWeight() > 0 && p.edgeWeight() < 1);
		assertEquals(0, p.weight(null));
		// Groups of 1-3 in summer (FA1), more in winter (FA2).
		assertEquals(new AnimalProfile.Range(1, 3), p.group(SubSeason.MID_SUMMER));
		assertTrue(p.group(SubSeason.MID_WINTER).max() > 3);
		// The flight distances of the plan: 20-40 blocks.
		assertEquals(20, p.fleeMin());
		assertEquals(40, p.fleeMax());
		// The size of V8 (C2).
		assertEquals(0.7F, p.width());
		assertEquals(1.1F, p.height());
	}

	@Test
	void winterCoatAsInStepV8() {
		// Report 07 §3.2: summer coat V-IX, the rule of V8 (RoeDeer.winterCoat) unchanged.
		for (SubSeason s : SubSeason.values()) {
			boolean summer = switch (s) {
				case LATE_SPRING, EARLY_SUMMER, MID_SUMMER, LATE_SUMMER, EARLY_AUTUMN -> true;
				default -> false;
			};
			assertEquals(!summer, RoeDeer.winterCoat(s), s.name());
		}
	}

	@Test
	void savedFieldsOfStepV8Unchanged() {
		assertEquals(List.of("Buck", "WinterCoat"), RoeDeer.SAVED_FIELDS);
		assertEquals(RoeDeer.SAVED_FIELDS, ModEntities.savedFields(ModEntities.roeDeer()));
		assertEquals(1, ModEntities.fauna().size());
	}

	@Test
	void scheduleCoversTheDay() {
		AnimalProfile p = RoeDeer.PROFILE;
		assertEquals(AnimalProfile.Activity.FEED, p.activity(0));
		assertEquals(AnimalProfile.Activity.REST, p.activity(6_000));
		assertEquals(AnimalProfile.Activity.FEED, p.activity(12_000));
		assertEquals(AnimalProfile.Activity.REST, p.activity(18_000));
		assertEquals(AnimalProfile.Activity.FEED, p.activity(23_000));
		assertEquals(p.activity(6_000), p.activity(30_000));
	}

	@Test
	void forestChunkExpectsAFewDeer() {
		AnimalProfile p = RoeDeer.PROFILE;
		// A chunk of oak-hornbeam forest (weight 1): density × 256 columns; with ×30 about 0.08 roe deer per chunk, so a
		// circle of 64 blocks (about 50 chunks) holds about 3-4 of them, and the 128-block despawn circle about 15.
		double perChunk = p.densityPerBlock() * 256;
		assertTrue(perChunk > 0.04 && perChunk < 0.2, "per chunk " + perChunk);
		double circle = p.densityPerBlock() * Math.PI * FaunaSpawner.AREA_RADIUS * FaunaSpawner.AREA_RADIUS;
		assertTrue(circle >= 1 && circle <= p.maxNearPlayer(), "circle " + circle);
		double groups = FaunaSpawner.expectedGroups(p, p.summerGroup(), 256);
		assertEquals(perChunk / 2, groups, 1e-12);
	}

	@Test
	void stochasticRoundKeepsTheMean() {
		RandomSource r = RandomSource.create(5);
		long sum = 0;
		int n = 100_000;
		for (int i = 0; i < n; i++) {
			sum += FaunaSpawner.stochasticRound(2.3, r);
		}
		assertEquals(2.3, sum / (double) n, 0.02);
		assertEquals(4, FaunaSpawner.stochasticRound(4.0, r));
	}

	@Test
	void generationRandomIsPerChunkAndSeed() {
		long a = FaunaSpawner.generationRandom(20260927L, 3, -7).nextLong();
		assertEquals(a, FaunaSpawner.generationRandom(20260927L, 3, -7).nextLong());
		assertNotEquals(a, FaunaSpawner.generationRandom(20260927L, -7, 3).nextLong());
		assertNotEquals(a, FaunaSpawner.generationRandom(20260928L, 3, -7).nextLong());
	}

	@Test
	void pickColumnFollowsTheWeights() {
		float[] w = new float[256];
		w[17] = 1;
		w[200] = 0.25F;
		RandomSource r = RandomSource.create(9);
		int a = 0;
		int b = 0;
		for (int i = 0; i < 20_000; i++) {
			int c = FaunaSpawner.pickColumn(w, r);
			if (c == 17) {
				a++;
			} else if (c == 200) {
				b++;
			} else {
				assertEquals(-1, c);
			}
		}
		assertEquals(4.0, a / (double) b, 0.4);
		assertEquals(-1, FaunaSpawner.pickColumn(new float[256], r));
	}

	@Test
	void despawnOnlyFarAway() {
		assertEquals(128, PolishAnimal.DESPAWN_DISTANCE);
		assertTrue(FaunaSpawner.SPAWN_MAX < FaunaSpawner.COUNT_RADIUS && FaunaSpawner.COUNT_RADIUS < PolishAnimal.DESPAWN_DISTANCE);
	}

	@Test
	void columnWeightsGiveTheForestEdge() {
		// A chunk half oak-hornbeam forest (x < 8), half hay meadow: the meadow within EDGE_REACH of the forest is the edge.
		int[] codes = new int[256];
		short[] top = new short[256];
		short[] water = new short[256];
		java.util.Arrays.fill(water, ChunkHabitats.NO_WATER);
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				HabitatBiome b = x < 8 ? HabitatBiome.OAK_HORNBEAM_FOREST : HabitatBiome.HAY_MEADOW;
				codes[ChunkHabitats.index(x, z)] = b.ordinal(); // the biome is the low 6 bits of the code (Habitat)
			}
		}
		ChunkHabitats h = new ChunkHabitats(codes, top, water, 0, 0, ChunkHabitats.NO_STRUCTURES, ChunkHabitats.NO_CAVES);
		float[] w = pl.polishforests.entity.spawn.FaunaHabitat.columnWeights(h, RoeDeer.PROFILE);
		assertEquals(1.0F, w[ChunkHabitats.index(3, 3)]);
		assertEquals(RoeDeer.PROFILE.edgeWeight(), w[ChunkHabitats.index(12, 3)]);
		assertEquals(RoeDeer.PROFILE.edgeWeight(), w[ChunkHabitats.index(15, 3)]);
	}
}
