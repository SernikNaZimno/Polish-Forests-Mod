package pl.polishforests.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import pl.polishforests.entity.jay.EurasianJay;
import pl.polishforests.entity.jay.JayFlight;
import pl.polishforests.entity.jay.JayGeometry;

/**
 * Step FA6 (docs/04-pionowy-wycinek.md "Projekt FA6"): the geometry of the Eurasian jay's flight without a world (the
 * cruise band under the canopy, the under-canopy test of the fauna game test, the sweep points, the steering); the flight
 * in the world is the fauna game test's ({@code JayTrials}).
 */
class JayGeometryTest {
	private static final float H = EurasianJay.PROFILE.height();

	@Test
	void cruiseBandLiesUnderTheCanopy() {
		// a forest with its canopy 20 blocks over the ground: from 1.5 over the ground to the cruise ceiling of 12
		assertTrue(JayGeometry.hasBand(64, 84, H));
		assertEquals(65.5, JayGeometry.cruiseLow(64, 84, H), 1e-9);
		assertEquals(76.0, JayGeometry.cruiseHigh(64, 84, H), 1e-9);
		// a lower forest (canopy 8 over the ground): the top of the hitbox 0.8 under the canopy
		assertEquals(64 + 8 - JayGeometry.UNDER_CANOPY - H, JayGeometry.cruiseHigh(64, 72, H), 1e-6);
		assertTrue(JayGeometry.underCanopy(JayGeometry.cruiseHigh(64, 72, H), H, 72));
		// every height of a band is under the canopy
		for (int canopy = 68; canopy <= 100; canopy++) {
			double hi = JayGeometry.cruiseHigh(64, canopy, H);
			double lo = JayGeometry.cruiseLow(64, canopy, H);
			assertTrue(lo <= hi, "band at canopy " + canopy);
			assertTrue(JayGeometry.underCanopy(hi, H, canopy), "ceiling at canopy " + canopy);
			assertTrue(JayGeometry.inBand(lo, 64, canopy, H) && JayGeometry.inBand(hi, 64, canopy, H));
			assertFalse(JayGeometry.inBand(hi + 0.1, 64, canopy, H));
			assertFalse(JayGeometry.inBand(lo - 0.1, 64, canopy, H));
		}
		// a gap or a field (canopy under 4 blocks over the ground): no band
		assertFalse(JayGeometry.hasBand(64, 67, H));
		assertTrue(Double.isNaN(JayGeometry.cruiseLow(64, 66, H)));
		assertFalse(JayGeometry.inBand(65.5, 64, 66, H));
	}

	@Test
	void underCanopyIsTheTopOfTheHitbox() {
		assertTrue(JayGeometry.underCanopy(70.0, H, 70.3F + 1e-4));
		assertTrue(JayGeometry.underCanopy(69.69, H, 70));
		assertFalse(JayGeometry.underCanopy(69.8, H, 70));
		assertFalse(JayGeometry.underCanopy(75, H, 70));
	}

	@Test
	void plannedCeilingIsStricterThanTheMeasure() {
		// stage 2 (FA6-q): a planned point needs the top of its hitbox 0.3 under the local canopy top
		assertTrue(JayGeometry.plannedUnder(70 - H - JayGeometry.CEILING_MARGIN, H, 70));
		assertFalse(JayGeometry.plannedUnder(70 - H - JayGeometry.CEILING_MARGIN + 0.01, H, 70));
		// below the band (a take-off from the ground, a landing on it) is not over the ceiling
		assertTrue(JayGeometry.plannedUnder(64, H, 72));
		// every planned point is under the canopy by the measure of the game test
		for (double y = 60; y <= 80; y += 0.05) {
			if (JayGeometry.plannedUnder(y, H, 72)) {
				assertTrue(JayGeometry.underCanopy(y, H, 72), "y " + y);
			}
		}
		// the share of a leg over the ceiling stays under the measured threshold of the game test (90% under)
		assertTrue(JayGeometry.ABOVE_SHARE <= 0.1);
	}

	@Test
	void sweepPointsCoverTheSegment() {
		Vec3 a = new Vec3(0, 70, 0);
		Vec3 b = new Vec3(3, 70, 4);   // 5 blocks
		int n = JayGeometry.sweepPoints(a.distanceTo(b));
		assertEquals(21, n);
		assertEquals(a, JayGeometry.sweepPoint(a, b, 0, n));
		assertEquals(b, JayGeometry.sweepPoint(a, b, n - 1, n));
		for (int k = 1; k < n; k++) {
			double step = JayGeometry.sweepPoint(a, b, k, n).distanceTo(JayGeometry.sweepPoint(a, b, k - 1, n));
			assertTrue(step <= JayGeometry.SWEEP_STEP + 1e-9, "step " + step);
		}
		assertEquals(2, JayGeometry.sweepPoints(0.1));
	}

	@Test
	void steeringAcceleratesAndStopsAtTheWaypoint() {
		Vec3 from = new Vec3(0, 70, 0);
		Vec3 to = new Vec3(10, 70, 0);
		// from a standstill the speed grows by the acceleration per tick, at least the least speed
		Vec3 v = JayGeometry.steer(from, to, 0, JayFlight.CRUISE_SPEED, 0.05);
		assertEquals(0.05, v.length(), 1e-9);
		assertEquals(0, v.y, 1e-12);
		assertTrue(v.x > 0);
		v = JayGeometry.steer(from, to, 0.2, JayFlight.CRUISE_SPEED, 0.05);
		assertEquals(0.25, v.length(), 1e-9);
		v = JayGeometry.steer(from, to, 0.3, JayFlight.CRUISE_SPEED, 0.05);
		assertEquals(0.3, v.length(), 1e-9);
		// slowing from the flight speed to the cruise speed by the acceleration
		v = JayGeometry.steer(from, to, JayFlight.FLEE_SPEED, JayFlight.CRUISE_SPEED, 0.05);
		assertEquals(JayFlight.FLEE_SPEED - 0.05, v.length(), 1e-9);
		// it does not overshoot: the last tick ends on the waypoint
		v = JayGeometry.steer(new Vec3(9.9, 70, 0), to, 0.3, 0.3, 0.05);
		assertEquals(0.1, v.length(), 1e-9);
		assertEquals(Vec3.ZERO, JayGeometry.steer(to, to, 0.3, 0.3, 0.05));
	}

	@Test
	void glideAndYaw() {
		assertTrue(JayGeometry.glides(new Vec3(0.3, -0.1, 0), 0.25));
		assertFalse(JayGeometry.glides(new Vec3(0.3, -0.05, 0), 0.25));
		assertFalse(JayGeometry.glides(new Vec3(0.3, 0.1, 0), 0.25));
		assertFalse(JayGeometry.glides(new Vec3(0, -0.3, 0), 0.25));
		// Minecraft yaw: 0 faces south (+Z), 90 west (-X), -90 east (+X), 180 north
		assertEquals(0, JayGeometry.yawOf(0, 1), 1e-4);
		assertEquals(90, JayGeometry.yawOf(-1, 0), 1e-4);
		assertEquals(-90, JayGeometry.yawOf(1, 0), 1e-4);
		assertEquals(180, Math.abs(JayGeometry.yawOf(0, -1)), 1e-4);
	}
}
