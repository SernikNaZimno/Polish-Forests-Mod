package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/**
 * Step TT3 (KT-1, docs/04-pionowy-wycinek.md "Projekt TT3"): the belts of the Tatras. The thresholds of
 * {@link BeltProfile#TATRA} and their blend, and the shares of the belts in elevation bands of the Tatra massifs nearest
 * the origin at gameplay scale (a model-side check of the {@code tatra} gametest): at 1300–1450 m at least 80% of the
 * columns in the upper montane spruce forest, at 1600–1750 m at least 70% in the dwarf pine scrub, at 1900–2200 m at
 * least 70% on the alpine grassland and above 2350 m at least 70% in the subnival belt or on bare rock. Open water, the
 * waterside zones and the valley floors are left out of the shares (a riparian forest is no belt). Step TT5: the same in
 * the realistic scale (the profile works in meters; the massifs lie up to 2000 km from the origin, the lattice grows with
 * the forms).
 */
class BeltProfileTatraTest {
	static final long SEED_A = 20260927L;
	static final long SEED_B = -7_316_550_294_015_845_337L;
	/** Half side of the box searched for massifs (m). */
	static final double BOX = 300_000;
	/** Step TT5: half side of the box at realistic scale (the wide radius of find tatra there). */
	static final double BOX_REAL = 2_000_000;
	/** Lattice step (m). */
	static final double STEP = 24;

	/** Step TT5: the scales of the parameterized tests. */
	enum Scale {
		GAMEPLAY(LandscapeScale.GAMEPLAY, BOX), REALISTIC(LandscapeScale.REALISTIC, BOX_REAL);

		final LandscapeScale scale;
		final double box;

		Scale(LandscapeScale scale, double box) {
			this.scale = scale;
			this.box = box;
		}

		/** A lattice step of the gameplay tests grown with the forms (3.33 times at realistic scale). */
		double step(double gameplayStep) {
			return gameplayStep * scale.mountainSpacing() / LandscapeScale.GAMEPLAY.mountainSpacing();
		}

		/** The massif of the seed nearest the origin. */
		LandscapeModel.Tatra nearest(LandscapeModel m) {
			List<LandscapeModel.Tatra> ts = new ArrayList<>(m.tatras(-box, -box, box, box));
			ts.sort(Comparator.comparingDouble(t -> Math.hypot(t.x(), t.z())));
			assertTrue(!ts.isEmpty(), "no Tatras within " + box + " m");
			return ts.get(0);
		}
	}

	@Test
	void tatraThresholdsInOrder() {
		BeltProfile t = BeltProfile.TATRA;
		assertTrue(t.lowerMontane() <= 750 && t.upperMontane() >= 1_200 && t.upperMontane() <= 1_250);
		assertTrue(t.timberline() >= 1_500 && t.timberline() <= 1_550);
		assertEquals(1_800, t.alpineThreshold());
		assertTrue(t.subnivalThreshold() >= 2_250 && t.subnivalThreshold() <= 2_300);
		assertTrue(!t.largeMassifOnly() && BeltProfile.BESKIDS.largeMassifOnly());
		assertEquals(BeltProfile.NO_SUBNIVAL, BeltProfile.BESKIDS.subnivalThreshold());
		assertEquals(AltitudinalBelts.TIMBERLINE, BeltProfile.BESKIDS.timberline());
		assertEquals(0, BeltProfile.BESKIDS.montaneRamp());
		// The blend is exact at both ends.
		assertEquals(1_390, BeltProfile.blend(1_390, 1_525, 0));
		assertEquals(1_525, BeltProfile.blend(1_390, 1_525, 1));
		assertEquals(1_457.5, BeltProfile.blend(1_390, 1_525, 0.5), 1e-9);
		assertThrows(IllegalArgumentException.class, () -> new BeltProfile("bad", 700, 600, 1_500, 1_800, 2_200, 0, 0, false));
	}

	/** The bands of KT-1: from, to (m), the share required and the biomes or zone that count. */
	enum Band {
		UPPER_MONTANE(1_300, 1_450, 0.80), DWARF_PINE(1_600, 1_750, 0.70), ALPINE(1_900, 2_200, 0.70), SUBNIVAL(2_350, 9_000, 0.70);

		final double from;
		final double to;
		final double required;

		Band(double from, double to, double required) {
			this.from = from;
			this.to = to;
			this.required = required;
		}

