package pl.polishforests.gametest.probe;

import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import pl.polishforests.PolishForests;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;

/**
 * The phase of the year on the client for the render probes (step V3): the color step, the trial color curve of the probe
 * leaves and the phases of the wrapped models. The year comes from the season provider of the mod (Serene Seasons or the
 * built-in calendar, {@link Seasons}), so the probes change with and without SS. A change of the color step rebuilds every
 * section mesh in view, by default by marking the sections dirty ({@link #markAllDirty}); {@link #fullRebuild} switches to
 * {@code levelExtractor.allChanged()}, as SS does on a change of the sub-season.
 *
 * <p>The color of the probe leaves is computed from the <b>raw</b> foliage color of the biome, read through the own color
 * resolver {@link #RAW_FOLIAGE} (registered in Fabric's {@code ColorResolverRegistry}): it keeps the biome blend, and SS,
 * which replaces only the vanilla foliage resolver, does not season it a second time (decision B6 (a)).
 */
public final class ProbeSeason {
	/** Color steps per sub-season (docs/04 V7: 2-3) and per year. */
	public static final int STEPS_PER_SUB_SEASON = 3;
	public static final int STEPS = 12 * STEPS_PER_SUB_SEASON;

	/** The raw foliage color of the biome, without the seasonal overlay of Serene Seasons. */
	public static final ColorResolver RAW_FOLIAGE = (biome, x, z) -> biome.getFoliageColor();

	/**
	 * The trial curve: per sub-season (from EARLY_SPRING) a target color and the weight of the target over the biome color.
	 * Young light green leaves in spring, the biome color in summer, yellow, orange and brown in autumn; winter keeps the
	 * brown of late autumn (the probe is leafless then). Between the sub-seasons the curve moves in steps of a third.
	 */
	private static final int[] TARGET = {0xB5D86A, 0xB5D86A, 0xB5D86A, 0, 0, 0, 0xD8C040, 0xE0A030, 0x9A6A35, 0x9A6A35, 0x9A6A35,
			0x9A6A35};
	private static final double[] WEIGHT = {0.6, 0.6, 0.3, 0, 0, 0, 0.4, 0.8, 0.85, 0.85, 0.85, 0.85};

	/**
	 * How a change of the color step reaches the meshes: false (default) marks every section within the render distance
	 * dirty ({@code setSectionRangeDirty}), so the renderer rebuilds them while the old meshes stay visible; true drops
	 * and rebuilds everything ({@code levelExtractor.allChanged()}, what Serene Seasons does on a sub-season change).
	 */
	public static volatile boolean fullRebuild;

	private static volatile int step = -1;
	private static volatile int rebuilds;
	private static volatile long lastRebuildNanos;

	private ProbeSeason() {
	}

	/** The current color step, 0 ... {@value #STEPS} - 1 (start of EARLY_SPRING = 0), or -1 before the first client tick. */
	public static int step() {
		return step;
	}

	public static SubSeason subSeason() {
		return SubSeason.byOrdinal(Math.max(0, step) / STEPS_PER_SUB_SEASON);
	}

	/** Mesh rebuilds started by a change of the color step since the start of the game. */
	public static int rebuilds() {
		return rebuilds;
	}

	public static long lastRebuildNanos() {
		return lastRebuildNanos;
	}

	/** The color step of a level now. */
	public static int stepOf(Level level) {
		double progress = Seasons.provider().yearProgress(level);
		return Math.clamp((int) Math.floor(progress * STEPS), 0, STEPS - 1);
	}

	/** Leafless phase of the probe leaves (twig model): EARLY_WINTER ... EARLY_SPRING. */
	public static boolean leafless() {
		return leafless(subSeason());
	}

	public static boolean leafless(SubSeason s) {
		return s.ordinal() >= SubSeason.EARLY_WINTER.ordinal() || s == SubSeason.EARLY_SPRING;
	}

