package pl.polishforests.worldgen.chunk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** The persistent chunk habitats attachment with the cave mask of step J1, and attachments saved before it. */
class ChunkHabitatsTest {
	private static ChunkHabitats habitats(long[] caveOpened) {
		int[] codes = new int[256];
		short[] top = new short[256];
		short[] water = new short[256];
		for (int i = 0; i < 256; i++) {
			codes[i] = i * 7;
			top[i] = (short) (100 + i % 13);
			water[i] = i % 5 == 0 ? (short) (top[i] + 2) : ChunkHabitats.NO_WATER;
		}
		return new ChunkHabitats(codes, top, water, 0.25F, 0.5F, ChunkHabitats.NO_STRUCTURES, caveOpened);
	}

	@Test
	void caveMaskRoundTrips() {
		long[] opened = new long[4];
		opened[0] = 1L << 3;
		opened[3] = 1L << 63;
		ChunkHabitats h = habitats(opened);
		JsonElement json = ChunkHabitats.CODEC.encodeStart(JsonOps.INSTANCE, h).getOrThrow();
		ChunkHabitats back = ChunkHabitats.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertArrayEquals(opened, back.caveOpened());
		assertArrayEquals(h.top(), back.top());
		assertTrue(back.caveOpened(3));
		assertTrue(back.caveOpened(255));
		assertFalse(back.caveOpened(4));
	}

	@Test
	void attachmentsSavedBeforeTheCavesHaveNoOpenedTops() {
		JsonObject json = ChunkHabitats.CODEC.encodeStart(JsonOps.INSTANCE, habitats(new long[] {1, 2, 3, 4})).getOrThrow()
				.getAsJsonObject();
		json.remove("cave_opened");
		ChunkHabitats old = ChunkHabitats.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertArrayEquals(ChunkHabitats.NO_CAVES, old.caveOpened());
		for (int i = 0; i < 256; i++) {
			assertFalse(old.caveOpened(i));
		}
	}
}