		boolean counts(int code) {
			HabitatBiome b = Habitat.biome(code);
			return switch (this) {
				case UPPER_MONTANE -> b == HabitatBiome.MONTANE_SPRUCE_FOREST;
				case DWARF_PINE -> b == HabitatBiome.DWARF_PINE_SCRUB;
				case ALPINE -> b == HabitatBiome.ALPINE_GRASSLAND;
				case SUBNIVAL -> b == HabitatBiome.SUBNIVAL_ROCKS || Habitat.zone(code) == Zone.BARE_ROCK;
			};
		}
	}

	@ParameterizedTest
	@EnumSource(Scale.class)
	void tatraBeltShares(Scale sc) {
		StringBuilder report = new StringBuilder();
		AtomicLongArray all = new AtomicLongArray(2 * Band.values().length);
		for (long seed : new long[] {SEED_A, SEED_B}) {
			LandscapeModel m = new LandscapeModel(seed, sc.scale, 1.0, true);
			HabitatClassifier k = new HabitatClassifier(seed, m, HabitatClassifier.Mode.NATURAL);
			LandscapeModel.Tatra t = sc.nearest(m);
			AtomicLongArray l = shares(m, k, t, sc.step(STEP));
			report.append(String.format(Locale.ROOT, "%s seed %d massif (%.0f, %.0f), summit %.0f m:", sc.scale.id(), seed, t.x(), t.z(),
					t.targetSummit()));
			for (Band b : Band.values()) {
				long n = l.get(2 * b.ordinal());
				long y = l.get(2 * b.ordinal() + 1);
				all.addAndGet(2 * b.ordinal(), n);
				all.addAndGet(2 * b.ordinal() + 1, y);
				report.append(String.format(Locale.ROOT, " %s %.1f%% (%d)", b, n == 0 ? 0 : 100.0 * y / n, n));
			}
			report.append('\n');
		}
		System.out.print(report);
		for (Band b : Band.values()) {
			long n = all.get(2 * b.ordinal());
			double share = n == 0 ? 0 : (double) all.get(2 * b.ordinal() + 1) / n;
			assertTrue(n >= 200, "too few columns in the band " + b + ": " + n + "\n" + report);
			assertTrue(share >= b.required, "share of the band " + b + ": " + share + "\n" + report);
		}
	}

