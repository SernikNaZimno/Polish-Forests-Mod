package pl.polishforests.worldgen.chunk;

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

class PolandDimensionTest {
	@Test
	void lowlandsAndUplandsAreExactlyOneToOne() {
		for (int m = -120; m <= 880; m++) {
			assertEquals(m, PolandDimension.blocksForMeters(m), 0.0);
			assertEquals(PolandDimension.SEA_LEVEL_Y + m - 1, PolandDimension.topBlockY(m));
		}
	}

	@Test
	void rysyFitUnderBuildLimitWithHeadroom() {
		int rysy = PolandDimension.topBlockY(2_499);
		assertTrue(rysy >= 1_990 && rysy <= 2_001, "Rysy na Y " + rysy);
		assertTrue(PolandDimension.maxY() - rysy >= 25, "za mało miejsca nad Rysami");
		assertEquals(2_031, PolandDimension.maxY());
	}

	@Test
	void mappingIsContinuousAndIncreasing() {
		double prev = PolandDimension.blocksForMeters(0);
		for (double m = 0.05; m <= 2_700; m += 0.05) {
			double b = PolandDimension.blocksForMeters(m);
			assertTrue(b > prev, "nie rośnie przy " + m);
			assertTrue(b - prev < 0.051, "skok przy " + m);
			prev = b;
		}
	}

	@Test
	void inverseMatches() {
		for (double m = 0; m <= 2_600; m += 7.3) {
			assertEquals(m, PolandDimension.metersForBlocks(PolandDimension.blocksForMeters(m)), 1e-6);
		}
		assertEquals(100.0, PolandDimension.metersAboveSea(PolandDimension.topBlockY(100)), 1e-9);
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
	void isPolandRecognizesBothScales() {
		LevelHeightAccessor real = LevelHeightAccessor.create(-64, 2_096);
		LevelHeightAccessor gameplay = LevelHeightAccessor.create(-64, 832);
		assertTrue(PolandDimension.isPoland(real));
		assertTrue(PolandDimension.isPoland(gameplay));
		assertSame(VerticalScale.REAL, PolandDimension.scaleOf(real));
		assertSame(VerticalScale.GAMEPLAY, PolandDimension.scaleOf(gameplay));
		// Wanilijny overworld, Nether i End.
		assertFalse(PolandDimension.isPoland(LevelHeightAccessor.create(-64, 384)));
		assertFalse(PolandDimension.isPoland(LevelHeightAccessor.create(0, 256)));
		assertNull(PolandDimension.scaleOf(LevelHeightAccessor.create(0, 832)));
		for (PolandScale scale : PolandScale.values()) {
			VerticalScale v = scale.vertical();
			assertSame(v, PolandDimension.scaleOf(LevelHeightAccessor.create(v.minY(), v.height())));
		}
	}

	@Test
	void scaleFromDimensionType() {
		for (PolandScale scale : PolandScale.values()) {
			assertSame(scale, PolandScale.byDimensionType(scale.dimensionType()));
		}
		assertEquals("polishforests:poland", PolandScale.REALISTIC.dimensionType().identifier().toString());
		assertEquals("polishforests:poland_gameplay", PolandScale.GAMEPLAY.dimensionType().identifier().toString());
		assertNull(PolandScale.byDimensionType(
				ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.withDefaultNamespace("overworld"))));
		assertNull(PolandScale.byDimensionType(
				ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.withDefaultNamespace("the_nether"))));
	}
}
