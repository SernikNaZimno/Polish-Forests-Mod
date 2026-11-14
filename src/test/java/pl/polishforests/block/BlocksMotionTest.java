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
 * as the tag merges in the game (the copies in the game jar and in the generated resources of the mod, with the tags they
 * name). The MOTION_BLOCKING heightmaps of 26.3 go by {@code #blocks_motion_in_heightmap} (= {@code #blocks_motion}); a
 * solid block outside it lets rain, snow and the spawns fall through it (the Tatra rocks and the scree until TT5).
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
		List<String> missing = new ArrayList<>();
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
		}
		assertTrue(solid > 50, "solid blocks of the mod: " + solid);
		assertTrue(missing.isEmpty(), "solid blocks of the mod outside #minecraft:blocks_motion: " + missing);
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
