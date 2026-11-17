package pl.polishforests.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.GameRegistries;
import pl.polishforests.entity.base.AnimalProfile;
import pl.polishforests.entity.base.AnimalProfile.Activity;
import pl.polishforests.entity.base.ForestAlarm;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.roedeer.RoeDeer;
import pl.polishforests.entity.roedeer.RoeDeer.Antlers;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Step FA2 (docs/04-pionowy-wycinek.md "Projekt FA2"): the rules of the roe deer's behaviour and looks that need no world
 * (the deer in the world are the fauna game test's behaviour trials).
 */
class FaunaBehaviourTest {
	static {
		// The registries first: the profile lives in the entity class (also when this class runs alone).
		GameRegistries.bootstrap();
	}

	private static final AnimalProfile P = RoeDeer.PROFILE;
	/** Dawn 6:30, noon, dusk 18:30, midnight (the times of the activity trial of the game test). */
	private static final long DAWN = 500;
	private static final long NOON = 6_000;
	private static final long DUSK = 12_500;
	private static final long NIGHT = 18_000;

	@Test
	void scheduleFeedsAtDawnMiddayAndDusk() {
		// The design margin of the shares is larger than the threshold of the game test (0.10), which samples a few dozen deer.
		double dawn = P.share(Activity.FEED, DAWN);
		double dusk = P.share(Activity.FEED, DUSK);
		for (long other : new long[] {NOON, NIGHT}) {
			assertTrue(dawn >= P.share(Activity.FEED, other) + 0.3, "dawn against " + other);
			assertTrue(dusk >= P.share(Activity.FEED, other) + 0.3, "dusk against " + other);
		}
		// Midday: a feeding bout among the rest (report 07: a peak at midday), more resting than at dawn.
		assertTrue(P.share(Activity.FEED, NOON) > P.share(Activity.FEED, 3_500), "a midday feeding bout");
		assertTrue(P.share(Activity.REST, NOON) > P.share(Activity.REST, DAWN));
		assertEquals(Activity.REST, P.activity(9_000));
		assertEquals(Activity.REST, P.activity(NIGHT));
		for (AnimalProfile.Period period : P.schedule()) {
			double sum = 0;
			for (Activity a : Activity.values()) {
				sum += period.share(a);
			}
			assertEquals(1.0, sum, 1e-6);
			assertEquals(Activity.FEED, period.pick(0));
			assertEquals(Activity.MOVE, period.pick(0.9999F));
		}
		// Taken modulo a day; the night period runs over midnight until 4:00.
		assertEquals(P.periodIndex(NIGHT), P.periodIndex(NIGHT + 24_000));
		assertEquals(P.periodIndex(20_000), P.periodIndex(NIGHT));
	}

	@Test
	void groupsBySubSeason() {
		for (SubSeason s : SubSeason.values()) {
			boolean winter = switch (s) {
				case LATE_AUTUMN, EARLY_WINTER, MID_WINTER, LATE_WINTER, EARLY_SPRING -> true;
				default -> false;
			};
			assertEquals(winter, P.winterGroups(s), s.name());
			assertEquals(winter ? new AnimalProfile.Range(2, 6) : new AnimalProfile.Range(1, 3), P.group(s), s.name());
			boolean young = switch (s) {
				case LATE_SPRING, EARLY_SUMMER, MID_SUMMER, LATE_SUMMER -> true;
				default -> false;
			};
			assertEquals(young, P.young(s), s.name());
		}
		// A mother with two young is a summer group of three.
		assertTrue(P.summerGroup().contains(3));
		assertTrue(P.youngChance() > 0 && P.youngChance() < 1);
		// The radii of the groups nest: a lone animal that joins a group in winter is not lost at once (stage 2 fix).
		assertTrue(PolishAnimal.MERGE_SUMMER < PolishAnimal.MERGE_WINTER && PolishAnimal.MERGE_WINTER <= PolishAnimal.LONE_WINTER
				&& PolishAnimal.LONE_WINTER + 8 <= PolishAnimal.HERD_LOST);
	}

	@Test
	void youngOnlyInTheirSubSeasons() {
		// FA2 review: a spotted young roe deer only V-VIII (born V-VI, spots lost by autumn); out of season it grows up in the
		// calendar tick (also the young of a summer chunk unloaded until winter), unless a player locked its age.
		Set<SubSeason> young = Set.of(SubSeason.LATE_SPRING, SubSeason.EARLY_SUMMER, SubSeason.MID_SUMMER, SubSeason.LATE_SUMMER);
		for (SubSeason s : SubSeason.values()) {
			assertEquals(young.contains(s), RoeDeer.PROFILE.young(s), s.name());
			assertEquals(!young.contains(s), PolishAnimal.outOfSeasonYoung(RoeDeer.PROFILE, s, true, false), s.name());
			assertFalse(PolishAnimal.outOfSeasonYoung(RoeDeer.PROFILE, s, false, false), s.name());
			assertFalse(PolishAnimal.outOfSeasonYoung(RoeDeer.PROFILE, s, true, true), s.name());
		}
	}

	@Test
	void antlersBySubSeason() {
		long[] ids = new long[2000];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = 0x9E3779B97F4A7C15L * (i + 1);
		}
		int early = 0;
		for (long id : ids) {
			early += RoeDeer.earlyShedder(id) ? 1 : 0;
			for (SubSeason s : SubSeason.values()) {
				Antlers a = RoeDeer.antlers(s, true, false, id);
				Antlers want = switch (s) {
					// polished V-IX, in October polished or cast, cast in November, in velvet XII-IV (report 07 §3.2)
					case LATE_SPRING, EARLY_SUMMER, MID_SUMMER, LATE_SUMMER, EARLY_AUTUMN -> Antlers.HARD;
					case MID_AUTUMN -> RoeDeer.earlyShedder(id) ? Antlers.CAST : Antlers.HARD;
					case LATE_AUTUMN -> Antlers.CAST;
					default -> Antlers.VELVET;
				};
				assertEquals(want, a, s.name());
				assertEquals(Antlers.NONE, RoeDeer.antlers(s, false, false, id));
				assertEquals(Antlers.NONE, RoeDeer.antlers(s, true, true, id));
			}
		}
		// About half of the bucks cast in October.
		assertEquals(0.5, early / (double) ids.length, 0.05);
		assertTrue(Antlers.VELVET.shown() && Antlers.HARD.shown());
		assertFalse(Antlers.CAST.shown() || Antlers.NONE.shown());
	}

	@Test
	void everyLookHasItsTexture() throws IOException {
		Path dir = Path.of("src/main/resources/assets/polishforests/textures/entity/roe_deer");
		Set<String> files = new TreeSet<>();
		try (Stream<Path> s = Files.list(dir)) {
			s.forEach(p -> files.add(p.getFileName().toString().replace(".png", "")));
		}
		assertEquals(files, new TreeSet<>(RoeDeer.variants()), "textures of tools/fauna against RoeDeer.variants()");
		Set<String> used = new TreeSet<>();
		for (SubSeason s : SubSeason.values()) {
			for (boolean buck : new boolean[] {false, true}) {
				for (boolean flared : new boolean[] {false, true}) {
					for (long id : new long[] {1, 2, 3, 4, 5, 6}) {
						String v = RoeDeer.variant(buck, RoeDeer.winterCoat(s), RoeDeer.antlers(s, buck, false, id), flared);
						assertTrue(files.contains(v), v + " in " + s);
						used.add(v);
					}
				}
			}
		}
		used.add(RoeDeer.KID);
		assertEquals(files, used, "every texture is used in some sub-season");
		// The variants of V8 keep their names (the model calibration of the fauna game test uses them).
		assertEquals("doe_summer", RoeDeer.variant(false, false));
		assertEquals("buck_winter", RoeDeer.variant(true, true));
		assertEquals("buck_winter", RoeDeer.variant(true, true, Antlers.VELVET, false));
		assertEquals("buck_winter_hard_flared", RoeDeer.variant(true, true, Antlers.HARD, true));
		assertEquals("buck_winter", RoeDeer.variant(true, true, Antlers.CAST, false));
	}

	@Test
	void alarmFallsWithDistance() {
		ForestAlarm a = new ForestAlarm(Vec3.ZERO, 40, 1.0F, ForestAlarm.Kind.TEST, null);
		assertEquals(1.0F, a.intensityAt(0), 1e-6);
		assertEquals(0.5F, a.intensityAt(20), 1e-6);
		assertEquals(0F, a.intensityAt(40));
		assertTrue(a.fleesAt(8));
		assertTrue(a.fleesAt(20));
		assertFalse(a.fleesAt(30));
		assertTrue(a.intensityAt(30) > 0, "alert at 30 blocks");
		// The bark: its group and neighbours flee within 12 blocks, stand alert within 32.
		ForestAlarm bark = new ForestAlarm(Vec3.ZERO, RoeDeer.BARK_RADIUS, RoeDeer.BARK_STRENGTH, ForestAlarm.Kind.BARK, null);
		assertTrue(bark.fleesAt(12));
		assertFalse(bark.fleesAt(13));
		assertTrue(bark.intensityAt(31) > 0);
		// A running player is heard beyond the largest flight distance (alert there).
		assertTrue(ForestAlarm.PLAYER_RADIUS > P.fleeMax());
		// New kinds only at the end (FA4: the jay).
		assertEquals(List.of(ForestAlarm.Kind.TEST, ForestAlarm.Kind.RUNNING_PLAYER, ForestAlarm.Kind.BARK),
				List.of(ForestAlarm.Kind.values()));
	}

	@Test
	void densityCalibration() {
		// Real: 0.085 roe deer per hectare of forest (about 810 thousand on 9.5 million ha, report 07 §2), × 30 at generation
		// and × 45 for the spawner: a circle of 64 blocks of fresh pine forest (weight 0.8) holds 2-3 deer from the generation.
		assertEquals(0.085F, P.realDensity(), 1e-6);
		double circle = Math.PI * 64 * 64;
		double pine = P.generationDensityPerBlock() * P.weight(HabitatBiome.FRESH_PINE_FOREST) * circle;
		assertTrue(pine >= 2 && pine <= 3, "fresh pine circle " + pine);
		// The weights by habitat: oak-hornbeam and riparian forests most, beech and fir about half, alder carr, montane spruce
		// and dwarf pine little; the edge 0.6.
		assertEquals(1.0F, P.weight(HabitatBiome.OAK_HORNBEAM_FOREST));
		assertTrue(P.weight(HabitatBiome.ASH_ALDER_FOREST) >= 0.8F);
		assertTrue(P.weight(HabitatBiome.LOWLAND_BEECH_FOREST) < P.weight(HabitatBiome.OAK_HORNBEAM_FOREST));
		assertTrue(P.weight(HabitatBiome.BOG_WOODLAND) < P.weight(HabitatBiome.FRESH_PINE_FOREST));
		assertTrue(P.weight(HabitatBiome.DWARF_PINE_SCRUB) < P.weight(HabitatBiome.MONTANE_SPRUCE_FOREST));
		assertEquals(0.6F, P.edgeWeight());
		assertTrue(P.homeRange() >= 32 && P.homeRange() <= 64);
	}
}
