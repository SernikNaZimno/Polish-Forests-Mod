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
import pl.polishforests.entity.squirrel.RedSquirrel;
import pl.polishforests.entity.wildboar.WildBoar;
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
		// PW-15 (b), CP-D.3: the game density is about 20-50 times the real one, the generation and the spawner apart (R16).
		assertTrue(p.generationFactor() >= 20 && p.generationFactor() <= 50, "generation factor " + p.generationFactor());
		assertTrue(p.spawnerFactor() >= 20 && p.spawnerFactor() <= 50, "spawner factor " + p.spawnerFactor());
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
		// Groups of 1-3 in summer, 2-6 in winter (FA2).
		assertEquals(new AnimalProfile.Range(1, 3), p.group(SubSeason.MID_SUMMER));
		assertEquals(new AnimalProfile.Range(2, 6), p.group(SubSeason.MID_WINTER));
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
		// Step FA3: the wild boar after the roe deer (the roe deer's draws at generation stay those of FA2).
		// Step FA5: the red squirrel last (the draws of the roe deer and the wild boar at generation stay those of FA3).
		assertEquals(3, ModEntities.fauna().size());
		assertEquals(RoeDeer.PROFILE, ModEntities.fauna().get(0).profile());
		assertEquals(WildBoar.PROFILE, ModEntities.fauna().get(1).profile());
		assertEquals(RedSquirrel.PROFILE, ModEntities.fauna().get(2).profile());
		assertEquals(List.of("Dark"), RedSquirrel.SAVED_FIELDS);
		assertEquals(RedSquirrel.SAVED_FIELDS, ModEntities.savedFields(ModEntities.redSquirrel()));
		assertEquals(List.of("Male", "Seniority"), WildBoar.SAVED_FIELDS);
		assertEquals(WildBoar.SAVED_FIELDS, ModEntities.savedFields(ModEntities.wildBoar()));
	}

	/** Step FA5: the red squirrel's profile (docs/04 "Projekt FA5"). */
	@Test
	void redSquirrelProfileIsSane() {
		AnimalProfile p = RedSquirrel.PROFILE;
		assertEquals("red_squirrel", p.species());
		// KFA-5: about 20-50 times the real density, generation and spawner apart.
		assertTrue(p.generationFactor() >= 20 && p.generationFactor() <= 50, "generation factor " + p.generationFactor());
		assertTrue(p.spawnerFactor() >= 20 && p.spawnerFactor() <= 50, "spawner factor " + p.spawnerFactor());
		// The conifer and mixed forests of the plan carry the most; the oak-hornbeam and beech forests are habitats too; no open
		// land, no water, no forest edge, no dwarf pine.
		for (HabitatBiome b : List.of(HabitatBiome.MIXED_PINE_FOREST, HabitatBiome.MIXED_FOREST, HabitatBiome.MONTANE_SPRUCE_FOREST,
				HabitatBiome.FRESH_PINE_FOREST)) {
			assertTrue(p.weight(b) >= 0.9F, b + " " + p.weight(b));
		}
		for (HabitatBiome b : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.LOWLAND_BEECH_FOREST, HabitatBiome.MONTANE_BEECH_FOREST,
				HabitatBiome.UPLAND_FIR_FOREST)) {
			assertTrue(p.weight(b) >= 0.7F, b + " " + p.weight(b));
		}
		for (HabitatBiome b : HabitatBiome.values()) {
			if (!b.isForest()) {
				assertEquals(0, p.weight(b), b + " has a weight");
			}
		}
		assertEquals(0, p.weight(HabitatBiome.DWARF_PINE_SCRUB));
		assertEquals(0, p.edgeWeight());
		// Alone all year, no young (C10), and its size (0.4 x 0.45).
		assertEquals(new AnimalProfile.Range(1, 1), p.group(SubSeason.MID_SUMMER));
		assertEquals(new AnimalProfile.Range(1, 1), p.group(SubSeason.MID_WINTER));
		assertEquals(0, p.youngChance());
		for (SubSeason s : SubSeason.values()) {
			assertTrue(!p.young(s), s.name());
		}
		assertEquals(0.4F, p.width());
		assertEquals(0.45F, p.height());
		// The winter coat with the ear tufts from mid autumn to early spring, the summer coat from mid spring to early autumn.
		assertTrue(p.winterCoat(SubSeason.MID_WINTER));
		assertTrue(p.winterCoat(SubSeason.MID_AUTUMN));
		assertTrue(!p.winterCoat(SubSeason.MID_SUMMER));
		assertTrue(!p.winterCoat(SubSeason.MID_SPRING));
		// The dark morph more often in the montane spruce and the fir forests; a cone in the conifer forests, a nut elsewhere.
		assertTrue(RedSquirrel.darkShare(HabitatBiome.MONTANE_SPRUCE_FOREST) > RedSquirrel.darkShare(HabitatBiome.OAK_HORNBEAM_FOREST));
		assertTrue(RedSquirrel.coneForest(HabitatBiome.FRESH_PINE_FOREST));
		assertTrue(RedSquirrel.coneForest(HabitatBiome.MONTANE_SPRUCE_FOREST));
		assertTrue(!RedSquirrel.coneForest(HabitatBiome.OAK_HORNBEAM_FOREST));
		assertTrue(!RedSquirrel.coneForest(null));
		// The texture variants of the generator.
		assertEquals(List.of("red_summer", "red_winter", "dark_summer", "dark_winter"), RedSquirrel.variants());
	}

	@Test
	void wildBoarProfileIsSane() {
		AnimalProfile p = WildBoar.PROFILE;
		assertEquals("wild_boar", p.species());
		// KFA-5: about 20-50 times the real density, generation and spawner apart.
		assertTrue(p.generationFactor() >= 20 && p.generationFactor() <= 50, "generation factor " + p.generationFactor());
		assertTrue(p.spawnerFactor() >= 20 && p.spawnerFactor() <= 50, "spawner factor " + p.spawnerFactor());
		// C3: about 4 boars to 10 roe deer at the same weight, at generation and for the spawner.
		double generation = p.generationDensityPerBlock() / RoeDeer.PROFILE.generationDensityPerBlock();
		double spawner = p.spawnerDensityPerBlock() / RoeDeer.PROFILE.spawnerDensityPerBlock();
		assertEquals(0.4, generation, 0.05, "generation ratio");
		assertEquals(0.4, spawner, 0.05, "spawner ratio");
		// The habitats of the plan: oak-hornbeam and beech forests, alder carrs and riparian forests, mixed pine and mixed
		// forests carry the most; no open land and no water (the edge apart), no dwarf pine.
		for (HabitatBiome b : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.LOWLAND_BEECH_FOREST, HabitatBiome.ALDER_CARR,
				HabitatBiome.ASH_ALDER_FOREST, HabitatBiome.ELM_ASH_FOREST, HabitatBiome.WILLOW_POPLAR_FOREST, HabitatBiome.MIXED_PINE_FOREST,
				HabitatBiome.MIXED_FOREST)) {
			assertTrue(p.weight(b) >= 0.7F, b + " " + p.weight(b));
		}
		for (HabitatBiome b : HabitatBiome.values()) {
			if (!b.isForest()) {
				assertEquals(0, p.weight(b), b + " has a weight");
			}
			assertTrue(p.weight(b) <= p.weight(HabitatBiome.OAK_HORNBEAM_FOREST), b.name());
		}
		assertEquals(0, p.weight(HabitatBiome.DWARF_PINE_SCRUB));
		assertTrue(p.weight(HabitatBiome.DRY_PINE_FOREST) < p.weight(HabitatBiome.MIXED_PINE_FOREST));
		// Sounders of 3-8, a quarter lone males, up to four young per sow.
		assertEquals(new AnimalProfile.Range(3, 8), p.group(SubSeason.MID_SUMMER));
		assertEquals(0.25F, p.soloShare());
		assertEquals(4, p.maxYoung());
		assertEquals(0.9F, p.width());
		assertEquals(0.95F, p.height());
	}

	@Test
	void loneAnimalsCountInTheExpectedGroups() {
		// The roe deer has no lone animals of its own: the mean group is the mean of the range (FA2 unchanged).
		assertEquals(RoeDeer.PROFILE.summerGroup().mean(), RoeDeer.PROFILE.meanGroupSize(RoeDeer.PROFILE.summerGroup()));
		// The wild boar: a quarter of the groups one lone male, the rest sounders of 3-8 (mean 5.5).
		AnimalProfile p = WildBoar.PROFILE;
		double mean = 0.25 + 0.75 * 5.5;
		assertEquals(mean, p.meanGroupSize(p.summerGroup()), 1e-9);
		assertEquals(p.generationDensityPerBlock() * 256 / mean, FaunaSpawner.expectedGroups(p, p.summerGroup(), 256), 1e-12);
		// A circle of 64 blocks of oak-hornbeam forest: a few boars, within the spawner's own limit.
		double circle = p.generationDensityPerBlock() * Math.PI * FaunaSpawner.AREA_RADIUS * FaunaSpawner.AREA_RADIUS;
		assertTrue(circle >= 1 && circle <= p.maxNearPlayer(), "circle " + circle);
		double target = p.spawnerDensityPerBlock() * Math.PI * FaunaSpawner.AREA_RADIUS * FaunaSpawner.AREA_RADIUS;
		assertTrue(target >= circle && target <= p.maxNearPlayer(), "spawner target " + target);
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
		double perChunk = p.generationDensityPerBlock() * 256;
		assertTrue(perChunk > 0.04 && perChunk < 0.2, "per chunk " + perChunk);
		double circle = p.generationDensityPerBlock() * Math.PI * FaunaSpawner.AREA_RADIUS * FaunaSpawner.AREA_RADIUS;
		assertTrue(circle >= 1 && circle <= p.maxNearPlayer(), "circle " + circle);
		double target = p.spawnerDensityPerBlock() * Math.PI * FaunaSpawner.AREA_RADIUS * FaunaSpawner.AREA_RADIUS;
		assertTrue(target >= circle && target <= p.maxNearPlayer(), "spawner target " + target);
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
