package pl.polishforests.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;

/**
 * The fast path of the fill (step S10): a section that {@link ChunkSurface#uniformRock} reports as one rock kind has that
 * block in every column and height of {@link ChunkSurface#material}, and the fast path covers most deep sections of chunks
 * without flysch.
 */
class UniformRockTest {
	/** Chunks of the lowland, the Beskids (flysch) and the large river of the stage measurement, both scales. */
	private static final int[][] REAL = {{-40_000, 25_000}, {154_834, 1_058_738}, {-19_484, 11_253}};
	private static final int[][] GAMEPLAY = {{-40_000, 25_000}, {27_609, 3_254}, {-1_851, 6_022}};

	@Test
	void uniformSectionsMatchTheMaterials() {
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture f = new SurfaceFixture(scale, true);
			int uniform = 0;
			int deep = 0;
			for (int[] p : scale == PolandScale.REALISTIC ? REAL : GAMEPLAY) {
				for (int k = 0; k < 4; k++) {
					ChunkSurface s = f.chunk((p[0] >> 4) + k, p[1] >> 4).surface();
					int[] bounds = s.rockBounds();
					for (int y0 = f.minY(); y0 + 15 <= f.maxY(); y0 += 16) {
						Material m = ChunkSurface.uniformRock(y0, bounds);
						boolean counted = bounds[4] == 0 && y0 + 15 < bounds[1] - 16;
						if (counted) {
							deep++;
						}
						if (m == null) {
							continue;
						}
						if (counted) {
							uniform++;
						}
						for (int i = 0; i < 256; i++) {
							for (int y = y0; y <= y0 + 15; y++) {
								assertEquals(m, s.material(i, y), scale + " chunk " + k + " column " + i + " y " + y);
							}
						}
					}
				}
			}
			// The deep sections (more than a section below the lowest rock top) of chunks without flysch (its beds are never
			// uniform) are mostly covered by the fast path; the rest are the bedrock section and the 1-2 sections of the deepslate
			// limit (it varies by 0-7 blocks between columns).
			assertTrue(uniform >= 0.6 * deep, scale + ": " + uniform + " uniform sections of " + deep + " deep ones");
		}
	}
}
