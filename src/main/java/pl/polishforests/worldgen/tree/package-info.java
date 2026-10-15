/**
 * Procedural trees and stands (vertical slice, docs/04-pionowy-wycinek.md), pure Java without game classes where
 * possible (rule ZW-1): from step V5 the crown territories, from V6 the tree shape planner ({@code TreeShapePlan}:
 * skeleton, pipe model, voxelization), from V9 the stand map ({@code StandMap}: managed and natural stands, age classes,
 * gaps), from V11 the dead wood. New noise fields take new salts (rule ZW-2); the trunk candidate pattern of M2 stays.
 */
package pl.polishforests.worldgen.tree;
