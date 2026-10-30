package pl.polishforests.gametest;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl;
import pl.polishforests.PolishForests;

/**
 * Stall watchdog of the client gametests (the user's request of 2026-10-29: "waiting 60 s is too long", review round 1 of
 * Z2). A daemon thread in the game watches whether the tests make progress and ends a hung run within
 * {@value #STALL_SECONDS_DEFAULT} s, with a full thread dump, instead of a run script waiting for a long silence in the log.
 *
 * <p><b>Progress</b> is a game tick: Fabric's client gametests run the client, the server and the test thread in lockstep
 * through {@link ThreadingImpl#PHASER}, so every wait of a test ({@code waitTicks}, {@code waitFor}, {@link ViewReady})
 * advances its phase 20 times a second, and a hang of any of the three threads (a server stuck inside its tick, a client
 * that stopped drawing, a world that does not close) stops it. Without ticks a run is still healthy while the test does its
 * own work: the test thread is running (a search, a census, a frame comparison) or the client or server thread runs a
 * test task ({@code runOnClient}, {@code computeOnServer}) or the client loads a world (the integrated server starts), and
 * the process uses at least {@value #BUSY_CORES} of a core.
 * <ul>
 * <li><b>Stall</b>: no tick for {@code stallSeconds} (default {@value #STALL_SECONDS_DEFAULT}, {@code -PgametestStall=<s>})
 * and no test work, or no tick for {@value #BUSY_SECONDS} s even with test work (a loop that never ends). The watchdog
 * writes every thread's full stack and locks to {@code build/gametest-stall/stall-<time>.txt}, logs the test, client and
 * server threads, and halts the game with exit status {@value #STALL_EXIT} (the Gradle task fails).
 * <li><b>Heartbeat</b>: every {@value #HEARTBEAT_SECONDS} s one line {@code [watchdog] alive: …} with the ticks of that
 * period and the test's current step (its innermost frame in this package), so a log shows progress even in a long silent
 * wait; while there are no ticks the line says what work is running instead.
 * <li><b>Gaps</b>: every pause of {@value #GAP_REPORT_SECONDS} s or more without a tick is logged when it ends, with
 * whether test work ran in it ({@code [watchdog] ticks resumed after …}); these lines calibrate the limit.
 * </ul>
 * {@code -PgametestStall=0} turns it off; it is also off under a debugger (a breakpoint stops the ticks), and it watches
 * only while a test thread exists (not while the game starts or after the last test).
 */
public final class GameTestWatchdog implements ClientModInitializer {
	static final int STALL_SECONDS_DEFAULT = 20;
	static final int BUSY_SECONDS = 600;
	static final double BUSY_CORES = 0.1;
	static final int HEARTBEAT_SECONDS = 10;
	static final double GAP_REPORT_SECONDS = 3;
	static final int STALL_EXIT = 3;

	private static final String TASK_FRAME = "runTaskOnThisThread";
	/** The client starting the integrated server of a new or loaded world (no ticks while the server starts). */
	private static final String LOAD_FRAME = "doWorldLoad";
	private static final String SAVE_FRAME = "stopServer";

	@Override
	public void onInitializeClient() {
		int stall = Integer.getInteger("polishforests.gametest.stallSeconds", STALL_SECONDS_DEFAULT);
		boolean debugger = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().anyMatch(a -> a.contains("jdwp"));
		if (stall <= 0 || debugger) {
			PolishForests.LOG.info("[watchdog] off ({})", debugger ? "debugger attached" : "stall limit 0");
			return;
		}
		Path dir = Path.of(System.getProperty("polishforests.gametest.stallDir", "gametest-stall"));
		Thread t = new Thread(() -> new Watch(stall, dir).run(), "PolishForests gametest watchdog");
		t.setDaemon(true);
		t.start();
		PolishForests.LOG.info("[watchdog] on: a run without a game tick for {} s and without test work ends with a thread dump in {}",
				stall, dir.toAbsolutePath());
	}

