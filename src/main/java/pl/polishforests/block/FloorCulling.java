package pl.polishforests.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fewer hidden faces on the forest floor and in the ground (step O2 of docs/04-pionowy-wycinek.md, DP-38; the frame rate
 * over the pine forest's canopy fell from V10 to V13 with the moss, the litter and the roots), without a visible change:
 * <ul>
 * <li><b>Moss layer of the pine forest floor</b> ({@link BerryShrubBlock#MOSSY}, {@link GroundCoverBlock#MOSSY}): a
 * plant with the mossy underside occludes like the moss carpet it draws ({@link #MOSS_LAYER}, the vanilla carpet's 1 px
 * box), so the renderers cull the four sides of the layer between two mossy plants and the top of the ground under it,
 * as they do between vanilla carpets. The layer's texture is opaque; light, collision, support and the other shapes of
 * the plant are unchanged (the light engine ignores occlusion shapes without {@code useShapeForLightOcclusion}). Before
 * O2 every mossy floor block drew the top of the ground and four sides of its layer, five faces nobody could see.
 * <li><b>Buried roots</b> ({@code tree_root}): the client draws nothing for a root block that no camera can see
 * ({@link #hiddenRoot}): every neighbor is an opaque full block, or a root block whose own neighbors are all opaque full
 * blocks or roots. A root is a thin trunk of 27 faces in a block of soil; almost all of them lie under at least two
 * blocks of ground (step I1).
 * </ul>
 * {@code -Dpolishforests.floorCulling=off} ({@code -PfloorCulling=off} of the game tests) draws both as before O2.
 */
public final class FloorCulling {
	/** System property of the switch. */
	public static final String PROPERTY = "polishforests.floorCulling";
	/** Whether the culling is on (read once). */
	public static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "").trim());
	/** The occlusion shape of a plant with the mossy underside: the moss layer, as vanilla's carpet. */
	public static final VoxelShape MOSS_LAYER = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

	private FloorCulling() {
	}

	/** The occlusion shape of a floor plant: the moss layer when it has one (and the culling is on), else none. */
	public static VoxelShape plantOcclusion(boolean mossy) {
		return ENABLED && mossy ? MOSS_LAYER : Shapes.empty();
	}

	/** What a neighbor of a root block is for its visibility. */
	public enum Neighbor {
		/** An opaque full block: no camera looks through it. */
		SOLID,
		/** Another root block: hollow around its root, but closed if all its own neighbors are. */
		ROOT,
		/** Anything else (air, water, plants, other thin blocks): a camera may look through it. */
		OPEN
	}

	/** The neighbors of a position as {@link Neighbor}s. */
	@FunctionalInterface
	public interface Neighbors {
		/** The neighbor at offset (dx, dy, dz) from the root block (offsets up to 2 blocks). */
		Neighbor at(int dx, int dy, int dz);
	}

	private static final int[][] SIX = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

	/**
	 * Whether a root block is hidden: each of its six neighbors is {@link Neighbor#SOLID}, or {@link Neighbor#ROOT} with no
	 * {@link Neighbor#OPEN} neighbor of its own. A camera can only look into a root block from an open block next to it or
	 * through the hollow of a root block next to it that touches an open block; a view through two hollow root blocks
	 * along a root (a root cut open in a cave or a trench, the third block in) is left out: the root's own core lies on
	 * that line. The client asks again when a section is meshed, and a block change re-meshes the sections within one
	 * block of it, so a root two blocks from a newly opened block shows from the next mesh of its section.
	 */
	public static boolean hiddenRoot(Neighbors n) {
		if (!ENABLED) {
			return false;
		}
		for (int[] a : SIX) {
			Neighbor first = n.at(a[0], a[1], a[2]);
			if (first == Neighbor.OPEN) {
				return false;
			}
			if (first == Neighbor.ROOT) {
				for (int[] b : SIX) {
					if (b[0] == -a[0] && b[1] == -a[1] && b[2] == -a[2]) {
						continue;
					}
					if (n.at(a[0] + b[0], a[1] + b[1], a[2] + b[2]) == Neighbor.OPEN) {
						return false;
					}
				}
			}
		}
		return true;
	}
}
