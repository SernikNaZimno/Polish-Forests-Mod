package pl.polishforests.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * The climate mixin targets exist in {@link Biome} of this game version. Unit tests run without
 * mixins, so without this check a signature change after a Minecraft update would only show up at game start.
 */
class MixinTargetsTest {
	@Test
	void injectTargetsExistInBiome() {
		int found = 0;
		for (Class<?> mixin : List.of(BiomeTemperatureMixin.class, BiomeFreezeMixin.class)) {
			// @Mixin is not visible at runtime (CLASS retention), @Inject is.
			for (Method handler : mixin.getDeclaredMethods()) {
				Inject inject = handler.getAnnotation(Inject.class);
				if (inject == null) {
					continue;
				}
				for (String target : inject.method()) {
					assertTrue(hasMethod(target), "Missing target " + target + " in Biome (" + mixin.getSimpleName() + ")");
					found++;
				}
			}
		}
		assertEquals(2, found);
	}

	/** Whether {@link Biome} declares a method with the name and descriptor from {@code name(descriptor)}. */
	private static boolean hasMethod(String target) {
		int paren = target.indexOf('(');
		assertTrue(paren > 0, "Target without a descriptor: " + target);
		String name = target.substring(0, paren);
		String descriptor = target.substring(paren);
		List<String> candidates = new ArrayList<>();
		for (Method m : Biome.class.getDeclaredMethods()) {
			if (m.getName().equals(name)) {
				String d = MethodType.methodType(m.getReturnType(), m.getParameterTypes()).toMethodDescriptorString();
				if (d.equals(descriptor)) {
					return true;
				}
				candidates.add(d);
			}
		}
		System.out.println("Biome." + name + ": " + candidates);
		return false;
	}
}
