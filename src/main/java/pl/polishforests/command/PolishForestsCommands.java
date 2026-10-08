package pl.polishforests.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.habitat.AltitudinalBelts;
import pl.polishforests.worldgen.habitat.Calibration;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Substrate;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Diagnostic commands of the "Poland" world:
 * <ul>
 * <li>{@code /polishforests find <target>} – the nearest landscape type or landform;</li>
 * <li>{@code /polishforests elevation <from> <to>} – the nearest terrain with an elevation in the given range (m a.s.l.);</li>
 * <li>{@code /polishforests highest [radius_km]} – the highest point in the area;</li>
 * <li>{@code /polishforests list} – available targets;</li>
 * <li>{@code /polishforests here} – description of the terrain at the player's position.</li>
 * </ul>
 */
public final class PolishForestsCommands {
	private PolishForestsCommands() {
	}

	/** Target kind: landscape type, belt, water, coast or landform. */
	private enum Kind {
		LANDSCAPE, BELT, WATER, COAST, LANDFORM
	}

	/**
	 * Search targets. {@code fine} marks small objects (searched more densely, over a smaller area),
	 * {@code forms} – the need for a full terrain description instead of just the column sample, and
	 * {@code coastal} – objects in a narrow coastal strip, first searched coarsely by distance from the
	 * coastline.
	 */
	public enum Target {
		OUTWASH_PLAIN(Kind.LANDSCAPE, false, false, d -> d.sample().type() == LandscapeType.OUTWASH_PLAIN),
		MORAINE_PLATEAU(Kind.LANDSCAPE, false, false, d -> d.sample().type() == LandscapeType.MORAINE_PLATEAU),
		OLD_GLACIAL_PLAIN(Kind.LANDSCAPE, false, false, d -> d.sample().type() == LandscapeType.OLD_GLACIAL_PLAIN),
		FOOTHILLS(Kind.LANDSCAPE, false, false, d -> d.sample().type() == LandscapeType.FOOTHILLS),
		BESKIDS(Kind.LANDSCAPE, false, false, d -> d.sample().type() == LandscapeType.BESKIDS),
		SEA(Kind.LANDSCAPE, false, false, true, d -> d.sample().type() == LandscapeType.SEA),
		COASTLAND(Kind.LANDSCAPE, true, false, true, d -> d.sample().type() == LandscapeType.COASTLAND),
		// Belts by the nominal boundary from habitat/AltitudinalBelts (no aspect correction, like the terrain description).
		LOWER_MONTANE(Kind.BELT, false, false,
				d -> d.sample().type() == LandscapeType.BESKIDS && !AltitudinalBelts.isUpperMontane(d.sample().surface())),
		UPPER_MONTANE(Kind.BELT, false, false,
				d -> d.sample().type() == LandscapeType.BESKIDS && AltitudinalBelts.isUpperMontane(d.sample().surface())),
		RIVER(Kind.WATER, true, false, d -> d.sample().waterKind() == WaterKind.RIVER && d.sample().hasWater()),
		LAKE(Kind.WATER, true, false, d -> d.sample().waterKind().isLake() && d.sample().hasWater()),
		TUNNEL_VALLEY_LAKE(Kind.WATER, true, false,
				d -> d.sample().waterKind() == WaterKind.LAKE && d.sample().hasWater()),
		KETTLE_POND(Kind.WATER, true, false, d -> d.sample().waterKind() == WaterKind.KETTLE && d.sample().hasWater()),
		PEATLAND(Kind.WATER, true, false, d -> d.sample().substrate() == Substrate.PEAT),
		STREAM(Kind.WATER, true, true, d -> d.forms().contains(Landform.STREAM)),
		HEADWATERS(Kind.WATER, true, true, d -> d.forms().contains(Landform.HEADWATERS)),
		OXBOW_LAKE(Kind.WATER, true, false, d -> d.sample().waterKind() == WaterKind.OXBOW && d.sample().hasWater()),
		LAGOON(Kind.WATER, true, true, true, d -> d.forms().contains(Landform.LAGOON)),
		RIVER_MOUTH(Kind.WATER, true, true, true, d -> d.forms().contains(Landform.RIVER_MOUTH)),
		BEACH(Kind.COAST, true, true, true, d -> d.forms().contains(Landform.BEACH)),
		COASTAL_DUNES(Kind.COAST, true, true, true, d -> d.forms().contains(Landform.COASTAL_DUNES)),
		// Step K8b2: a cliff of a high shore (not the high foredune of a low shore, which also gets the CLIFF landform), at
		// least CLIFF_MIN_HEIGHT high; the seaward wall is checked in matches (it needs the model).
		CLIFF(Kind.COAST, true, true, true, d -> d.forms().contains(Landform.CLIFF)
				&& d.sample().terrain().lowShore() < Calibration.LOW_SHORE
				&& d.sample().terrain().cliffHeight() >= CLIFF_MIN_HEIGHT),
		RIVER_VALLEY(Kind.LANDFORM, true, false,
				d -> d.sample().substrate() == Substrate.ALLUVIUM || d.sample().waterKind() == WaterKind.RIVER),
		VALLEY_SLOPE(Kind.LANDFORM, true, true, d -> d.forms().contains(Landform.VALLEY_SLOPE)),
		TUNNEL_VALLEY(Kind.LANDFORM, true, true, d -> d.forms().contains(Landform.TUNNEL_VALLEY)),
		INLAND_DUNES(Kind.LANDFORM, true, true, d -> d.forms().contains(Landform.INLAND_DUNES)),
		END_MORAINE(Kind.LANDFORM, true, true, d -> d.forms().contains(Landform.END_MORAINE)),
		RIDGE(Kind.LANDFORM, true, true, d -> d.forms().contains(Landform.RIDGE)),
		SUMMIT(Kind.LANDFORM, true, true, d -> d.forms().contains(Landform.SUMMIT)),
		MOUNTAIN_PASS(Kind.LANDFORM, true, true, d -> d.forms().contains(Landform.MOUNTAIN_PASS)),
		MOUNTAIN_VALLEY(Kind.LANDFORM, true, true, d -> d.forms().contains(Landform.MOUNTAIN_VALLEY));

