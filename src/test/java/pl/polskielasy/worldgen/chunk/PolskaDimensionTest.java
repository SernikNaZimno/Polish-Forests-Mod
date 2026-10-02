package pl.polskielasy.worldgen.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.Test;

class PolskaDimensionTest {
	@Test
	void lowlandsAndUplandsAreExactlyOneToOne() {
		for (int m = -120; m <= 880; m++) {
			assertEquals(m, PolskaDimension.blocksForMeters(m), 0.0);
			assertEquals(PolskaDimension.SEA_LEVEL_Y + m - 1, PolskaDimension.topBlockY(m));
		}
	}

	@Test
	void rysyFitUnderBuildLimitWithHeadroom() {
		int rysy = PolskaDimension.topBlockY(2_499);
		assertTrue(rysy >= 1_990 && rysy <= 2_001, "Rysy na Y " + rysy);
		assertTrue(PolskaDimension.maxY() - rysy >= 25, "za mało miejsca nad Rysami");
		assertEquals(2_031, PolskaDimension.maxY());
	}

	@Test
	void mappingIsContinuousAndIncreasing() {
		double prev = PolskaDimension.blocksForMeters(0);
		for (double m = 0.05; m <= 2_700; m += 0.05) {
			double b = PolskaDimension.blocksForMeters(m);
			assertTrue(b > prev, "nie rośnie przy " + m);
			assertTrue(b - prev < 0.051, "skok przy " + m);
			prev = b;
		}
	}

	@Test
	void inverseMatches() {
		for (double m = 0; m <= 2_600; m += 7.3) {
			assertEquals(m, PolskaDimension.metersForBlocks(PolskaDimension.blocksForMeters(m)), 1e-6);
		}
		assertEquals(100.0, PolskaDimension.metersAboveSea(PolskaDimension.topBlockY(100)), 1e-9);
	}

	@Test
	void gameplayScaleFitsItsDimensionAndIsMonotonic() {
		VerticalScale v = VerticalScale.GAMEPLAY;
		int rysy = v.topBlockY(2_499);
		assertTrue(rysy > 600 && rysy < v.maxY() - 40, "Rysy na Y " + rysy);
		assertTrue(v.topBlockY(1_725) > 480 && v.topBlockY(1_725) < 560, "Babia Góra na Y " + v.topBlockY(1_725));
		assertTrue(v.topBlockY(130) > 120 && v.topBlockY(130) < 145, "nizina na Y " + v.topBlockY(130));
		double prev = v.blocksForMeters(-50);
		for (double m = -49.95; m <= 2_700; m += 0.05) {
			double b = v.blocksForMeters(m);
			assertTrue(b > prev, "nie rośnie przy " + m);
			prev = b;
		}
		for (double m = -40; m <= 2_600; m += 3.7) {
			assertEquals(m, v.metersForBlocks(v.blocksForMeters(m)), 1e-6);
		}
		assertEquals(0, v.blocksForMeters(0), 0.0);
	}

	@Test
	void isPolskaRecognizesBothScales() {
		LevelHeightAccessor real = LevelHeightAccessor.create(-64, 2_096);
		LevelHeightAccessor gameplay = LevelHeightAccessor.create(-64, 832);
		assertTrue(PolskaDimension.isPolska(real));
		assertTrue(PolskaDimension.isPolska(gameplay));
		assertSame(VerticalScale.REAL, PolskaDimension.scaleOf(real));
		assertSame(VerticalScale.GAMEPLAY, PolskaDimension.scaleOf(gameplay));
		// Wanilijny overworld, Nether i End.
		assertFalse(PolskaDimension.isPolska(LevelHeightAccessor.create(-64, 384)));
		assertFalse(PolskaDimension.isPolska(LevelHeightAccessor.create(0, 256)));
		assertNull(PolskaDimension.scaleOf(LevelHeightAccessor.create(0, 832)));
		for (PolskaScale scale : PolskaScale.values()) {
			VerticalScale v = scale.vertical();
			assertSame(v, PolskaDimension.scaleOf(LevelHeightAccessor.create(v.minY(), v.height())));
		}
	}

	@Test
	void scaleFromDimensionType() {
		for (PolskaScale scale : PolskaScale.values()) {
			assertSame(scale, PolskaScale.byDimensionType(scale.dimensionType()));
		}
		assertEquals("polskielasy:polska", PolskaScale.REALISTYCZNA.dimensionType().identifier().toString());
		assertEquals("polskielasy:polska_rozgrywka", PolskaScale.ROZGRYWKA.dimensionType().identifier().toString());
		assertNull(PolskaScale.byDimensionType(
				ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.withDefaultNamespace("overworld"))));
		assertNull(PolskaScale.byDimensionType(
				ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.withDefaultNamespace("the_nether"))));
	}
}
