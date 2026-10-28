package pl.polishforests.gametest;

import java.util.function.BooleanSupplier;

/**
 * The decision of the adaptive reference frame of {@code -PviewCheck} (step T8, the user's request of 2026-10-28): the
 * reference is the first probe frame at which the image has not changed for the stability window {@code W} and the view
 * is idle (no generation work on the server around the camera, the client's compile queue empty).
 *
 * <p>A <em>change</em> is a probe that differs from the anchor frame by more than the tolerance of {@link FrameDiff} (animated
 * elements stay below it); the changed probe becomes the new anchor, so a slow trickle of small changes adds up against the
 * anchor and is caught as well. Times are ticks since the wait for the view began, as the report prints them.
 */
final class ViewPattern {
	private final int windowTicks;
	private int anchorTick;
	private int lastChangeTick = -1;
	private int changes;
	private int foundTick = -1;

	/** @param startTick the time of the screenshot itself, the first anchor */
	ViewPattern(int startTick, int windowTicks) {
		this.anchorTick = startTick;
		this.windowTicks = windowTicks;
	}

	/**
	 * Feeds one probe frame taken at {@code tick}; {@code idle} is asked only when the window is over.
	 *
	 * @param changed the probe differs from the anchor frame
	 * @return true when this probe is the reference frame
	 */
	boolean accept(int tick, boolean changed, BooleanSupplier idle) {
		if (changed) {
			anchorTick = tick;
			lastChangeTick = tick;
			changes++;
			return false;
		}
		if (tick - anchorTick < windowTicks || !idle.getAsBoolean()) {
			return false;
		}
		if (foundTick < 0) {
			foundTick = tick;
		}
		return true;
	}

	/** The time of the last change of the image after the screenshot, or -1 when it never changed. */
	int lastChangeTick() {
		return lastChangeTick;
	}

	/** The number of changes of the image after the screenshot. */
	int changes() {
		return changes;
	}

	/** Whether a reference frame was found (once {@link #accept} returned true). */
	boolean found() {
		return foundTick >= 0;
	}

	/** The time of the first reference frame found, or -1. */
	int foundTick() {
		return foundTick;
	}
}
