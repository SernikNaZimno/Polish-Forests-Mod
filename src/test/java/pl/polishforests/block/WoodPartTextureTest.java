package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The derived textures of the doors, trapdoors, signs and shelves (step FA7, {@link WoodPartTexture}) on textures drawn in
 * the test (no vanilla texture): a "door" of brown wood pixels of several brightnesses around and outside the brightness
 * range of a drawn "planks", with gray iron, a blue chain and transparent holes.
 */
class WoodPartTextureTest {
	private static final int W = 16;
	private static final int H = 16;

	/** Planks of brown tones, brightness range about 80 … 160. */
	private static int[] planks() {
		int[] px = new int[W * H];
		for (int i = 0; i < px.length; i++) {
			int v = 80 + (i * 37 % 81);
			px[i] = argb(255, brown(v));
		}
		return px;
	}

	/** A part: brown wood of brightness 40 … 220, gray iron, a blue chain, transparent holes. */
	private static int[] part() {
		int[] px = new int[W * H];
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				int i = y * W + x;
				if (x == 0 && y < 4) {
					px[i] = 0;
				} else if (x == 15 && y == 8) {
					px[i] = argb(255, 0x6b6f7a);
				} else if (x == 14 && y == 8) {
					px[i] = argb(255, 0x4060a0);
				} else {
					px[i] = argb(255, brown(40 + (i * 11) % 181));
				}
			}
		}
		return px;
	}

	/** A brown of about this brightness (hue about 30 degrees, saturation about 0.45). */
	private static int brown(int v) {
		int r = Math.min(255, (int) Math.round(v * 1.25));
		int g = Math.min(255, (int) Math.round(v * 0.98));
		int b = Math.min(255, (int) Math.round(v * 0.68));
		return (r << 16) | (g << 8) | b;
	}

	private static int argb(int a, int rgb) {
		return (a << 24) | rgb;
	}

	@Test
	void woodPixelsAreBrownsOnly() {
		assertTrue(WoodPartTexture.wood(argb(255, brown(100))));
		assertTrue(WoodPartTexture.wood(argb(255, 0x6b4f2f)));
		assertFalse(WoodPartTexture.wood(argb(255, 0x6b6f7a)), "gray iron");
		assertFalse(WoodPartTexture.wood(argb(255, 0x6f6f6f)), "gray");
		assertFalse(WoodPartTexture.wood(argb(255, 0x4060a0)), "blue chain");
		assertFalse(WoodPartTexture.wood(argb(255, 0x40a040)), "green");
		assertFalse(WoodPartTexture.wood(argb(0, brown(100))), "transparent");
		assertFalse(WoodPartTexture.wood(argb(255, 0x000000)), "black");
	}

	@Test
	void woodTakesThePaletteTheRestStays() {
		int[] palette = WoodSet.BEECH.planksPalette();
		int[] in = part();
		int[] out = WoodPartTexture.derive(in, W, H, planks(), W, H, palette);
		assertEquals(in.length, out.length);
		assertArrayEquals(out, WoodPartTexture.derive(in, W, H, planks(), W, H, palette), "deterministic");
		Set<Integer> ramp = new HashSet<>();
		for (int s = 0; s <= 1000; s++) {
			ramp.add(PlanksTexture.ramp(palette, s / 1000.0));
		}
		double lo = Double.MAX_VALUE;
		double hi = 0;
		for (int c : planks()) {
			lo = Math.min(lo, PlanksTexture.luminance(c));
			hi = Math.max(hi, PlanksTexture.luminance(c));
		}
		int onRamp = 0;
		for (int i = 0; i < in.length; i++) {
			assertEquals(in[i] >>> 24, out[i] >>> 24, "alpha kept at " + i);
			if (!WoodPartTexture.wood(in[i])) {
				assertEquals(in[i], out[i], "non-wood pixel kept at " + i);
				continue;
			}
			double l = PlanksTexture.luminance(in[i]);
			if (l >= lo && l <= hi) {
				assertTrue(near(ramp, out[i] & 0xffffff), "on the palette ramp at " + i + ": " + Integer.toHexString(out[i]));
				onRamp++;
			}
		}
		assertTrue(onRamp > 50, "wood pixels inside the planks range: " + onRamp);
		// The iron and the chain kept, the holes kept.
		assertEquals(in[8 * W + 15], out[8 * W + 15]);
		assertEquals(in[8 * W + 14], out[8 * W + 14]);
		assertEquals(0, out[0]);
	}

	@Test
	void brightnessOrderIsKept() {
		int[] palette = WoodSet.LARCH.planksPalette();
		int[] in = part();
		int[] out = WoodPartTexture.derive(in, W, H, planks(), W, H, palette);
		Integer[] idx = new Integer[in.length];
		for (int i = 0; i < idx.length; i++) {
			idx[i] = i;
		}
		Arrays.sort(idx, (a, b) -> Double.compare(PlanksTexture.luminance(in[a]), PlanksTexture.luminance(in[b])));
		double previous = -1;
		for (int i : idx) {
			if (WoodPartTexture.wood(in[i])) {
				double l = PlanksTexture.luminance(out[i]);
				assertTrue(l >= previous - 1.0, "darker wood stays darker: " + l + " after " + previous);
				previous = Math.max(previous, l);
			}
		}
		// Darker than the planks: darker than the darkest tone; lighter: lighter than the lightest.
		int dark = WoodPartTexture.tone(40, 80, 160, palette);
		int light = WoodPartTexture.tone(220, 80, 160, palette);
		assertTrue(PlanksTexture.luminance(dark) < PlanksTexture.luminance(palette[0]));
		assertTrue(PlanksTexture.luminance(light) > PlanksTexture.luminance(palette[palette.length - 1]));
		assertEquals(0, WoodPartTexture.tone(0, 80, 160, palette), "black stays black");
	}

	@Test
	void everySpeciesDiffersAndOddInputsWork() {
		Set<Integer> hashes = new HashSet<>();
		for (WoodSet set : WoodSet.values()) {
			hashes.add(Arrays.hashCode(WoodPartTexture.derive(part(), W, H, planks(), W, H, set.planksPalette())));
		}
		assertEquals(WoodSet.values().length, hashes.size());
		// A transparent planks base: nothing to measure, the part stays.
		int[] in = part();
		assertArrayEquals(in, WoodPartTexture.derive(in, W, H, new int[W * H], W, H, WoodSet.ASH.planksPalette()));
		// A planks animation strip counts by its first frame; a part of another size keeps its size.
		int[] strip = new int[W * H * 2];
		System.arraycopy(planks(), 0, strip, 0, W * H);
		Arrays.fill(strip, W * H, strip.length, argb(255, brown(250)));
		assertArrayEquals(WoodPartTexture.derive(in, W, H, planks(), W, H, WoodSet.ASH.planksPalette()),
				WoodPartTexture.derive(in, W, H, strip, W, 2 * H, WoodSet.ASH.planksPalette()));
		int[] wide = new int[24 * 26];
		Arrays.fill(wide, argb(255, brown(120)));
		assertEquals(24 * 26, WoodPartTexture.derive(wide, 24, 26, planks(), W, H, WoodSet.ASH.planksPalette()).length);
		assertNotEquals(wide[0], WoodPartTexture.derive(wide, 24, 26, planks(), W, H, WoodSet.ASH.planksPalette())[0]);
		assertThrows(IllegalArgumentException.class, () -> WoodPartTexture.derive(new int[10], W, H, planks(), W, H,
				WoodSet.ASH.planksPalette()));
	}

	/**
	 * Step FA8: a chest boat is recolored only where it equals the boat texture at the same position (the hull): its own
	 * pixels (the chest, of brown wood too) and the rows below the boat texture stay vanilla's.
	 */
	@Test
	void theChestBoatKeepsItsChest() {
		int[] boat = part();
		int tall = 2 * H;
		int[] chestBoat = new int[W * tall];
		for (int y = 0; y < tall; y++) {
			for (int x = 0; x < W; x++) {
				// The upper half is the boat, but for a square of chest wood; the lower half is chest wood only.
				boolean chest = y >= H || x >= 4 && x < 8 && y >= 4 && y < 8;
				chestBoat[y * W + x] = chest ? argb(255, brown(60 + (x * 7 + y * 13) % 120)) : boat[y * W + x];
			}
		}
		int[] palette = WoodSet.BEECH.planksPalette();
		int[] derivedBoat = WoodPartTexture.derive(boat, W, H, planks(), W, H, palette);
		int[] out = WoodPartTexture.deriveShared(chestBoat, W, tall, boat, W, H, planks(), W, H, palette);
		int changed = 0;
		for (int y = 0; y < tall; y++) {
			for (int x = 0; x < W; x++) {
				int i = y * W + x;
				boolean hull = y < H && chestBoat[i] == boat[i];
				if (hull) {
					assertEquals(derivedBoat[i], out[i], "the hull as the boat at " + x + ", " + y);
					changed += out[i] != chestBoat[i] ? 1 : 0;
				} else {
					assertEquals(chestBoat[i], out[i], "the chest stays at " + x + ", " + y);
				}
			}
		}
		assertTrue(changed > W * H / 2, "recolored hull pixels: " + changed);
		assertEquals(WoodPartTexture.Part.BOAT, WoodPartTexture.Part.CHEST_BOAT.shared());
		assertEquals(WoodPartTexture.Part.BOAT_ITEM, WoodPartTexture.Part.CHEST_BOAT_ITEM.shared());
		assertEquals(null, WoodPartTexture.Part.BOAT.shared());
		assertEquals("textures/entity/chest_boat/oak.png", WoodPartTexture.Part.CHEST_BOAT.path("oak"));
		assertEquals("item/beech_chest_boat", WoodPartTexture.Part.CHEST_BOAT_ITEM.path("beech"));
		assertTrue(WoodPartTexture.Kind.ENTITY.ownFile() && WoodPartTexture.Kind.SCREEN.ownFile());
		assertFalse(WoodPartTexture.Kind.ITEM.ownFile());
	}

	@Test
	void partPathsAndBases() {
		assertEquals("block/beech_door_top", WoodPartTexture.Part.DOOR_TOP.path("beech"));
		assertEquals("item/beech_hanging_sign", WoodPartTexture.Part.HANGING_SIGN_ITEM.path("beech"));
		assertEquals("textures/gui/hanging_signs/oak.png", WoodPartTexture.Part.HANGING_SIGN_SCREEN.path("oak"));
		assertEquals("spruce", WoodSet.SCOTS_PINE.partsBase());
		assertEquals("oak", WoodSet.BEECH.partsBase());
		assertEquals("spruce", WoodSet.LARCH.partsBase());
	}

	private static boolean near(Set<Integer> ramp, int rgb) {
		for (int c : ramp) {
			if (Math.abs(((c >> 16) & 0xff) - ((rgb >> 16) & 0xff)) <= 1 && Math.abs(((c >> 8) & 0xff) - ((rgb >> 8) & 0xff)) <= 1
					&& Math.abs((c & 0xff) - (rgb & 0xff)) <= 1) {
				return true;
			}
		}
		return false;
	}
}
