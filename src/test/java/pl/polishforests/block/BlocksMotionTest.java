package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Step TT5: every block of the mod whose default state is a full cube of collision is in {@code #minecraft:blocks_motion},
 * and every such block outside {@code #minecraft:leaves} also in {@code #minecraft:blocks_motion_no_leaves}, as the tags
 * merge in the game (the copies in the game jar and in the generated resources of the mod, with the tags they name). The
 * heightmaps of 26.3 go by {@code #blocks_motion_in_heightmap} (= {@code #blocks_motion}: MOTION_BLOCKING, OCEAN_FLOOR,
 * OCEAN_FLOOR_WG) and {@code #blocks_motion_in_heightmap_no_leaves} (= {@code #blocks_motion_no_leaves}:
 * MOTION_BLOCKING_NO_LEAVES, which the spawns read); a solid block outside them lets rain, snow and the spawns fall through
 * it (the Tatra rocks and the scree until TT5, MOTION_BLOCKING_NO_LEAVES until the TT5 review, round 1).
 */
class BlocksMotionTest {
	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void solidBlocksOfTheModBlockMotion() {
		Set<String> motion = new HashSet<>();
		collect("minecraft:blocks_motion", motion, new HashSet<>());
		Set<String> noLeaves = new HashSet<>();
		collect("minecraft:blocks_motion_no_leaves", noLeaves, new HashSet<>());
		Set<String> leaves = new HashSet<>();
		collect("minecraft:leaves", leaves, new HashSet<>());
		List<String> missing = new ArrayList<>();
		List<String> missingNoLeaves = new ArrayList<>();
		int solid = 0;
		for (Block b : ModBlocks.all()) {
			BlockState state = b.defaultBlockState();
			if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
				continue;
			}
			solid++;
			String id = BuiltInRegistries.BLOCK.getKey(b).toString();
			if (!motion.contains(id)) {
				missing.add(id);
			}
			if (!leaves.contains(id) && !noLeaves.contains(id)) {
				missingNoLeaves.add(id);
			}
		}
		assertTrue(solid > 50, "solid blocks of the mod: " + solid);
		assertTrue(missing.isEmpty(), "solid blocks of the mod outside #minecraft:blocks_motion: " + missing);
		assertTrue(missingNoLeaves.isEmpty(),
				"solid blocks of the mod outside #minecraft:leaves and #minecraft:blocks_motion_no_leaves: " + missingNoLeaves);
	}

	/** The block ids of a tag and of the tags it names, from every copy of each on the class path. */
	private static void collect(String tag, Set<String> out, Set<String> seen) {
		if (!seen.add(tag)) {
			return;
		}
		int colon = tag.indexOf(':');
		String path = "data/" + tag.substring(0, colon) + "/tags/block/" + tag.substring(colon + 1) + ".json";
		List<URL> copies;
		try {
			copies = Collections.list(BlocksMotionTest.class.getClassLoader().getResources(path));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		assertTrue(!copies.isEmpty(), "no tag file " + path);
		for (URL url : copies) {
			try (InputStreamReader r = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
				for (JsonElement e : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("values")) {
					String v = e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString();
					if (v.startsWith("#")) {
						collect(v.substring(1), out, seen);
					} else {
						out.add(v);
					}
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}
