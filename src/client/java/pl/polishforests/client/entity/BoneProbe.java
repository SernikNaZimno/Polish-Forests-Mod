package pl.polishforests.client.entity;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.RenderPassInfo;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bone positions of rendered GeckoLib models, for the fauna game test (step V8: calibration of the axes and the
 * regression test of the bone positions). Off unless a test calls {@link #start()}: then every render pass of an
 * attached renderer reports, through GeckoLib's position listeners, the pivot of every bone ({@code bone:<name>}) and every
 * locator ({@code locator:<name>}) in model space (Bedrock pixels, the axes of the .geo.json) and in the world (blocks),
 * filed under the position of the rendered entity. The latest frame wins.
 */
public final class BoneProbe {
	/** A position of a frame: model space in pixels, world in blocks. */
	public record Point(Vec3 model, @Nullable Vec3 world) {
	}

	private static volatile @Nullable Map<Vec3, Map<String, Point>> frames;

	private BoneProbe() {
	}

	/** Starts recording (clears earlier points). */
	public static void start() {
		frames = new ConcurrentHashMap<>();
	}

	/** Stops recording. */
	public static void stop() {
		frames = null;
	}

	/** The points of the entity rendered at the given position (within 0.01 block), a copy; empty when none. */
	public static Map<String, Point> points(Vec3 entityPosition) {
		Map<Vec3, Map<String, Point>> f = frames;
		if (f == null) {
			return Map.of();
		}
		for (Map.Entry<Vec3, Map<String, Point>> e : f.entrySet()) {
			if (e.getKey().distanceTo(entityPosition) < 0.01) {
				return Map.copyOf(e.getValue());
			}
		}
		return Map.of();
	}

	/** Called by the renderers at the start of a render pass; adds the listeners when recording. */
	public static void attach(RenderPassInfo<?> info) {
		Map<Vec3, Map<String, Point>> f = frames;
		if (f == null) {
			return;
		}
		Vec3 entity = info.renderState().getGeckolibData(DataTickets.POSITION);
		if (entity == null) {
			return;
		}
		Map<String, Point> sink = f.computeIfAbsent(entity, k -> new ConcurrentHashMap<>());
		BakedGeoModel model = info.model();
		for (String bone : model.boneLookup().get().keySet()) {
			info.addBonePositionListener(bone, (world, modelPos, local) -> sink.put("bone:" + bone, new Point(modelPos, world)));
		}
		for (String locator : model.locators().keySet()) {
			info.addLocatorPositionListener(locator, (world, modelPos, local) -> sink.put("locator:" + locator, new Point(modelPos, world)));
		}
	}
}
