package pl.polishforests.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import pl.polishforests.PolishForests;

/**
 * The part of the registries that goes into the saved worlds (rule ZW-4, decision PW-11 of docs/04-pionowy-wycinek.md):
 * every block of the mod with its properties, their values in order and the default state, every item with the
 * block it places, and (from step V8) every entity type with its category and size. {@code RegistrySnapshotTest} compares these lines with {@code src/test/resources/registry_snapshot.txt}
 * (regenerated on purpose with {@code -PwriteRegistrySnapshot}, like the golden terrain), and the trunk samples game
 * test compares the registry of the running game with the same file. After checkpoint CP-B the file is frozen: new
 * blocks, items and property values only at the end of their lists, new properties with a default matching old saves.
 */
public final class RegistrySnapshot {
	public static final String HEADER = "# Blocks, properties, values, items and entity types of polishforests that go into saved worlds (PW-11). "
			+ "Regenerate on purpose: ./gradlew test --tests '*RegistrySnapshotTest*' -PwriteRegistrySnapshot";

	private RegistrySnapshot() {
	}

	/** The snapshot lines of the registries as they are now, sorted by id; the first line is {@link #HEADER}. */
	public static List<String> lines() {
		List<String> out = new ArrayList<>();
		out.add(HEADER);
		List<Map.Entry<Identifier, Block>> blocks = BuiltInRegistries.BLOCK.entrySet().stream()
				.filter(e -> e.getKey().identifier().getNamespace().equals(PolishForests.MOD_ID))
				.map(e -> Map.entry(e.getKey().identifier(), e.getValue()))
				.sorted(Comparator.comparing(e -> e.getKey().toString())).toList();
		for (Map.Entry<Identifier, Block> e : blocks) {
			Block block = e.getValue();
			List<BlockState> states = block.getStateDefinition().getPossibleStates();
			out.add("block " + e.getKey() + " states=" + states.size() + " default=" + describe(block.defaultBlockState()));
			for (Property<?> p : block.getStateDefinition().getProperties().stream()
					.sorted(Comparator.comparing(Property::getName)).toList()) {
				out.add("  property " + p.getName() + ": " + values(p));
			}
		}
		BuiltInRegistries.ITEM.entrySet().stream()
				.filter(e -> e.getKey().identifier().getNamespace().equals(PolishForests.MOD_ID))
				.sorted(Comparator.comparing(e -> e.getKey().identifier().toString()))
				.forEach(e -> {
					Item item = e.getValue();
					String placed = item instanceof BlockItem bi ? " block=" + BuiltInRegistries.BLOCK.getKey(bi.getBlock()) : "";
					out.add("item " + e.getKey().identifier() + placed);
				});
		BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
				.filter(e -> e.getKey().identifier().getNamespace().equals(PolishForests.MOD_ID))
				.sorted(Comparator.comparing(e -> e.getKey().identifier().toString()))
				.forEach(e -> {
					EntityType<?> type = e.getValue();
					out.add(String.format(Locale.ROOT, "entity %s category=%s size=%.2fx%.2f", e.getKey().identifier(),
							type.getCategory().getName(), type.getWidth(), type.getHeight()));
				});
		return out;
	}

	/** Number of block states of the mod (step V2: 84 per trunk block). */
	public static int stateCount() {
		return BuiltInRegistries.BLOCK.entrySet().stream()
				.filter(e -> e.getKey().identifier().getNamespace().equals(PolishForests.MOD_ID))
				.mapToInt(e -> e.getValue().getStateDefinition().getPossibleStates().size()).sum();
	}

	private static <T extends Comparable<T>> String values(Property<T> p) {
		return p.getPossibleValues().stream().map(p::getName).collect(Collectors.joining(" "));
	}

	private static String describe(BlockState state) {
		return state.getBlock().getStateDefinition().getProperties().stream()
				.sorted(Comparator.comparing(Property::getName))
				.map(p -> p.getName() + "=" + name(state, p)).collect(Collectors.joining(","));
	}

	private static <T extends Comparable<T>> String name(BlockState state, Property<T> p) {
		return p.getName(state.getValue(p));
	}
}