	/** "Empty" phase of the probe plant (a spring geophyte): everything outside EARLY_SPRING ... LATE_SPRING. */
	public static boolean plantEmpty() {
		return plantEmpty(subSeason());
	}

	public static boolean plantEmpty(SubSeason s) {
		return s.ordinal() > SubSeason.LATE_SPRING.ordinal();
	}

	/** The color of the probe leaves at a step over a raw biome foliage color (ARGB, opaque). */
	public static int color(int step, int biome) {
		int s = Math.floorMod(step, STEPS);
		int sub = s / STEPS_PER_SUB_SEASON;
		int next = (sub + 1) % 12;
		double f = (double) (s % STEPS_PER_SUB_SEASON) / STEPS_PER_SUB_SEASON;
		int a = blend(biome, TARGET[sub], WEIGHT[sub]);
		int b = blend(biome, TARGET[next], WEIGHT[next]);
		return 0xFF000000 | lerp(a, b, f);
	}

	private static int blend(int biome, int target, double weight) {
		return lerp(biome & 0xFFFFFF, target, weight);
	}

	private static int lerp(int a, int b, double f) {
		int r = (int) Math.round(ARGB.red(a) + (ARGB.red(b) - ARGB.red(a)) * f);
		int g = (int) Math.round(ARGB.green(a) + (ARGB.green(b) - ARGB.green(a)) * f);
		int bl = (int) Math.round(ARGB.blue(a) + (ARGB.blue(b) - ARGB.blue(a)) * f);
		return (r << 16) | (g << 8) | bl;
	}

	/** Marks every section within the render distance around the camera dirty: rebuilt in the background, old meshes kept. */
	public static void markAllDirty(Minecraft mc) {
		int rd = mc.options.getEffectiveRenderDistance() + 1;
		SectionPos c = SectionPos.of(mc.gameRenderer.mainCamera().position());
		mc.levelExtractor.setSectionRangeDirty(c.x() - rd, mc.level.getMinSectionY(), c.z() - rd, c.x() + rd, mc.level.getMaxSectionY(),
				c.z() + rd);
	}

	/** The section of the camera now (the center of a wave rebuild). */
	public static SectionPos cameraSection(Minecraft mc) {
		return SectionPos.of(mc.gameRenderer.mainCamera().position());
	}

	/**
	 * Marks the sections of the chunk columns at Chebyshev distance {@code ring} from {@code center} dirty (the wave rebuild
	 * of the V3 measurement: rings one after another spread the compile and upload over several frames). The center is
	 * taken once at the start of the wave (review round 1): with a center that followed the camera, a player crossing a
	 * section border during the wave would leave some loaded sections never marked, with the old color or phase.
	 */
	public static void markRingDirty(Minecraft mc, SectionPos center, int ring) {
		int minY = mc.level.getMinSectionY();
		int maxY = mc.level.getMaxSectionY();
		for (int dx = -ring; dx <= ring; dx++) {
			for (int dz = -ring; dz <= ring; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
					mc.levelExtractor.setSectionRangeDirty(center.x() + dx, minY, center.z() + dz, center.x() + dx, maxY,
							center.z() + dz);
				}
			}
		}
	}

	/**
	 * Client tick: on a change of the color step (also of the phases, which change only with the sub-season) every section
	 * mesh is rebuilt. Without a level nothing changes.
	 */
	static void tick(Minecraft mc) {
		if (mc.level == null) {
			return;
		}
		int now = stepOf(mc.level);
		if (now != step) {
			int old = step;
			step = now;
			if (old >= 0) {
				rebuilds++;
				lastRebuildNanos = System.nanoTime();
				if (fullRebuild) {
					mc.levelExtractor.allChanged();
				} else {
					markAllDirty(mc);
				}
				PolishForests.LOG.info("[probes] color step {} -> {} ({}), section meshes rebuilt ({})", old, now, subSeason(),
						fullRebuild ? "allChanged" : "sections marked dirty");
			}
		}
	}
}