	/**
	 * The habitats of the Tatras (the census of the {@code habitats} game test on the model): every forest and grassland
	 * association of the step occurs in the massif nearest the origin of seed A (a 32 m lattice, Tatra weight at least 0.5).
	 */
	@ParameterizedTest
	@EnumSource(Scale.class)
	void tatraHabitatsOccur(Scale sc) {
		LandscapeModel m = new LandscapeModel(SEED_A, sc.scale, 1.0, true);
		HabitatClassifier k = new HabitatClassifier(SEED_A, m, HabitatClassifier.Mode.NATURAL);
		LandscapeModel.Tatra t = sc.nearest(m);
		Object[][] expected = {{HabitatBiome.MONTANE_BEECH_FOREST, null}, {HabitatBiome.MONTANE_SPRUCE_FOREST, Association.ABIETI_PICEETUM},
				{HabitatBiome.MONTANE_SPRUCE_FOREST, Association.POLYSTICHO_PICEETUM}, {HabitatBiome.DWARF_PINE_SCRUB, null},
				{HabitatBiome.ALPINE_GRASSLAND, Association.ALPINE_SILICATE}, {HabitatBiome.ALPINE_GRASSLAND, Association.ALPINE_CALCAREOUS},
				{HabitatBiome.ALPINE_GRASSLAND, Association.SNOWBED}, {HabitatBiome.ALPINE_GRASSLAND, Association.ROCK_GRASSLAND},
				{HabitatBiome.SUBNIVAL_ROCKS, null}, {HabitatBiome.MOUNTAIN_TARN, null}};
		AtomicLongArray counts = new AtomicLongArray(expected.length);
		double half = 1.2 * t.halfLength();
		double step = sc.step(32);
		int n = (int) (2 * half / step);
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = t.x() - half + (i + 0.5) * step;
				double z = t.z() - half + (j + 0.5) * step;
				ColumnSample s = m.sample(x, z);
				if (s.terrain().wTatra() < 0.5) {
					continue;
				}
				int code = k.classify(s, x, z);
				for (int h = 0; h < expected.length; h++) {
					if (Habitat.biome(code) == expected[h][0] && (expected[h][1] == null || Habitat.association(code) == expected[h][1])) {
						counts.incrementAndGet(h);
					}
				}
			}
		});
		StringBuilder report = new StringBuilder(sc.scale.id() + " Tatra habitats (columns of a " + step + " m lattice):");
		for (int h = 0; h < expected.length; h++) {
			report.append(' ').append(((HabitatBiome) expected[h][0]).id()).append(expected[h][1] == null ? "" : "/" + expected[h][1])
					.append(' ').append(counts.get(h)).append(';');
		}
		System.out.println(report);
		for (int h = 0; h < expected.length; h++) {
			assertTrue(counts.get(h) > 0, "missing: " + expected[h][0] + "/" + expected[h][1] + "\n" + report);
		}
	}

	/** The tarns of the Tatras are the mountain tarn biome (and the biome occurs only there). */
	@ParameterizedTest
	@EnumSource(Scale.class)
	void tarnsAreMountainTarns(Scale sc) {
		LandscapeModel m = new LandscapeModel(SEED_A, sc.scale, 1.0, true);
		HabitatClassifier k = new HabitatClassifier(SEED_A, m, HabitatClassifier.Mode.NATURAL);
		LandscapeModel.Tatra near = sc.nearest(m);
		double r = near.reach();
		List<LandscapeModel.TatraTarn> tarns = m.tatraTarns(near.x() - r, near.z() - r, near.x() + r, near.z() + r);
		assertTrue(tarns.size() >= 3, "tarns: " + tarns.size());
		int tarn = 0;
		for (LandscapeModel.TatraTarn t : tarns) {
			ColumnSample s = m.sample(t.x(), t.z());
			if (s.hasWater() && Habitat.biome(k.classify(s, t.x(), t.z())) == HabitatBiome.MOUNTAIN_TARN) {
				tarn++;
			}
		}
		System.out.printf(Locale.ROOT, "%s: tarns %d, the mountain tarn biome at the center of %d%n", sc.scale.id(), tarns.size(), tarn);
		assertTrue(tarn >= 0.9 * tarns.size(), "tarns with the mountain tarn biome: " + tarn + " of " + tarns.size());
		assertTrue(tatraOnlyBiomes(), "tatra-only biomes");
	}

	private static boolean tatraOnlyBiomes() {
		return pl.polishforests.command.HabitatTargets.tatraOnly(HabitatBiome.SUBNIVAL_ROCKS)
				&& pl.polishforests.command.HabitatTargets.tatraOnly(HabitatBiome.MOUNTAIN_TARN)
				&& !pl.polishforests.command.HabitatTargets.tatraOnly(HabitatBiome.ALPINE_GRASSLAND);
	}

	/** Columns (counted, matching) per band in the box of the massif: every land column of the Tatras proper. */
	static AtomicLongArray shares(LandscapeModel m, HabitatClassifier k, LandscapeModel.Tatra t, double step) {
		Band[] bands = Band.values();
		AtomicLongArray l = new AtomicLongArray(2 * bands.length);
		double half = 1.2 * t.halfLength();
		int n = (int) (2 * half / step);
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = t.x() - half + (i + 0.5) * step;
				double z = t.z() - half + (j + 0.5) * step;
				ColumnSample s = m.sample(x, z);
				if (s.hasWater() || s.terrain().wTatra() < 0.9 || s.surface() < bands[0].from) {
					continue;
				}
				Band band = null;
				for (Band b : bands) {
					if (s.surface() >= b.from && s.surface() < b.to) {
						band = b;
					}
				}
				if (band == null) {
					continue;
				}
				HabitatClassifier.Column col = new HabitatClassifier.Column(k, s, x, z);
				if (col.onValleyFloor()) {
					continue;
				}
				int code = k.classify(s, x, z);
				HabitatBiome b = Habitat.biome(code);
				if (b.isWater() || waterside(Habitat.zone(code))) {
					continue;
				}
				l.incrementAndGet(2 * band.ordinal());
				if (band.counts(code)) {
					l.incrementAndGet(2 * band.ordinal() + 1);
				}
			}
		});
		return l;
	}

	/** Zones of the waters and their banks (channel up to the shore alders). */
	static boolean waterside(Zone z) {
		return z.ordinal() >= Zone.CHANNEL.ordinal() && z.ordinal() <= Zone.SHORE_ALDERS.ordinal();
	}
}
