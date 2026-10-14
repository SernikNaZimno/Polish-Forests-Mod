package pl.polishforests.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * S9 review round 1: {@link GridSearch} and the cancellation of the searches. A search cancelled after its first hit
 * keeps the hit (it used to return null, and the command reported "gave up after 60 s" for a place it had found), a
 * search cancelled before any hit returns null, an uncancelled one returns the nearest hit, and the {@code Deadline} of
 * the commands remembers that it fired.
 */
class GridSearchTest {
	private static final double CELL = 100;
	private static final List<GridSearch.Phase> PHASES = List.of(new GridSearch.Phase(10, 5_000));

	/** Hits on the diagonal cell around (1 000, 1 000) and on the farther one around (-1 500, 0). */
	private static double[] probe(double[] p) {
		boolean diagonal = Math.abs(p[0] - 1_000) < 6 && Math.abs(p[1] - 1_000) < 6;
		boolean far = Math.abs(p[0] + 1_500) < 6 && Math.abs(p[1]) < 6;
		return diagonal || far ? new double[] {p[0], p[1]} : null;
	}

	@Test
	void findsTheNearestHit() {
		double[] hit = GridSearch.nearest(CELL, PHASES, 0, GridSearchTest::probe, 0, 0, () -> false);
		assertNotNull(hit);
		assertTrue(Math.abs(hit[0] - 1_000) < 6 && Math.abs(hit[1] - 1_000) < 6, "nearest hit: " + hit[0] + ", " + hit[1]);
	}

	@Test
	void cancelledAfterTheFirstHitKeepsIt() {
		double[] expected = GridSearch.nearest(CELL, PHASES, 0, GridSearchTest::probe, 0, 0, () -> false);
		// The diagonal hit is first seen in ring ~10; confirming it needs the rings up to ~15. Cancel right after the hit.
		AtomicBoolean hitSeen = new AtomicBoolean();
		double[] hit = GridSearch.nearest(CELL, PHASES, 0, p -> {
			double[] h = probe(p);
			if (h != null) {
				hitSeen.set(true);
			}
			return h;
		}, 0, 0, hitSeen::get);
		assertArrayEquals(expected, hit, "a hit found before the cancellation must be kept");
	}

	@Test
	void cancelledBeforeAnyHitReturnsNull() {
		assertNull(GridSearch.nearest(CELL, PHASES, 0, GridSearchTest::probe, 0, 0, () -> true));
	}

	@Test
	void deadlineRemembersThatItFired() {
		AtomicBoolean stopped = new AtomicBoolean();
		PolishForestsCommands.Deadline d = new PolishForestsCommands.Deadline(60_000, stopped::get);
		assertTrue(!d.getAsBoolean() && !d.fired());
		stopped.set(true);
		assertTrue(d.getAsBoolean() && d.fired());
		stopped.set(false);
		assertTrue(d.getAsBoolean() && d.fired(), "a fired deadline stays fired");
		BooleanSupplier expired = new PolishForestsCommands.Deadline(-1, () -> false);
		assertTrue(expired.getAsBoolean());
	}

	@Test
	void ringSearchStopsWhenCancelled() {
		double[][] phases = {{10, 100_000}};
		// Without the cancellation the whole radius would be searched (no hit): over 10^8 points.
		long t0 = System.nanoTime();
		assertNull(PolishForestsCommands.search(phases, p -> false, 0, 0, () -> System.nanoTime() - t0 > 200_000_000L));
		assertTrue(System.nanoTime() - t0 < 10_000_000_000L, "the ring search did not stop");
	}
}
