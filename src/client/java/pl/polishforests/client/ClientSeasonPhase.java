package pl.polishforests.client;

import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorResolverRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.SpeciesLeavesBlock;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.season.FoliageCurve;
import pl.polishforests.season.SeasonPhase;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The phase of the year on the client and the species foliage colors (step V7 of docs/04-pionowy-wycinek.md, decisions B6
 * (a) and V3-a): the color step of {@link SeasonPhase} on every client tick, the tint of the species leaves
 * ({@link FoliageCurve} over the raw biome foliage color of the own resolver {@link #RAW_FOLIAGE}, which keeps the biome
 * blend and which Serene Seasons does not season, so nothing is seasoned twice) and, on a change of the step, a rebuild of
 * the section meshes in waves of chunk-column rings from the camera (one ring every {@value #WAVE_TICKS} ticks, the
 * center taken at the start of the wave; the old meshes stay visible until the new ones arrive, no frame drops the view,
 * V3). The frame times during a wave (the last frame of every client tick, {@code Minecraft.getFrameTimeNs()}) go to the
 * log when the wave ends.
 */
public final class ClientSeasonPhase {
	/** The raw foliage color of the biome, without the seasonal overlay of Serene Seasons. */
	public static final ColorResolver RAW_FOLIAGE = (biome, x, z) -> biome.getFoliageColor();
	/** Ticks between two rings of a wave rebuild (V3: 2). */
	static final int WAVE_TICKS = 2;
	/** Frame samples kept after the last ring for the log line. */
	private static final int TAIL_TICKS = 40;

	private static volatile int step = -1;
	private static volatile boolean changes = true;
	private static volatile int rebuilds;
	private static SectionPos waveCenter;
	private static int ring = -1;
	private static int rings;
	private static int waveTick;
	private static long[] frames = new long[0];
	private static int frameCount;
	private static int waveFrom;

	private ClientSeasonPhase() {
	}

	static void register() {
		ColorResolverRegistry.register(RAW_FOLIAGE);
		for (TreeFoliage f : TreeFoliage.values()) {
			BlockColorRegistry.register(List.of(new SpeciesTint(f.species())), ModBlocks.leaves(f));
		}
		ClientTickEvents.END_CLIENT_TICK.register(ClientSeasonPhase::tick);
	}

	/** The color step shown now (−1 before the first client tick in a level). */
	public static int step() {
		return step;
	}

	/** The sub-season of the color step shown now. */
	public static SubSeason subSeason() {
		return SeasonPhase.subSeason(Math.max(0, step));
	}

	/** Mesh rebuilds started by a change of the color step since the start of the game. */
	public static int rebuilds() {
		return rebuilds;
	}

	/** True while a wave rebuild runs (or its frame times are still being recorded). */
	public static boolean rebuilding() {
		return ring >= 0;
	}

	/** The color step the curves use: the season's, or MID_SUMMER when the foliage does not change (SS switch). */
	static int curveStep() {
		int s = step;
		return !changes ? SeasonPhase.firstStep(SubSeason.MID_SUMMER) + 1 : Math.max(0, s);
	}

	static void tick(Minecraft mc) {
		if (mc.level == null) {
			step = -1;
			ring = -1;
			return;
		}
		int now = SeasonPhase.step(mc.level);
		boolean ch = SeasonPhase.foliageChanges();
		if (now != step || ch != changes) {
			int old = step;
			step = now;
			changes = ch;
			if (old >= 0) {
				startWave(mc);
				PolishForests.LOG.info("[seasons] color step {} -> {} ({}{}), section meshes rebuilt in a wave of {} rings", old, now,
						subSeason(), ch ? "" : ", foliage colors off", rings);
			}
		}
		if (ring >= 0) {
			waveTick(mc);
		}
	}

	private static void startWave(Minecraft mc) {
		rebuilds++;
		waveCenter = SectionPos.of(mc.gameRenderer.mainCamera().position());
		rings = mc.options.getEffectiveRenderDistance() + 1;
		ring = 0;
		waveTick = 0;
		waveFrom = 0;
		frameCount = 0;
		frames = new long[rings * WAVE_TICKS + TAIL_TICKS + 1];
	}

	private static void waveTick(Minecraft mc) {
		if (frameCount < frames.length) {
			frames[frameCount++] = mc.getFrameTimeNs();
		}
		if (ring <= rings) {
			if (waveTick++ % WAVE_TICKS == 0) {
				markRing(mc, waveCenter, ring);
				ring++;
			}
			waveFrom = frameCount;
			return;
		}
		if (frameCount >= frames.length) {
			long[] f = Arrays.copyOf(frames, frameCount);
			long[] sorted = f.clone();
			Arrays.sort(sorted);
			PolishForests.LOG.info(String.format(java.util.Locale.ROOT,
					"[seasons] wave rebuild done: frame time over %d client ticks (last frame of each tick): median %.1f ms, p95 %.1f ms, "
							+ "max %.1f ms (max during the rings %.1f ms)", f.length, sorted[f.length / 2] / 1e6,
					sorted[Math.min(f.length - 1, (int) (f.length * 0.95))] / 1e6, sorted[f.length - 1] / 1e6,
					Arrays.stream(f, 0, Math.max(1, waveFrom)).max().orElse(0) / 1e6));
			ring = -1;
		}
	}

	/** Marks the sections of the chunk columns at Chebyshev distance {@code ring} from {@code center} dirty. */
	static void markRing(Minecraft mc, SectionPos center, int ring) {
		int minY = mc.level.getMinSectionY();
		int maxY = mc.level.getMaxSectionY();
		for (int dx = -ring; dx <= ring; dx++) {
			for (int dz = -ring; dz <= ring; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
					mc.levelExtractor.setSectionRangeDirty(center.x() + dx, minY, center.z() + dz, center.x() + dx, maxY, center.z() + dz);
				}
			}
		}
	}

	/** The tint of the leaves of a species: its curve at the current step and the leaf's phase over the raw biome color. */
	public static final class SpeciesTint implements BlockTintSource {
		private final Species species;

		SpeciesTint(Species species) {
			this.species = species;
		}

		@Override
		public int color(BlockState state) {
			return 0xFF000000 | FoliageCurve.color(species, curveStep(), state.getValue(SpeciesLeavesBlock.PHASE),
					FoliageCurve.DEFAULT_FOLIAGE);
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			int raw = FoliageCurve.conifer(species) ? 0 : level.getBlockTint(pos, RAW_FOLIAGE);
			return 0xFF000000 | FoliageCurve.color(species, curveStep(), state.getValue(SpeciesLeavesBlock.PHASE), raw);
		}

		@Override
		public java.util.Set<Property<?>> relevantProperties() {
			return java.util.Set.of(SpeciesLeavesBlock.PHASE);
		}
	}
}
