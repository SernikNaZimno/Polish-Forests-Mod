package pl.polishforests.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** The plain-Java parts of the screenshot wait of step T7: the rays of the view cone, its reach and the frame comparison. */
class ViewReadyMathTest {
	private static final double EPS = 1e-9;

	@Test
	void centralRayLooksWhereTheCameraLooks() {
		// yaw 0 looks south (+z), yaw 90 west (-x), pitch 90 straight down.
		assertVector(new double[] {0, 0, 1}, center(0, 0));
		assertVector(new double[] {-1, 0, 0}, center(90, 0));
		assertVector(new double[] {0, -1, 0}, center(37, 90));
		double s = Math.sqrt(0.5);
		assertVector(new double[] {0, -s, s}, center(0, 45));
	}

	@Test
	void raysSpanTheFieldOfViewWithTheMargin() {
		double fov = 70;
		double aspect = 16 / 9.0;
		double[][] rays = ViewCone.rays(30, 10, fov, aspect, 5, 3, 1.0);
		assertEquals(15, rays.length);
		double[] f = center(30, 10);
		// Middle of the top and bottom rows: half the vertical field of view from the forward direction.
		assertEquals(fov / 2, angle(rays[2], f), 1e-6);
		assertEquals(fov / 2, angle(rays[12], f), 1e-6);
		// Middle of the left and right columns: half the horizontal field of view.
		double half = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(fov / 2)) * aspect));
		assertEquals(half, angle(rays[5], f), 1e-6);
		assertEquals(half, angle(rays[9], f), 1e-6);
		// The top row is above the forward direction (more y), the bottom row below.
		assertTrue(rays[12][1] > f[1] && rays[2][1] < f[1] || rays[2][1] > f[1] && rays[12][1] < f[1]);
		// A margin widens the cone.
		double[][] wide = ViewCone.rays(30, 10, fov, aspect, 5, 3, 1.1);
		assertTrue(angle(wide[2], f) > fov / 2);
		for (double[] r : wide) {
			assertEquals(1, Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]), EPS);
		}
	}

	@Test
	void raysStayFiniteLookingStraightUpOrDown() {
		for (double pitch : new double[] {-90, 90}) {
			for (double[] r : ViewCone.rays(123, pitch, 70, 16 / 9.0, 24, 14, 1.1)) {
				for (double v : r) {
					assertTrue(Double.isFinite(v));
				}
			}
		}
	}

	@Test
	void reachGrowsWithHeightAndStopsInsideTheRenderDistance() {
		assertEquals(ViewCone.EYE_REACH, ViewCone.reach(1.6, 12, 0), EPS);
		assertEquals(ViewCone.EYE_REACH, ViewCone.reach(-5, 12, 0), EPS);
		assertEquals(ViewCone.EYE_REACH + 56, ViewCone.reach(30, 16, 0), EPS);
		assertEquals((12 - ViewCone.EDGE_CHUNKS) * 16, ViewCone.reach(400, 12, 0), EPS);
		assertEquals((6 - ViewCone.EDGE_CHUNKS) * 16, ViewCone.reach(1.6, 6, 0), EPS);
		assertEquals(80, ViewCone.reach(400, 12, 80), EPS);
		assertEquals((12 - ViewCone.EDGE_CHUNKS) * 16, ViewCone.horizontalLimit(12), EPS);
	}

	@Test
	void equalFramesAreEqual() {
		BufferedImage a = image(160, 90, 0x556633);
		FrameDiff.Result r = FrameDiff.compare(a, image(160, 90, 0x556633));
		assertEquals(0, r.diffShare(), EPS);
		assertEquals(0, r.tiles());
		assertTrue(r.equal());
	}

	@Test
	void scatteredSmallDifferencesStayEqual() {
		BufferedImage a = image(160, 90, 0x556633);
		BufferedImage b = image(160, 90, 0x556633);
		// A few particles: 40 single pixels.
		for (int k = 0; k < 40; k++) {
			b.setRGB((k * 37) % 160, (k * 11) % 90, 0xFFFFFF);
		}
		FrameDiff.Result r = FrameDiff.compare(a, b);
		assertTrue(r.diffShare() > 0);
		assertEquals(0, r.tiles());
		assertTrue(r.equal(), r.describe());
	}

	@Test
	void skyInPlaceOfTerrainIsAHole() {
		BufferedImage reference = image(160, 90, 0x556633);
		BufferedImage frame = image(160, 90, 0x556633);
		// A missing section: a 20 × 20 block of sky in the lower half.
		for (int x = 60; x < 80; x++) {
			for (int y = 50; y < 70; y++) {
				frame.setRGB(x, y, 0x78A7FF);
			}
		}
		FrameDiff.Result r = FrameDiff.compare(frame, reference);
		assertEquals(400 / (160.0 * 90), r.holeShare(), EPS);
		assertTrue(r.tiles() >= 2);
		assertFalse(r.equal(), r.describe());
		// The other way round (terrain where the frame has sky) differs too, but is no hole.
		assertEquals(0, FrameDiff.compare(reference, frame).holeShare(), EPS);
	}

	@Test
	void framesOfDifferentSizeDiffer() {
		assertFalse(FrameDiff.compare(image(16, 9, 0), image(32, 18, 0)).equal());
	}

	private static double[] center(double yaw, double pitch) {
		return ViewCone.rays(yaw, pitch, 70, 1.5, 1, 1, 1.0)[0];
	}

	private static double angle(double[] a, double[] b) {
		double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
	}

	private static void assertVector(double[] expected, double[] actual) {
		for (int i = 0; i < 3; i++) {
			assertEquals(expected[i], actual[i], 1e-9);
		}
	}

	private static BufferedImage image(int w, int h, int rgb) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setRGB(x, y, rgb);
			}
		}
		return img;
	}
}