		final Kind kind;
		final boolean fine;
		final boolean forms;
		final boolean coastal;
		final Predicate<LandscapeModel.Description> test;

		Target(Kind kind, boolean fine, boolean forms, Predicate<LandscapeModel.Description> test) {
			this(kind, fine, forms, false, test);
		}

		Target(Kind kind, boolean fine, boolean forms, boolean coastal, Predicate<LandscapeModel.Description> test) {
			this.kind = kind;
			this.fine = fine;
			this.forms = forms;
			this.coastal = coastal;
			this.test = test;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Component displayName() {
			return Component.translatable("polishforests.target." + id());
		}
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("polishforests")
				.then(Commands.literal("find")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("target", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
										Arrays.stream(Target.values()).map(Target::id), builder))
								.executes(PolishForestsCommands::find)))
				.then(Commands.literal("elevation")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("from", IntegerArgumentType.integer(-100, 3_000))
								.then(Commands.argument("to", IntegerArgumentType.integer(-100, 3_000))
										.executes(PolishForestsCommands::findElevation))))
				.then(Commands.literal("highest")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> highest(ctx, -1))
						.then(Commands.argument("radius_km", IntegerArgumentType.integer(1, 200))
								.executes(ctx -> highest(ctx, IntegerArgumentType.getInteger(ctx, "radius_km")))))
				.then(Commands.literal("list").executes(PolishForestsCommands::list))
				.then(Commands.literal("here").executes(PolishForestsCommands::here)));
	}

	private static LandscapeModel modelOrNull(ServerLevel level) {
		if (level.getChunkSource().getGenerator() instanceof PolandChunkGenerator gen) {
			return gen.model(level.getSeed());
		}
		return null;
	}

	private static LandscapeModel requireModel(CommandSourceStack source) {
		LandscapeModel model = modelOrNull(source.getLevel());
		if (model == null) {
			source.sendFailure(Component.translatable("polishforests.command.not_poland"));
		}
		return model;
	}

	// ------------------------------------------------------------------ list and here

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		for (Kind kind : Kind.values()) {
			MutableComponent line = Component.translatable("polishforests.kind." + kind.name().toLowerCase(Locale.ROOT))
					.withStyle(ChatFormatting.GOLD).append(": ");
			boolean first = true;
			for (Target t : Target.values()) {
				if (t.kind != kind) {
					continue;
				}
				if (!first) {
					line.append(", ");
				}
				first = false;
				line.append(Component.literal(t.id()).withStyle(s -> s.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.SuggestCommand("/polishforests find " + t.id()))
						.withHoverEvent(new HoverEvent.ShowText(t.displayName()))));
			}
			source.sendSuccess(() -> line, false);
		}
		return Target.values().length;
	}

	private static int here(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		LandscapeModel model = requireModel(source);
		if (model == null) {
			return 0;
		}
		Vec3 pos = source.getPosition();
		LandscapeModel.Description d = model.describe(pos.x, pos.z);
		ColumnSample s = d.sample();
		String water = s.hasWater() ? s.waterKind().name().toLowerCase(Locale.ROOT) + " " + s.waterLevel() + " m" : "-";
		String forms = d.forms().isEmpty() ? "-"
				: d.forms().stream().map(f -> f.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
		source.sendSuccess(() -> Component.translatable("polishforests.command.here",
				s.type().name().toLowerCase(Locale.ROOT), String.format(Locale.ROOT, "%.1f", s.surface()),
				s.substrate().name().toLowerCase(Locale.ROOT), water, forms), false);
		return 1;
	}

	// ------------------------------------------------------------------ search

	private static int find(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		LandscapeModel model = requireModel(source);
		if (model == null) {
			return 0;
		}
		String arg = StringArgumentType.getString(ctx, "target").toUpperCase(Locale.ROOT);
		Target target;
		try {
			target = Target.valueOf(arg);
		} catch (IllegalArgumentException e) {
			source.sendFailure(Component.translatable("polishforests.command.unknown_target", arg.toLowerCase(Locale.ROOT)));
			return 0;
		}
		Vec3 origin = source.getPosition();
		source.sendSuccess(() -> Component.translatable("polishforests.command.searching", target.displayName()), false);
		double[][] phases = phases(model, target.fine);
		double radius = target.coastal ? coastalRadius(model) : phases[phases.length - 1][1];
		runSearch(source, origin, () -> locate(model, target, origin.x, origin.z), radius, target.displayName());
		return 1;
	}

	/** The nearest location of the target from point {@code (ox, oz)}: {x, z} or null. */
	public static double[] locate(LandscapeModel model, Target target, double ox, double oz) {
		Predicate<double[]> test = p -> matches(model, target, p);
		return target.coastal ? searchCoastal(model, test, ox, oz) : search(phases(model, target.fine), test, ox, oz);
	}

	/**
	 * Whether the place {@code p} = {x, z} is the target: its test on the sample or the terrain description, and for a
	 * {@link Target#CLIFF} also a seaward wall ({@link #hasSeawardWall}).
	 */
	public static boolean matches(LandscapeModel model, Target target, double[] p) {
		boolean found = target.test.test(target.forms ? model.describe(p[0], p[1])
				: new LandscapeModel.Description(model.sample(p[0], p[1]), EnumSet.noneOf(Landform.class)));
		return found && (target != Target.CLIFF || hasSeawardWall(model, p[0], p[1]));
	}

	/** Step K8b2: least height of a cliff found by {@code find cliff} (m). */
	static final double CLIFF_MIN_HEIGHT = 12;
	/** Step K8b2: the ground {@value #CLIFF_RUN} m seaward of a cliff lies at least {@value #CLIFF_MIN_DROP} m lower. */
	static final double CLIFF_RUN = 4;
	static final double CLIFF_MIN_DROP = 6;

	/**
	 * Step K8b2: a steep seaward wall at (x, z): the valley-free terrain {@link #CLIFF_RUN} m towards the sea (along the
	 * gradient of the coast distance) lies at least {@link #CLIFF_MIN_DROP} m lower (the cliff wall of the model is
	 * 2.5 : 1; a foredune rises at most about 1 m per meter).
	 */
	static boolean hasSeawardWall(LandscapeModel model, double x, double z) {
		double gx = model.coastDistance(x + 2, z) - model.coastDistance(x - 2, z);
		double gz = model.coastDistance(x, z + 2) - model.coastDistance(x, z - 2);
		double len = Math.hypot(gx, gz);
		if (!(len > 1e-9)) {
			return false;
		}
		double sx = x - CLIFF_RUN * gx / len;
		double sz = z - CLIFF_RUN * gz / len;
		return model.landElevation(x, z) - model.landElevation(sx, sz) >= CLIFF_MIN_DROP;
	}

	/** Coastline search range in meters. */
	private static double coastalRadius(LandscapeModel model) {
		return model.scale() == LandscapeScale.REALISTIC ? 2_000_000 : 300_000;
	}

	/**
	 * Stretches of the coast searched densely before giving up (step K8b2: 12 → 24, so that {@code find cliff} reaches
	 * a full cliff of the realistic scale; targets found within 12 stretches are found at the same place).
	 */
	static final int COASTAL_TRIES = 24;

	/**
	 * Coastal objects: first successive stretches of the coastline farther and farther from the player
	 * (coarsely, by the analytic distance from the shore), then a dense search around each of them.
	 */
	static double[] searchCoastal(LandscapeModel model, Predicate<double[]> test, double ox, double oz) {
		boolean realistic = model.scale() == LandscapeScale.REALISTIC;
		double coarse = realistic ? 1_500 : 150;
		double band = realistic ? 2_500 : 250;
		double[][] local = realistic ? new double[][] {{24, 1_500}, {60, 6_000}} : new double[][] {{6, 200}, {20, 1_000}};
		double max = coastalRadius(model);
		double[] p = new double[2];
		int tries = 0;
		for (double r = 0; r <= max && tries < COASTAL_TRIES; r += coarse) {
			int n = Math.max(8, (int) Math.ceil(2 * Math.PI * r / coarse));
			for (int k = 0; k < n && tries < COASTAL_TRIES; k++) {
				double a = r * 0.618 + k * (2 * Math.PI / n);
				p[0] = ox + r * Math.cos(a);
				p[1] = oz + r * Math.sin(a);
				if (Math.abs(model.coastDistance(p[0], p[1])) < band) {
					tries++;
					double[] found = search(local, test, p[0], p[1]);
					if (found != null) {
						return found;
					}
					// Next attempt farther out: this stretch of the coast has already been checked.
					r += local[local.length - 1][1];
					break;
				}
			}
		}
		return null;
	}

	private static int findElevation(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		LandscapeModel model = requireModel(source);
		if (model == null) {
			return 0;
		}
		int from = IntegerArgumentType.getInteger(ctx, "from");
		int to = IntegerArgumentType.getInteger(ctx, "to");
		int lo = Math.min(from, to);
		int hi = Math.max(from, to);
		Component name = Component.translatable("polishforests.command.elevation_range", lo, hi);
		source.sendSuccess(() -> Component.translatable("polishforests.command.searching", name), false);
		double[][] phases = phases(model, false);
		Vec3 origin = source.getPosition();
		Predicate<double[]> test = p -> {
			ColumnSample s = model.sample(p[0], p[1]);
			return !s.hasWater() && s.surface() >= lo && s.surface() <= hi;
		};
		runSearch(source, origin, () -> search(phases, test, origin.x, origin.z), phases[phases.length - 1][1], name);
		return 1;
	}

	/** Search phases {spacing, radius} in meters, matched to the world scale. */
	private static double[][] phases(LandscapeModel model, boolean fine) {
		boolean gameplay = model.scale() != LandscapeScale.REALISTIC;
		if (gameplay) {
			return fine ? new double[][] {{24, 3_000}, {80, 20_000}, {300, 100_000}}
					: new double[][] {{100, 20_000}, {500, 300_000}};
		}
		return fine ? new double[][] {{48, 5_000}, {160, 40_000}, {600, 300_000}}
				: new double[][] {{400, 60_000}, {2_000, 2_000_000}};
	}

	private static void runSearch(CommandSourceStack source, Vec3 origin, Supplier<double[]> search, double radius,
			Component name) {
		ServerLevel level = source.getLevel();
		CompletableFuture.supplyAsync(search, Util.backgroundExecutor())
				.thenAccept(found -> source.getServer().execute(() -> {
					if (found == null) {
						double km = radius / 1000.0;
						source.sendFailure(Component.translatable("polishforests.command.not_found", name,
								String.format(Locale.ROOT, "%.0f", km)));
					} else {
						report(source, level, origin, found, "polishforests.command.found", name);
					}
				}));
	}

	/** Search in rings around the player; returns {x, z} or null. */
	static double[] search(double[][] phases, Predicate<double[]> test, double ox, double oz) {
		double r = 0;
		double[] p = new double[2];
		for (double[] phase : phases) {
			double spacing = phase[0];
			double max = phase[1];
			for (; r <= max; r += spacing) {
				int n = Math.max(8, (int) Math.ceil(2 * Math.PI * r / spacing));
				double offset = r * 0.618;
				for (int k = 0; k < n; k++) {
					double a = offset + k * (2 * Math.PI / n);
					p[0] = ox + r * Math.cos(a);
					p[1] = oz + r * Math.sin(a);
					if (test.test(p)) {
						return new double[] {p[0], p[1]};
					}
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ highest point

	private static int highest(CommandContext<CommandSourceStack> ctx, int radiusKm) {
		CommandSourceStack source = ctx.getSource();
		LandscapeModel model = requireModel(source);
		if (model == null) {
			return 0;
		}
		double radius = radiusKm > 0 ? radiusKm * 1000.0 : model.scale() == LandscapeScale.REALISTIC ? 20_000 : 3_000;
		Vec3 origin = source.getPosition();
		ServerLevel level = source.getLevel();
		Component name = Component.translatable("polishforests.command.highest_name",
				String.format(Locale.ROOT, "%.0f", radius / 1000.0));
		source.sendSuccess(() -> Component.translatable("polishforests.command.searching", name), false);
		CompletableFuture.supplyAsync(() -> highestPoint(model, origin.x, origin.z, radius), Util.backgroundExecutor())
				.thenAccept(found -> source.getServer().execute(() -> {
					Component title = Component.translatable("polishforests.command.highest_found",
							String.format(Locale.ROOT, "%.0f", found[2]));
					report(source, level, origin, found, "polishforests.command.found", title);
				}));
		return 1;
	}

	/** A grid of about 200 × 200 points, then hill climbing to the local maximum. Returns {x, z, elevation}. */
	static double[] highestPoint(LandscapeModel model, double ox, double oz, double radius) {
		double step = radius / 100.0;
		double bx = ox;
		double bz = oz;
		double bh = model.landElevation(ox, oz);
		for (double dx = -radius; dx <= radius; dx += step) {
			for (double dz = -radius; dz <= radius; dz += step) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				double h = model.landElevation(ox + dx, oz + dz);
				if (h > bh) {
					bh = h;
					bx = ox + dx;
					bz = oz + dz;
				}
			}
		}
		for (double s = step / 2; s >= 1; s /= 2) {
			boolean moved = true;
			while (moved) {
				moved = false;
				for (int k = 0; k < 8; k++) {
					double a = k * Math.PI / 4;
					double x = bx + s * Math.cos(a);
					double z = bz + s * Math.sin(a);
					double h = model.landElevation(x, z);
					if (h > bh) {
						bh = h;
						bx = x;
						bz = z;
						moved = true;
					}
				}
			}
		}
		return new double[] {bx, bz, bh};
	}

	// ------------------------------------------------------------------ result

	private static void report(CommandSourceStack source, ServerLevel level, Vec3 origin, double[] found, String key,
			Component name) {
		int x = (int) Math.floor(found[0]);
		int z = (int) Math.floor(found[1]);
		int y = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
				level.getChunkSource().randomState());
		int distance = (int) Math.round(Math.hypot(found[0] - origin.x, found[1] - origin.z));
		Component coords = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", x, y, z))
				.withStyle(s -> s.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.SuggestCommand("/tp @s " + x + " " + y + " " + z))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip"))));
		source.sendSuccess(() -> Component.translatable(key, name, String.valueOf(distance), coords), false);
	}
}
