package pl.polishforests.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import pl.polishforests.PolishForests;
import pl.polishforests.client.ClientSeasonPhase;
import pl.polishforests.season.FallbackCalendar;
import pl.polishforests.season.SeasonPhase;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;

/**
 * Sets the date of a test world explicitly (V7 review round 1): since step V7 the date decides how the leaves look, and
 * a new world starts in early spring (Serene Seasons' default {@code starting_sub_season}, day 0 of the built-in
 * calendar), so screenshot tests set the season they show: with Serene Seasons by {@code season set}, without it by
 * {@code time set} on the built-in calendar, always at noon. A later change of the time of day must keep the date
 * ({@link #setTimeOfDay}, V7 review round 2).
 */
final class SeasonDates {
	static final int NOON = 6000;

	private SeasonDates() {
	}

	static boolean serene() {
		return FabricLoader.getInstance().isModLoaded("sereneseasons");
	}

	/** The noon of {@code day} (0-11) of a sub-season on the built-in calendar, for {@code time set}. */
	static long noonOf(SubSeason sub, int day) {
		return (sub.ordinal() * (long) Seasons.DEFAULT_DAYS_PER_SUB_SEASON + day) * FallbackCalendar.TICKS_PER_DAY + NOON;
	}

	/** The color step the client should reach (Serene Seasons sets the first day of the sub-season). */
	static int expectedStep(SubSeason sub, int day) {
		return SeasonPhase.firstStep(sub) + (serene() ? 0 : day / 4);
	}

	/** Sets the date on the server (no waiting); logs the sub-season with the test's tag. */
	static void set(TestSingleplayerContext sp, SubSeason sub, int day, String tag) {
		if (serene()) {
			sp.getServer().runCommand("time set " + NOON);
			sp.getServer().runCommand("season set " + sub.name().toLowerCase(Locale.ROOT));
		} else {
			sp.getServer().runCommand("time set " + noonOf(sub, day));
		}
		PolishForests.LOG.info("[{}] date: {} day {} ({}), color step {}", tag, sub, day, serene() ? "Serene Seasons" : "built-in calendar",
				expectedStep(sub, day));
	}

	/**
	 * Sets the time of day ({@code 0-23999}) without changing the date (V7 review round 2). Without Serene Seasons the
	 * built-in calendar reads the date from the overworld clock, so a plain {@code time set <timeOfDay>} would move the world
	 * back to day 0 (early spring); here the day of the clock is kept. With Serene Seasons the season has its own counter
	 * and {@code time set} does not touch it. Throws if the sub-season changed anyway.
	 */
	static void setTimeOfDay(TestSingleplayerContext sp, int timeOfDay, String tag) {
		SubSeason before = sp.getServer().computeOnServer(s -> Seasons.provider().subSeason(s.overworld()));
		if (serene()) {
			sp.getServer().runCommand("time set " + timeOfDay);
		} else {
			long now = sp.getServer().computeOnServer(s -> s.overworld().getOverworldClockTime());
			long day = Math.floorDiv(now, FallbackCalendar.TICKS_PER_DAY);
			sp.getServer().runCommand("time set " + (day * FallbackCalendar.TICKS_PER_DAY + timeOfDay));
		}
		SubSeason after = sp.getServer().computeOnServer(s -> Seasons.provider().subSeason(s.overworld()));
		if (after != before) {
			throw new AssertionError("[" + tag + "] setting the time of day " + timeOfDay + " changed the date: " + before + " -> " + after);
		}
		PolishForests.LOG.info("[{}] time of day {}, date kept: {}", tag, timeOfDay, after);
	}

	/** Sets the date and waits until the client has the color step and the leaf meshes are rebuilt. */
	static void setAndWait(ClientGameTestContext context, TestSingleplayerContext sp, SubSeason sub, int day, String tag) {
		set(sp, sub, day, tag);
		int expected = expectedStep(sub, day);
		context.waitFor(mc -> ClientSeasonPhase.step() == expected, 400);
		context.waitFor(mc -> !ClientSeasonPhase.rebuilding(), 600);
		context.waitFor(mc -> {
			try {
				return mc.levelRenderer.hasRenderedAllSections();
			} catch (RuntimeException e) {
				return false;
			}
		}, 1200);
		context.waitTicks(20);
	}
}
