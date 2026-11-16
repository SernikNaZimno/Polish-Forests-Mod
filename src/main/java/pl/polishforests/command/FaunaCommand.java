package pl.polishforests.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.spawn.FaunaRules;

/**
 * {@code /polishforests fauna [radius]} (step FA1, G.62-e): the animals within a horizontal radius (default
 * {@value #DEFAULT_RADIUS}, at most {@value #MAX_RADIUS} blocks) by species, the animals of the mod with their states
 * (fleeing, walking, grazing, resting, alert, standing), the vanilla animals (the creature, ambient, axolotl and water
 * creature categories) by count. For the lists "Do sprawdzenia w grze", next to {@code find}, {@code here} and {@code list}.
 * Step FA2: under each species of the mod its groups (by group id, sizes of the animals in the radius) and looks (young,
 * coat, antlers).
 */
public final class FaunaCommand {
	public static final int DEFAULT_RADIUS = 64;
	public static final int MAX_RADIUS = 256;

	private FaunaCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("fauna").executes(ctx -> run(ctx, DEFAULT_RADIUS))
				.then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
						.executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "radius"))));
	}

	/** One line per species, mod animals first; the number of animals is the command's result. */
	private static int run(CommandContext<CommandSourceStack> ctx, int radius) {
		CommandSourceStack source = ctx.getSource();
		Census census = census(source.getLevel(), source.getPosition(), radius);
		if (census.total == 0) {
			source.sendSuccess(() -> Component.translatable("polishforests.command.fauna_none", radius), false);
		} else {
			source.sendSuccess(() -> Component.translatable("polishforests.command.fauna", radius, census.total).withStyle(ChatFormatting.GOLD),
					false);
			for (Map.Entry<EntityType<?>, Line> e : census.lines.entrySet()) {
				Line line = e.getValue();
				MutableComponent text = Component.literal(String.valueOf(line.count));
				if (!line.states.isEmpty()) {
					text.append(" (");
					boolean first = true;
					for (Map.Entry<PolishAnimal.State, Integer> st : line.states.entrySet()) {
						if (!first) {
							text.append(", ");
						}
						first = false;
						text.append(Component.translatable("polishforests.fauna.state." + st.getKey().id())).append(" " + st.getValue());
					}
					text.append(")");
				}
				MutableComponent row = Component.translatable("polishforests.command.fauna_species", e.getKey().getDescription(), text);
				source.sendSuccess(() -> line.mod ? row.withStyle(ChatFormatting.GREEN) : row, false);
				if (line.mod) {
					MutableComponent looks = Component.empty();
					boolean first = true;
					for (Map.Entry<String, Integer> lk : line.looks.entrySet()) {
						if (!first) {
							looks.append(", ");
						}
						first = false;
						looks.append(Component.translatable("polishforests.fauna.look." + lk.getKey())).append(" " + lk.getValue());
					}
					List<Integer> sizes = line.groupSizes();
					MutableComponent groups = Component.translatable("polishforests.command.fauna_groups", sizes.size(),
							sizes.toString().replace("[", "").replace("]", ""), looks);
					source.sendSuccess(() -> groups.withStyle(ChatFormatting.DARK_GREEN), false);
				}
			}
		}
		if (!FaunaRules.spawning(source.getLevel())) {
			source.sendSuccess(() -> Component.translatable("polishforests.command.fauna_off", "polishforests:forest_fauna")
					.withStyle(ChatFormatting.GRAY), false);
		}
		return census.total;
	}

	/** The animals of one species: how many, whether of the mod, and the mod animals by state. */
	public static final class Line {
		public final boolean mod;
		public int count;
		public final Map<PolishAnimal.State, Integer> states = new EnumMap<>(PolishAnimal.State.class);
		/** Animals of the mod by look key (young, coat, antlers; step FA2). */
		public final Map<String, Integer> looks = new java.util.TreeMap<>();
		/** Animals of the mod in the radius by group id (step FA2). */
		public final Map<java.util.UUID, Integer> groups = new LinkedHashMap<>();

		/** The sizes of the groups in the radius, largest first. */
		public List<Integer> groupSizes() {
			List<Integer> sizes = new ArrayList<>(groups.values());
			sizes.sort(java.util.Comparator.reverseOrder());
			return sizes;
		}

		Line(boolean mod) {
			this.mod = mod;
		}
	}

	/** The animals around a point by species (mod species first, then vanilla, each in order of appearance). */
	public static final class Census {
		public final Map<EntityType<?>, Line> lines = new LinkedHashMap<>();
		public int total;
	}

	/** Counts the animals within a horizontal radius of a point (64 blocks up and down); used by the fauna game test too. */
	public static Census census(ServerLevel level, Vec3 at, int radius) {
		AABB box = new AABB(at.x - radius, at.y - 64, at.z - radius, at.x + radius, at.y + 64, at.z + radius);
		double r2 = (double) radius * radius;
		List<Entity> mod = new ArrayList<>();
		List<Entity> vanilla = new ArrayList<>();
		for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof Mob && e.isAlive())) {
			double dx = e.getX() - at.x;
			double dz = e.getZ() - at.z;
			if (dx * dx + dz * dz > r2) {
				continue;
			}
			if (e instanceof PolishAnimal<?>) {
				mod.add(e);
			} else if (animal(e.getType().getCategory())) {
				vanilla.add(e);
			}
		}
		Census c = new Census();
		for (Entity e : mod) {
			Line line = c.lines.computeIfAbsent(e.getType(), t -> new Line(true));
			line.count++;
			PolishAnimal<?> a = (PolishAnimal<?>) e;
			line.states.merge(a.state(), 1, Integer::sum);
			line.groups.merge(a.herdId(), 1, Integer::sum);
			for (String look : a.looks()) {
				line.looks.merge(look, 1, Integer::sum);
			}
			c.total++;
		}
		for (Entity e : vanilla) {
			c.lines.computeIfAbsent(e.getType(), t -> new Line(false)).count++;
			c.total++;
		}
		return c;
	}

	private static boolean animal(MobCategory category) {
		return category == MobCategory.CREATURE || category == MobCategory.AMBIENT || category == MobCategory.AXOLOTLS
				|| category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
				|| category == MobCategory.UNDERGROUND_WATER_CREATURE;
	}
}