	/** The watching loop, one sample a second. */
	private static final class Watch {
		private final int stall;
		private final Path dir;
		private final com.sun.management.OperatingSystemMXBean os =
				(com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		/** Process CPU time (ns) at each of the last seconds, a ring indexed by the second. */
		private final long[] cpu = new long[BUSY_SECONDS + 2];

		Watch(int stall, Path dir) {
			this.stall = stall;
			this.dir = dir;
		}

		void run() {
			int phase = Integer.MIN_VALUE;
			long lastTick = System.nanoTime();
			long lastBeat = lastTick;
			int ticksSinceBeat = 0;
			boolean workInGap = false;
			long second = 0;
			Thread watched = null;
			while (true) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					return;
				}
				second++;
				cpu[(int) (second % cpu.length)] = os.getProcessCpuTime();
				long now = System.nanoTime();
				Thread test = ThreadingImpl.testThread;
				if (test == null || !test.isAlive()) {
					watched = null;
					lastTick = now;
					lastBeat = now;
					continue;
				}
				if (test != watched) {
					watched = test;
					phase = ThreadingImpl.PHASER.getPhase();
					lastTick = now;
					lastBeat = now;
					ticksSinceBeat = 0;
				}
				int p = ThreadingImpl.PHASER.getPhase();
				double gap = (now - lastTick) / 1e9;
				if (p != phase) {
					// A tick has two phases (tick and test); count phases / 2 as ticks.
					ticksSinceBeat += Math.max(1, Math.floorMod(p - phase, Integer.MAX_VALUE) / 2);
					phase = p;
					if (gap >= GAP_REPORT_SECONDS) {
						PolishForests.LOG.info(String.format(Locale.ROOT, "[watchdog] ticks resumed after %.0f s without a tick (test work: %s)",
								gap, workInGap ? "yes" : "no"));
					}
					lastTick = now;
					gap = 0;
					workInGap = false;
				}
				String work = gap >= 1 ? testWork(test) : null;
				workInGap |= work != null;
				int window = (int) Math.min(Math.max(1, Math.floor(gap)), Math.min(stall, second - 1));
				double cores = window <= 0 ? 0
						: (cpu[(int) (second % cpu.length)] - cpu[(int) ((second - window) % cpu.length)]) / 1e9 / window;
				if (gap >= stall && (work == null || cores < BUSY_CORES)) {
					stalled(test, gap, work == null ? "no test work" : String.format(Locale.ROOT, "test work idle (%.2f cores)", cores));
				} else if (gap >= BUSY_SECONDS) {
					stalled(test, gap, "test work running for " + BUSY_SECONDS + " s without a tick");
				}
				if ((now - lastBeat) / 1e9 >= HEARTBEAT_SECONDS - 0.5) {
					if (gap >= 1) {
						PolishForests.LOG.info(String.format(Locale.ROOT, "[watchdog] alive: no tick for %.0f s, %s, %.2f cores; test at %s",
								gap, work == null ? "no test work" : work, cores, step(test)));
					} else {
						PolishForests.LOG.info(String.format(Locale.ROOT, "[watchdog] alive: %d ticks in %.0f s; test at %s", ticksSinceBeat,
								(now - lastBeat) / 1e9, step(test)));
					}
					lastBeat = now;
					ticksSinceBeat = 0;
				}
			}
		}

		/**
		 * The test work running without ticks, or null: the test thread running, a test task on another thread, or the client
		 * loading a world (the server starting; about 3 s in the seasons runs, longer with a heavier world or mod set), or
		 * the server saving at the close of a world (over 20 s after a census of thousands of new chunks, step O2).
		 */
		private static String testWork(Thread test) {
			if (test.getState() == Thread.State.RUNNABLE) {
				return "test thread running";
			}
			for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
				for (StackTraceElement f : e.getValue()) {
					if (f.getMethodName().equals(TASK_FRAME) && f.getClassName().equals(ThreadingImpl.class.getName())) {
						return "test task on " + e.getKey().getName();
					}
					if (f.getMethodName().equals(LOAD_FRAME) && f.getClassName().equals("net.minecraft.client.Minecraft")) {
						return "world loading";
					}
					if (f.getMethodName().equals(SAVE_FRAME) && f.getClassName().equals("net.minecraft.server.MinecraftServer")) {
						return "world saving";
					}
				}
			}
			return null;
		}

		/** The innermost frame of the test thread in this package (the test's current step). */
		private static String step(Thread test) {
			for (StackTraceElement f : test.getStackTrace()) {
				if (f.getClassName().startsWith("pl.polishforests.gametest.") && !f.getClassName().contains("GameTestWatchdog")) {
					String c = f.getClassName();
					return c.substring(c.lastIndexOf('.') + 1) + "." + f.getMethodName() + ":" + f.getLineNumber();
				}
			}
			return "(outside the tests)";
		}

		private void stalled(Thread test, double gap, String why) {
			String head = String.format(Locale.ROOT, "[watchdog] STALL: no game tick for %.0f s (%s); test at %s", gap, why, step(test));
			PolishForests.LOG.error(head);
			Path file = dir.resolve("stall-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
			try {
				Files.createDirectories(dir);
				try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
					out.println(head);
					for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
						out.print(dump(info));
					}
				}
				PolishForests.LOG.error("[watchdog] thread dump: {}", file.toAbsolutePath());
			} catch (IOException e) {
				PolishForests.LOG.error("[watchdog] thread dump not written: {}", e.toString());
			}
			for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
				String n = info.getThreadName();
				if (n.equals("Test thread") || n.equals("Render thread") || n.equals("Server thread")) {
					PolishForests.LOG.error("[watchdog] {}", dump(info).stripTrailing());
				}
			}
			System.out.flush();
			System.err.flush();
			Runtime.getRuntime().halt(STALL_EXIT);
		}

		/** One thread with its whole stack and locks (ThreadInfo.toString cuts the stack at 8 frames). */
		private static String dump(ThreadInfo info) {
			StringBuilder b = new StringBuilder();
			b.append('"').append(info.getThreadName()).append("\" ").append(info.getThreadState());
			if (info.getLockName() != null) {
				b.append(" on ").append(info.getLockName());
			}
			if (info.getLockOwnerName() != null) {
				b.append(" owned by \"").append(info.getLockOwnerName()).append('"');
			}
			b.append('\n');
			StackTraceElement[] stack = info.getStackTrace();
			MonitorInfo[] monitors = info.getLockedMonitors();
			for (int i = 0; i < stack.length; i++) {
				b.append("\tat ").append(stack[i]).append('\n');
				for (MonitorInfo m : monitors) {
					if (m.getLockedStackDepth() == i) {
						b.append("\t- locked ").append(m).append('\n');
					}
				}
			}
			for (LockInfo l : info.getLockedSynchronizers()) {
				b.append("\t- holds ").append(l).append('\n');
			}
			return b.append('\n').toString();
		}
	}
}
