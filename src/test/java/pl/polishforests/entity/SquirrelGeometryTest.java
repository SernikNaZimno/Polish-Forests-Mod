package pl.polishforests.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.GameRegistries;
import pl.polishforests.entity.base.AnimalProfile.Activity;
import pl.polishforests.entity.base.AnimalProfile.Period;
import pl.polishforests.entity.squirrel.RedSquirrel;
import pl.polishforests.entity.squirrel.TreeGeometry;
import pl.polishforests.season.SubSeason;

/**
 * Step FA5 (docs/04-pionowy-wycinek.md "Projekt FA5"): the geometry of the red squirrel's tree mode without a world (where
 * it clings to a trunk, which way it faces, its leaps) and its daily schedules; the behaviour in the world is the fauna
 * game test's ({@code SquirrelTrials}).
 */
class SquirrelGeometryTest {
	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void clingingKeepsTheHitboxJustOffTheBark() {
		for (int r = 1; r <= 7; r++) {
			double half = TreeGeometry.trunkHalf(r);
			assertEquals(r / 16.0, half, 1e-12);
			// the side of the hitbox towards the trunk is BARK_GAP from the bark
			assertEquals(TreeGeometry.BARK_GAP, TreeGeometry.clingDistance(half) - TreeGeometry.HALF_WIDTH - half, 1e-12);
		}
		assertEquals(0.72, TreeGeometry.clingDistance(TreeGeometry.VANILLA_LOG_HALF), 1e-12);
		// The bark plane of the climbing animations (tools/fauna/animations.py CLIMB_PLANE 6.4 model px at the render scale 0.5)
		// is the half width of the hitbox: the drawn paws are on the drawn bark.
		assertEquals(TreeGeometry.HALF_WIDTH, 6.4 * 0.5 / 16.0, 1e-12);
		assertEquals(RedSquirrel.PROFILE.width() / 2.0, TreeGeometry.HALF_WIDTH, 1e-6);
		// The four sides of a trunk of radius 4 at the column (10, -3).
		double[] north = TreeGeometry.clingXZ(10, -3, Direction.NORTH, 0.25);
		assertEquals(10.5, north[0], 1e-12);
		assertEquals(-2.5 - 0.47, north[1], 1e-12);
		double[] east = TreeGeometry.clingXZ(10, -3, Direction.EAST, 0.25);
		assertEquals(10.97, east[0], 1e-12);
		assertEquals(-2.5, east[1], 1e-12);
	}

	@Test
	void facingTheTrunkUpAndAwayFromItDown() {
		// Minecraft yaw: 0 faces +z (south), 90 faces -x (west).
		assertEquals(0.0F, TreeGeometry.yawFacing(0, 1), 1e-4F);
		assertEquals(90.0F, TreeGeometry.yawFacing(-1, 0), 1e-4F);
		// On the north side of a trunk it climbs facing south (the trunk), and goes down facing north (away from it).
		assertEquals(0.0F, TreeGeometry.climbYaw(Direction.NORTH), 1e-4F);
		assertEquals(180.0F, Math.abs(TreeGeometry.descendYaw(Direction.NORTH)), 1e-4F);
		assertEquals(90.0F, TreeGeometry.climbYaw(Direction.EAST), 1e-4F);
		assertEquals(-90.0F, TreeGeometry.descendYaw(Direction.EAST), 1e-4F);
		assertEquals(Direction.EAST, TreeGeometry.sideTowards(0, 0, 3.0, 0.9));
		assertEquals(Direction.NORTH, TreeGeometry.sideTowards(0, 0, 0.2, -4.0));
		assertEquals(Direction.WEST, TreeGeometry.sideTowards(5, 5, 1.0, 5.5));
		assertEquals(Direction.SOUTH, TreeGeometry.sideTowards(5, 5, 5.4, 9.0));
	}

	@Test
	void leapsAreShortAndArched() {
		assertTrue(TreeGeometry.leapable(2.0, 0));
		assertTrue(TreeGeometry.leapable(4.0, -3.0));
		assertTrue(TreeGeometry.leapable(1.0, 1.0));
		assertFalse(TreeGeometry.leapable(0.9, 0));
		assertFalse(TreeGeometry.leapable(4.1, 0));
		assertFalse(TreeGeometry.leapable(2.0, -3.1));
		assertFalse(TreeGeometry.leapable(2.0, 1.1));
		Vec3 a = new Vec3(0, 10, 0);
		Vec3 b = new Vec3(3, 9, 0);
		double h = TreeGeometry.arcHeight(3, -1);
		assertTrue(h > 0.5 && h < 1.5, "arc " + h);
		assertEquals(a, TreeGeometry.arc(a, b, 0, h));
		assertEquals(b.x, TreeGeometry.arc(a, b, 1, h).x, 1e-12);
		assertEquals(b.y, TreeGeometry.arc(a, b, 1, h).y, 1e-12);
		// the middle of the arc is the height above the middle of the straight line
		assertEquals(9.5 + h, TreeGeometry.arc(a, b, 0.5, h).y, 1e-12);
		// a rising leap arches higher than a level one
		assertTrue(TreeGeometry.arcHeight(3, 1) > TreeGeometry.arcHeight(3, 0));
		assertEquals(TreeGeometry.MIN_LEAP_TICKS, TreeGeometry.leapTicks(1.0));
		assertEquals((int) Math.ceil(4.0 / TreeGeometry.LEAP_SPEED), TreeGeometry.leapTicks(4.0));
		assertEquals(5.0, TreeGeometry.horizontal(new Vec3(1, 7, 1), new Vec3(4, -2, 5)), 1e-12);
	}

	/** Ticks of the day whose main activity is not resting. */
	private static int activeTicks(List<Period> schedule) {
		int active = 0;
		for (int t = 0; t < 24_000; t += 100) {
			Period p = schedule.getFirst();
			for (Period q : schedule) {
				if (q.fromTick() <= t) {
					p = q;
				}
			}
			if (p.main() != Activity.REST) {
				active += 100;
			}
		}
		return active;
	}

	@Test
	void diurnalWithAShortWinterDay() {
		List<Period> summer = RedSquirrel.schedule(SubSeason.MID_SUMMER);
		List<Period> winter = RedSquirrel.schedule(SubSeason.MID_WINTER);
		assertEquals(RedSquirrel.PROFILE.schedule(), summer);
		assertEquals(RedSquirrel.WINTER_SCHEDULE, winter);
		assertEquals(summer, RedSquirrel.schedule(null));
		// Active by day, in the drey at night, in both seasons; the winter day is shorter.
		assertEquals(Activity.FEED, RedSquirrel.PROFILE.activity(1_000));
		assertEquals(Activity.FEED, RedSquirrel.PROFILE.activity(9_000));
		assertEquals(Activity.REST, RedSquirrel.PROFILE.activity(18_000));
		int summerActive = activeTicks(summer);
		int winterActive = activeTicks(winter);
		assertTrue(winterActive < summerActive, "winter " + winterActive + " summer " + summerActive);
		assertTrue(winterActive >= 4_000, "winter " + winterActive);
		for (int t : new int[] {13_000, 18_000, 23_000}) {
			Period night = winter.getFirst();
			for (Period q : winter) {
				if (q.fromTick() <= t) {
					night = q;
				}
			}
			assertEquals(Activity.REST, night.main(), "winter at " + t);
		}
	}
}
