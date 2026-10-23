// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** AI-INTENT: positions were resolved by Cuetree; equal audio IDs do not erase declared positions.
 * https://wiki.271.pics/Riff/LATEST/album-grouping-positions#consumers-and-publication
 */
internal fun orderedAlbumTrackIds(
    nodes: List<Node>,
    sources: List<Int>,
): List<MediaId> = sources.map { nodes[it].id }
