package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.client.screen.PolandPresetEditor;
import pl.polishforests.command.HabitatTargets;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.TatraDetailsFeature;
import pl.polishforests.worldgen.feature.tatra.HutPlan;
import pl.polishforests.worldgen.feature.tatra.Piece;
import pl.polishforests.worldgen.feature.tatra.Placement;
import pl.polishforests.worldgen.feature.tatra.TatraSites;
import pl.polishforests.worldgen.feature.tatra.WaterPlan;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Step TT6 (docs/04-pionowy-wycinek.md, "Projekt TT6"), the details of the Tatras in the game test {@code tatra}: a world of
 * the preset of the scale in one of the two vegetation modes, the first {@value #PER_KIND} anchors of each kind of detail on
 * the nearest massif ({@link TatraSites}, chosen from the model, nearest the middle of the main ridge), the chunk of each
 * generated, and the placed details checked from the blocks ({@link TatraDetailsFeature#OBSERVER} records the plans):
 * <ul>
 * <li><b>waterfalls and springs:</b> every planned water cell is water; after the fluid ticks of the plan's cells are forced
 * and {@value #WAIT} ticks run (the chunks force-loaded), there is no water within 4 blocks of the plan outside its own
 * cells (0 leaks), and every flowing cell is within the reach of a source of the plan (the largest distance is reported);</li>
 * <li><b>cave mouths:</b> every cut cell is air, no water or lava beside the tunnel, rock over its roof;</li>
 * <li><b>huts:</b> only in the present-day mode (none in the natural one); the floor of each is planks, the ground under
 * and around the footprint differs by at most {@link HutPlan#MAX_RANGE} blocks (flat ground), the blocks of the plan are there;</li>
 * <li><b>boulders and caves:</b> the counts of boulders; the share of columns with a cave in a window of depth under the
 * top, over 4 chunks (1024 columns) of limestone or dolomite with karst and 4 of granite (the limestone must have more).</li>
 * </ul>
 * Numbers only, no screenshots.
 */
final class TatraDetailsCheck {
	/** Anchors of each kind built and checked. */
	static final int PER_KIND = 2;
	/** Ticks run after the fluid ticks of a water plan are forced. */
	static final int WAIT = 80;
	/** Ticks waited after each site (the generated chunks unload and save). */
	static final int SETTLE = 60;
	/** Chunks of each rock in the census of the caves (1024 columns). */
	static final int CAVE_CHUNKS = 4;
	/** The window of the cave census under the top block (blocks): deeper than the surface layer, not the whole mountain. */
	static final int CAVE_FROM = 12;
	static final int CAVE_TO = 120;

	private TatraDetailsCheck() {
	}

	/** What the feature placed (the observer): kind, anchor, plan. */
	private record Entry(TatraSites.Kind kind, BlockPos at, List<Placement> plan) {
	}

	private static final class Collector implements TatraDetailsFeature.Placements {
		final List<Entry> entries = new CopyOnWriteArrayList<>();
		final AtomicInteger boulders = new AtomicInteger();
		final AtomicInteger bigBoulders = new AtomicInteger();

		@Override
		public void placed(TatraSites.Kind kind, BlockPos at, @Nullable List<Placement> placements, int blocks) {
			if (placements != null) {
				entries.add(new Entry(kind, at, placements));
			}
		}

		@Override
		public void boulder(BlockPos at, int radius) {
			boulders.incrementAndGet();
			if (radius >= 2) {
				bigBoulders.incrementAndGet();
			}
		}
	}

	static void run(ClientGameTestContext context, boolean real, boolean presentDay, List<String> report, List<String> failures) {
		String label = (real ? "realistic" : "gameplay") + (presentDay ? " present-day" : " natural") + ": ";
		Collector collector = new Collector();
		TatraDetailsFeature.OBSERVER = collector;
		long huts0 = TatraDetailsFeature.HUTS.sum();
		long rejected0 = TatraDetailsFeature.REJECTED.sum();
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, real, presentDay)).create()) {
			sp.getServer().runCommand("gamemode spectator @a");
			List<TatraSites.Site> chosen = sp.getServer().computeOnServer(s -> choose(s.overworld(), presentDay, report, failures, label));
			context.waitTicks(SETTLE);
			for (TatraSites.Site site : chosen) {
				int before = collector.entries.size();
				sp.getServer().runOnServer(s -> s.overworld().getChunk(site.x() >> 4, site.z() >> 4));
				context.waitTicks(SETTLE);
				List<Entry> built = new ArrayList<>(collector.entries.subList(before, collector.entries.size())).stream()
						.filter(e -> e.kind() == site.kind() && Math.abs(e.at().getX() - site.x()) <= 1 && Math.abs(e.at().getZ() - site.z()) <= 1)
						.toList();
				if (built.isEmpty()) {
					report.add(label + site.kind() + " anchor (" + site.x() + ", " + site.z() + "): the ground did not hold it");
					continue;
				}
				for (Entry e : built) {
					check(context, sp, e, report, failures, label);
				}
			}
			report.add(String.format(Locale.ROOT, "%sdetails placed: %d waterfalls, %d springs, %d cave mouths, %d huts, %d boulders "
					+ "(%d of radius 2 or 3), %d anchors without a place", label, count(collector, TatraSites.Kind.WATERFALL),
					count(collector, TatraSites.Kind.SPRING), count(collector, TatraSites.Kind.CAVE_MOUTH),
					count(collector, TatraSites.Kind.SHEPHERD_HUT), collector.boulders.get(), collector.bigBoulders.get(),
					TatraDetailsFeature.REJECTED.sum() - rejected0));
			if (!presentDay && (TatraDetailsFeature.HUTS.sum() != huts0 || count(collector, TatraSites.Kind.SHEPHERD_HUT) > 0)) {
				failures.add(label + "a hut in the natural mode");
			}
			caves(context, sp, report, failures, label);
		} finally {
			TatraDetailsFeature.OBSERVER = null;
		}
	}

	private static long count(Collector c, TatraSites.Kind kind) {
		return c.entries.stream().filter(e -> e.kind() == kind).count();
	}

	private static void select(WorldCreationUiState ui, boolean real, boolean presentDay) {
		ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET,
				PolishForests.id(real ? "poland" : "poland_gameplay"));
		ui.setSeed("20260927");
		ui.setAllowCommands(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
		if (presentDay && ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen) {
			ui.updateDimensions(PolandPresetEditor.apply(gen.settings().withAgriculture(true)));
		}
	}

	private static LandscapeModel model(ServerLevel level) {
		return ((PolandChunkGenerator) level.getChunkSource().getGenerator()).model(level.getSeed());
	}

	// ------------------------------------------------------------------ the anchors

	/** The first anchors of each kind, nearest the middle of the nearest massif, at least 120 blocks apart. */
	private static List<TatraSites.Site> choose(ServerLevel level, boolean presentDay, List<String> report, List<String> failures,
			String label) {
		LandscapeModel m = model(level);
		double box = PolishForestsCommands.tatraRadius(m);
		LandscapeModel.Tatra t = m.tatras(-box, -box, box, box).stream()
				.min(Comparator.comparingDouble(a -> Math.hypot(a.x(), a.z()))).orElse(null);
		List<TatraSites.Site> out = new ArrayList<>();
		if (t == null) {
			failures.add(label + "no Tatra massif");
			return out;
		}
		HabitatTargets.World world = HabitatTargets.World.of(level);
		for (TatraSites.Kind kind : TatraSites.Kind.values()) {
			if (!TatraSites.exists(kind, presentDay)) {
				continue;
			}
			List<TatraSites.Site> found = new ArrayList<>();
			int cell = TatraSites.cellChunks(m, kind) * 16;
			int cx = Math.floorDiv((int) Math.round(t.x()), cell);
			int cz = Math.floorDiv((int) Math.round(t.z()), cell);
			int rings = (int) Math.ceil(t.reach() / cell);
			for (int r = 0; r <= rings && found.size() < PER_KIND; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r || found.size() >= PER_KIND) {
							continue;
						}
						TatraSites.Site s = TatraSites.site(m, kind, cx + dx, cz + dz, presentDay, world::code);
						if (s != null && found.stream().noneMatch(f -> Math.hypot(f.x() - s.x(), f.z() - s.z()) < 120)) {
							found.add(s);
						}
					}
				}
			}
			report.add(label + kind + " anchors chosen: " + found.stream().map(s -> "(" + s.x() + ", " + s.z() + ") " + s.rock()).toList());
			if (found.isEmpty()) {
				failures.add(label + "no anchor of " + kind + " on the nearest massif");
			}
			out.addAll(found);
		}
		return out;
	}

	// ------------------------------------------------------------------ the checks

	private static void check(ClientGameTestContext context, TestSingleplayerContext sp, Entry e, List<String> report,
			List<String> failures, String label) {
		switch (e.kind()) {
			case WATERFALL, SPRING -> water(context, sp, e, report, failures, label);
			case CAVE_MOUTH -> sp.getServer().runOnServer(s -> mouth(s.overworld(), e, report, failures, label));
			case SHEPHERD_HUT -> sp.getServer().runOnServer(s -> hut(s.overworld(), e, report, failures, label));
		}
	}

	private static boolean water(ServerLevel level, BlockPos p) {
		return level.getFluidState(p).is(FluidTags.WATER);
	}

	private static void water(ClientGameTestContext context, TestSingleplayerContext sp, Entry e, List<String> report,
			List<String> failures, String label) {
		Set<Long> planned = new HashSet<>();
		int x0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z0 = Integer.MAX_VALUE;
		int z1 = Integer.MIN_VALUE;
		List<Placement> cells = new ArrayList<>();
		for (Placement p : e.plan()) {
			if (p.piece().water()) {
				planned.add(p.key());
				cells.add(p);
				x0 = Math.min(x0, p.x());
				x1 = Math.max(x1, p.x());
				z0 = Math.min(z0, p.z());
				z1 = Math.max(z1, p.z());
			}
		}
		int fx0 = x0;
		int fx1 = x1;
		int fz0 = z0;
		int fz1 = z1;
		// Keep the chunks loaded and force the fluid ticks of every cell of the plan: the water is recomputed from what was built.
		sp.getServer().runCommand(String.format(Locale.ROOT, "forceload add %d %d %d %d", fx0 - 4, fz0 - 4, fx1 + 4, fz1 + 4));
		context.waitTicks(20);
		int missing = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			int miss = 0;
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (Placement p : cells) {
				if (!water(level, at.set(p.x(), p.y(), p.z()))) {
					miss++;
				}
			}
			for (Placement p : cells) {
				at.set(p.x(), p.y(), p.z());
				level.scheduleTick(at.immutable(), net.minecraft.world.level.material.Fluids.WATER, 1);
				level.scheduleTick(at.immutable(), net.minecraft.world.level.material.Fluids.FLOWING_WATER, 1);
			}
			return miss;
		});
		context.waitTicks(WAIT);
		int[] result = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			int stray = 0;
			int lost = 0;
			int far = 0;
			int farthest = 0;
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int x = fx0 - 4; x <= fx1 + 4; x++) {
				for (int z = fz0 - 4; z <= fz1 + 4; z++) {
					for (int y = e.at().getY() - WaterPlan.MAX_FALL - 6; y <= e.at().getY() + 6; y++) {
						if (water(level, at.set(x, y, z)) && !planned.contains(pl.polishforests.worldgen.feature.tatra.Cells.pack(x, y, z))) {
							stray++;
						}
					}
				}
			}
			for (Placement p : cells) {
				at.set(p.x(), p.y(), p.z());
				if (!water(level, at)) {
					lost++;
				}
				if (p.piece() != Piece.SOURCE) {
					int best = Integer.MAX_VALUE;
					for (Placement q : cells) {
						if (q.piece() == Piece.SOURCE) {
							best = Math.min(best, Math.abs(q.x() - p.x()) + Math.abs(q.y() - p.y()) + Math.abs(q.z() - p.z()));
						}
					}
					farthest = Math.max(farthest, best);
					if (best > WaterPlan.MAX_FALL + 8) {
						far++;
					}
				}
			}
			return new int[] {stray, lost, far, farthest};
		});
		sp.getServer().runCommand(String.format(Locale.ROOT, "forceload remove %d %d %d %d", fx0 - 4, fz0 - 4, fx1 + 4, fz1 + 4));
		long sources = cells.stream().filter(c -> c.piece() == Piece.SOURCE).count();
		report.add(String.format(Locale.ROOT, "%s%s at (%d, %d, %d): %d water cells (%d sources), %d missing after the build, after %d ticks "
				+ "%d stray water cells (leaks), %d planned cells lost, flowing cells at most %d blocks from a source, %d beyond reach",
				label, e.kind(), e.at().getX(), e.at().getY(), e.at().getZ(), cells.size(), sources, missing, WAIT, result[0],
				result[1], result[3], result[2]));
		if (missing > 0 || result[0] > 0 || result[1] > 0 || result[2] > 0) {
			failures.add(label + e.kind() + " at (" + e.at().getX() + ", " + e.at().getZ() + "): " + missing + " missing, " + result[0]
					+ " leaks, " + result[1] + " lost, " + result[2] + " beyond reach");
		}
		context.waitTicks(SETTLE);
	}

	private static void mouth(ServerLevel level, Entry e, List<String> report, List<String> failures, String label) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int cut = 0;
		int notAir = 0;
		int fluid = 0;
		int noRoof = 0;
		Set<Long> air = new HashSet<>();
		for (Placement p : e.plan()) {
			air.add(p.key());
		}
		for (Placement p : e.plan()) {
			cut++;
			BlockState s = level.getBlockState(at.set(p.x(), p.y(), p.z()));
			if (!s.isAir()) {
				notAir++;
			}
			for (BlockPos n : new BlockPos[] {at.east(), at.west(), at.north(), at.south(), at.above(), at.below()}) {
				if (!level.getFluidState(n).isEmpty()) {
					fluid++;
				}
			}
			if (!air.contains(pl.polishforests.worldgen.feature.tatra.Cells.pack(p.x(), p.y() + 1, p.z()))) {
				// The highest cut cell of the column: rock (not air) over it, two blocks of roof where the tunnel is under rock.
				if (level.getBlockState(at.set(p.x(), p.y() + 1, p.z())).isAir() && level.getBlockState(at.set(p.x(), p.y() + 2, p.z())).isAir()) {
					noRoof++;
				}
			}
		}
		report.add(String.format(Locale.ROOT, "%scave mouth at (%d, %d, %d): %d cells cut, %d not air, %d fluid cells beside, %d column tops "
				+ "open to the sky (the portal)", label, e.at().getX(), e.at().getY(), e.at().getZ(), cut, notAir, fluid, noRoof));
		if (notAir > 0 || fluid > 0) {
			failures.add(label + "cave mouth at (" + e.at().getX() + ", " + e.at().getZ() + "): " + notAir + " cells not air, " + fluid
					+ " fluid cells beside");
		}
	}

	private static void hut(ServerLevel level, Entry e, List<String> report, List<String> failures, String label) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int floor = e.at().getY();
		int planks = 0;
		int missing = 0;
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (Placement p : e.plan()) {
			BlockState s = level.getBlockState(at.set(p.x(), p.y(), p.z()));
			boolean ok = switch (p.piece()) {
				case AIR -> s.isAir() || !s.isSolid();
				case HAY -> s.is(Blocks.HAY_BLOCK);
				case PLANKS, LOG, STAIRS, ROCK -> !s.isAir();
				default -> true;
			};
			if (!ok) {
				missing++;
			}
			if (p.piece() == Piece.PLANKS && p.y() == floor) {
				planks++;
				// The ground under the floor: its columns' tops (the block below the floor is solid).
				int y = p.y() - 1;
				while (y > level.getMinY() && !level.getBlockState(at.set(p.x(), y, p.z())).isSolid()) {
					y--;
				}
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
			}
		}
		int range = hi - lo;
		report.add(String.format(Locale.ROOT, "%shut at (%d, %d, %d): %d floor planks, ground under the floor differs by %d blocks, %d blocks of "
				+ "the plan missing", label, e.at().getX(), floor, e.at().getZ(), planks, range, missing));
		if (planks < HutPlan.WIDTH * HutPlan.LENGTH || missing > 0 || range > HutPlan.MAX_RANGE + HutPlan.MAX_RANGE) {
			failures.add(label + "hut at (" + e.at().getX() + ", " + e.at().getZ() + "): " + planks + " floor planks, range " + range
					+ ", " + missing + " missing");
		}
	}

	// ------------------------------------------------------------------ caves in the limestone and the granite

	private static void caves(ClientGameTestContext context, TestSingleplayerContext sp, List<String> report, List<String> failures,
			String label) {
		int[][] chunks = sp.getServer().computeOnServer(s -> caveChunks(s.overworld()));
		if (chunks[0].length < 2 * CAVE_CHUNKS || chunks[1].length < 2 * CAVE_CHUNKS) {
			failures.add(label + "caves: too few chunks of the rocks (" + chunks[0].length / 2 + " limestone, " + chunks[1].length / 2 + " granite)");
			return;
		}
		int[] columns = new int[2];
		for (int rock = 0; rock < 2; rock++) {
			for (int c = 0; c < CAVE_CHUNKS; c++) {
				int cx = chunks[rock][2 * c];
				int cz = chunks[rock][2 * c + 1];
				columns[rock] += sp.getServer().computeOnServer(s -> caveColumns(s.overworld(), cx, cz));
				context.waitTicks(40);
			}
		}
		int n = 256 * CAVE_CHUNKS;
		report.add(String.format(Locale.ROOT, "%scave census (window %d-%d blocks under the top, %d columns each): limestone and dolomite %d columns "
				+ "with a cave (%.1f%%), granite %d (%.1f%%)", label, CAVE_FROM, CAVE_TO, n, columns[0], 100.0 * columns[0] / n,
				columns[1], 100.0 * columns[1] / n));
		if (columns[0] <= columns[1]) {
			failures.add(label + "caves: limestone " + columns[0] + " columns, granite " + columns[1] + " (limestone must have more)");
		}
	}

	/** Chunk coordinates (x, z pairs) of {@value #CAVE_CHUNKS} chunks of limestone or dolomite with karst and of granite, nearest the massif. */
	private static int[][] caveChunks(ServerLevel level) {
		LandscapeModel m = model(level);
		double box = PolishForestsCommands.tatraRadius(m);
		LandscapeModel.Tatra t = m.tatras(-box, -box, box, box).stream()
				.min(Comparator.comparingDouble(a -> Math.hypot(a.x(), a.z()))).orElseThrow();
		double step = 192 * PolishForestsCommands.tatraFormScale(m);
		List<Integer> lime = new ArrayList<>();
		List<Integer> granite = new ArrayList<>();
		for (double r = 0; r <= t.reach() && (lime.size() < 2 * CAVE_CHUNKS || granite.size() < 2 * CAVE_CHUNKS); r += step) {
			int n = Math.max(1, (int) Math.ceil(2 * Math.PI * r / step));
			for (int k = 0; k < n; k++) {
				double a = k * 2 * Math.PI / n + r;
				double x = t.x() + r * Math.cos(a);
				double z = t.z() + r * Math.sin(a);
				if (!m.tatraRock(x, z)) {
					continue;
				}
				ColumnSample s = m.sample(x, z);
				Substrate rock = s.terrain().rock();
				int cx = (int) Math.floor(x / 16);
				int cz = (int) Math.floor(z / 16);
				if ((rock == Substrate.LIMESTONE || rock == Substrate.DOLOMITE) && m.karst(x, z) > 0.5 && lime.size() < 2 * CAVE_CHUNKS
						&& !s.hasWater() && s.surface() > 700) {
					lime.add(cx);
					lime.add(cz);
				} else if (rock == Substrate.GRANITE && granite.size() < 2 * CAVE_CHUNKS && !s.hasWater() && s.surface() > 700) {
					granite.add(cx);
					granite.add(cz);
				}
			}
		}
		return new int[][] {lime.stream().mapToInt(Integer::intValue).toArray(), granite.stream().mapToInt(Integer::intValue).toArray()};
	}

	/** Columns of the chunk with an air block in the window of depth under the top block. */
	private static int caveColumns(ServerLevel level, int cx, int cz) {
		LevelChunk chunk = level.getChunk(cx, cz);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int columns = 0;
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int top = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lx, lz) - 1;
				boolean cave = false;
				for (int y = top - CAVE_FROM; y >= Math.max(level.getMinY(), top - CAVE_TO) && !cave; y--) {
					cave = chunk.getBlockState(at.set((cx << 4) + lx, y, (cz << 4) + lz)).isAir();
				}
				if (cave) {
					columns++;
				}
			}
		}
		return columns;
	}
}
