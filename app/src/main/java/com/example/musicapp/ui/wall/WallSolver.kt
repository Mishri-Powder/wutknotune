package com.example.musicapp.ui.wall

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Poster-wall grid solver — a faithful port of Iris Music's PosterWall.kt layout math
 * (packWall + solveWall). Pure functions, zero Android/Compose dependencies, so it can
 * run on Dispatchers.Default and be unit-tested without a device.
 *
 * Model: the wall is a cols x rows cell canvas; every song gets exactly one tile
 * (never duplicated — Iris removed the old modulo-fill that produced ghost copies).
 * Tiles are packed in 2x2 "blocks" with kind 1/2/4 tiles per block, so the base
 * layout is 100% filled with zero holes and zero overlaps.
 *
 * When a song becomes active, its tile expands to 2x2 and neighbors make way:
 * shrink-in-place, relocate, recursive push, then hole-filling passes. Any failure
 * rolls back to the base layout — better an unexpanded card than an overlapping wall.
 */

/** One tile: canvas grid coords + spans in cells. */
class WallTile(
    val index: Int,          // index into the queue/song list
    val col: Int, val row: Int,
    val spanW: Int, val spanH: Int
)

/** Solved geometry for a tile after the make-way solver ran. */
class SolvedTile(
    val tile: WallTile,
    val col: Int, val row: Int,
    val spanW: Int, val spanH: Int,
    val active: Boolean = false
)

/**
 * Base packing: B = qcols x qrows blocks of 2x2 cells; each block holds 1, 2 or 4
 * tiles and always fills its four cells completely. `prefOf` biases big cards
 * toward preferred songs (liked / frequently played), like Iris does.
 *
 * Returns (tiles, canvasRows, canvasCols).
 */
fun packWall(
    count: Int,
    cols: Int,
    seed: Long,
    prefOf: (Int) -> Float = { 0f }
): Triple<List<WallTile>, Int, Int> {
    if (count <= 0) return Triple(emptyList(), 0, 0)
    val qcols = (cols / 2).coerceAtLeast(2)
        .coerceAtMost(ceil(kotlin.math.sqrt(count.toDouble())).toInt().coerceAtLeast(2))
    val minRows = ceil(count / (4.0 * qcols)).toInt().coerceAtLeast(1)
    val maxRows = (count / qcols).coerceAtLeast(1)
    val qrows = (count / (2.0 * qcols)).roundToInt().coerceAtLeast(1).coerceIn(minRows, maxRows)
    val B = qcols * qrows

    var rng = seed
    fun rnd(): Int {
        rng = rng xor (rng shl 13); rng = rng xor (rng shr 7); rng = rng xor (rng shl 17)
        return (rng and 0x7FFFFFFF).toInt()
    }

    // Step 1: give each block a kind in {1,2,4} such that sum(kind) == count exactly.
    val kinds = IntArray(B)
    var remaining = count
    var firstIdx = 0
    for (b in 0 until B) {
        val blocksAfter = B - b - 1
        val lo = blocksAfter
        val hi = blocksAfter * 4
        val cands = ArrayList<Int>(3)
        for (k in intArrayOf(1, 2, 4)) if (remaining - k in lo..hi) cands.add(k)
        val k = if (cands.isEmpty()) {
            if (blocksAfter == 0) remaining.coerceIn(1, 4) else remaining.coerceIn(lo, hi.coerceAtLeast(lo))
        } else if (cands.size > 1) {
            val pref = prefOf(firstIdx).coerceIn(0f, 1f)
            val bigProb = 0.25f + 0.5f * pref
            val maxK = cands.maxOrNull() ?: 1
            if ((rnd() % 1000) / 1000f < bigProb) maxK else cands[rnd() % cands.size]
        } else cands[0]
        kinds[b] = k
        firstIdx += k
        remaining -= k
    }

    // Step 2: lay tiles; idx increases monotonically so every song appears exactly once.
    val tiles = ArrayList<WallTile>(count)
    var idx = 0
    for (b in 0 until B) {
        val qrow = b / qcols
        val qcol = b % qcols
        val col = qcol * 2
        val row = qrow * 2
        when (kinds[b]) {
            1 -> { tiles.add(WallTile(idx, col, row, 2, 2)); idx++ }
            2 -> {
                if (rnd() and 1 == 0) {
                    tiles.add(WallTile(idx, col, row, 2, 1)); idx++
                    tiles.add(WallTile(idx, col, row + 1, 2, 1)); idx++
                } else {
                    tiles.add(WallTile(idx, col, row, 1, 2)); idx++
                    tiles.add(WallTile(idx, col + 1, row, 1, 2)); idx++
                }
            }
            else -> {
                tiles.add(WallTile(idx, col, row, 1, 1)); idx++
                tiles.add(WallTile(idx, col + 1, row, 1, 1)); idx++
                tiles.add(WallTile(idx, col, row + 1, 1, 1)); idx++
                tiles.add(WallTile(idx, col + 1, row + 1, 1, 1)); idx++
            }
        }
    }
    return Triple(tiles, qrows * 2, qcols * 2)
}

/** Localized solve radius (cells) — holes only ever appear near the active neighborhood. */
private const val SOLVE_LOCAL_PAD = 10

/**
 * Make-way solver. Input: base tiles (fully packed, no overlap) + the playing tile.
 * Output: new geometry where the active tile occupies a 2x2 target zone, every other
 * tile keeps a position, nothing overlaps, nothing disappears unless it must
 * (dropped tiles get span 0 and simply render as whitespace — Iris's trade-off for
 * "the playing card ALWAYS expands").
 */
fun solveWall(
    tiles: List<WallTile>,
    cols: Int,
    rows: Int,
    activeIndex: Int
): List<SolvedTile> {
    if (tiles.isEmpty() || cols <= 0 || rows <= 0) {
        return tiles.map { SolvedTile(it, it.col, it.row, it.spanW, it.spanH) }
    }
    fun nc(c: Int) = ((c % cols) + cols) % cols
    fun nr(r: Int) = ((r % rows) + rows) % rows

    val n = tiles.size
    val pc = IntArray(n); val pr = IntArray(n)
    val pw = IntArray(n); val ph = IntArray(n)
    tiles.forEachIndexed { i, t -> pc[i] = t.col; pr[i] = t.row; pw[i] = t.spanW; ph[i] = t.spanH }
    val grid = IntArray(cols * rows) { -1 }
    fun gi(c: Int, r: Int) = nr(r) * cols + nc(c)
    fun stamp(i: Int, v: Int) {
        for (dc in 0 until pw[i]) for (dr in 0 until ph[i]) grid[gi(pc[i] + dc, pr[i] + dr)] = v
    }
    for (i in 0 until n) stamp(i, i)

    // active tile: largest-area copy wins (base packing has one tile per song, but keep
    // the rule anyway so this stays correct if duplicates ever enter the list).
    var ai = -1; var bestArea = -1
    for (i in 0 until n) {
        if (tiles[i].index != activeIndex) continue
        val area = tiles[i].spanW * tiles[i].spanH
        if (area > bestArea) { bestArea = area; ai = i }
    }
    if (ai < 0) {
        return (0 until n).map {
            SolvedTile(tiles[it], tiles[it].col, tiles[it].row, tiles[it].spanW, tiles[it].spanH, false)
        }
    }
    val MAX_SPAN = 2
    val TW = (pw[ai] + 1).coerceIn(2, minOf(cols, MAX_SPAN))
    val TH = (ph[ai] + 1).coerceIn(2, minOf(rows, MAX_SPAN))

    val bc = pc.copyOf(); val br = pr.copyOf(); val bw = pw.copyOf(); val bh = ph.copyOf()
    val bg = grid.copyOf()
    fun rollback() {
        bc.copyInto(pc); br.copyInto(pr); bw.copyInto(pw); bh.copyInto(ph); bg.copyInto(grid)
    }

    // candidate target zones: least-eviction first, then most-centered
    data class Cand(val evict: Int, val center: Int, val c0: Int, val r0: Int)
    val candList = ArrayList<Cand>(4)
    for (dc in 0 downTo (pw[ai] - TW)) for (dr in 0 downTo (ph[ai] - TH)) {
        val c0 = pc[ai] + dc; val r0 = pr[ai] + dr
        if (pc[ai] >= c0 && pc[ai] + pw[ai] <= c0 + TW &&
            pr[ai] >= r0 && pr[ai] + ph[ai] <= r0 + TH &&
            c0 >= 0 && c0 + TW <= cols && r0 >= 0 && r0 + TH <= rows
        ) {
            var evict = 0
            for (ddc in 0 until TW) for (ddr in 0 until TH) {
                val o = grid[gi(c0 + ddc, r0 + ddr)]
                if (o >= 0 && o != ai) evict++
            }
            val center = abs(2 * c0 + TW - 2 * pc[ai] - pw[ai]) + abs(2 * r0 + TH - 2 * pr[ai] - ph[ai])
            candList.add(Cand(evict, center, c0, r0))
        }
    }
    candList.sortWith(compareBy({ it.evict }, { it.center }))
    val cands = candList.map { intArrayOf(it.c0, it.r0) }
    if (cands.isEmpty()) return tiles.map { SolvedTile(it, it.col, it.row, it.spanW, it.spanH) }

    fun inCanvas(c: Int, r: Int) = c in 0 until cols && r in 0 until rows

    /** Place evicted tile t somewhere nearby: shift, drop into hole, shrink donors, recurse. */
    fun place(t: Int, depth: Int): Boolean {
        if (depth > 6) return false
        val oc = bc[t]; val or0 = br[t]
        val ow = bw[t]; val oh = bh[t]
        // 0) full-size translation near the original spot
        val ringW = ArrayList<IntArray>(48)
        for (d in 1..5) for (dc in -d..d) for (dr in -d..d) {
            if (abs(dc) + abs(dr) != d) continue
            val cc = oc + dc; val rr = or0 + dr
            if (cc < 0 || cc + ow > cols || rr < 0 || rr + oh > rows) continue
            ringW.add(intArrayOf(cc, rr))
        }
        for (cc in ringW) {
            var free = true
            outer@ for (dc in 0 until ow) for (dr in 0 until oh) {
                val k = gi(cc[0] + dc, cc[1] + dr)
                if (grid[k] != -1 && grid[k] != t) { free = false; break@outer }
            }
            if (free) {
                pc[t] = nc(cc[0]); pr[t] = nr(cc[1])
                stamp(t, t); return true
            }
        }
        val ring = ArrayList<IntArray>(48)
        for (d in 1..5) for (dc in -d..d) for (dr in -d..d) {
            if (abs(dc) + abs(dr) != d) continue
            val cc = oc + dc; val rr = or0 + dr
            if (!inCanvas(cc, rr)) continue
            ring.add(intArrayOf(cc, rr))
        }
        // 1) drop straight into an empty cell (shrink to 1x1)
        for (cc in ring) {
            if (grid[gi(cc[0], cc[1])] == -1) {
                pc[t] = nc(cc[0]); pr[t] = nr(cc[1]); pw[t] = 1; ph[t] = 1
                stamp(t, t); return true
            }
        }
        // 2) shrink an occupied multi-cell neighbor (not the active tile) to free a cell
        for (cc in ring) {
            val o = grid[gi(cc[0], cc[1])]
            if (o < 0 || o == t || o == ai || tiles[o].index == activeIndex) continue
            if (pw[o] * ph[o] <= 1) continue
            val keepC = pc[o]; val keepR = pr[o]
            if (nc(cc[0]) == nc(keepC) && nr(cc[1]) == nr(keepR)) continue
            stamp(o, -1)
            pw[o] = 1; ph[o] = 1; pc[o] = keepC; pr[o] = keepR
            stamp(o, o)
            if (grid[gi(cc[0], cc[1])] == -1) {
                pc[t] = nc(cc[0]); pr[t] = nr(cc[1]); pw[t] = 1; ph[t] = 1
                stamp(t, t); return true
            }
        }
        // 3) recursively push the occupant away and take its cell
        for (cc in ring) {
            val o = grid[gi(cc[0], cc[1])]
            if (o < 0 || o == t || o == ai || tiles[o].index == activeIndex) continue
            val sc = pc[o]; val sr = pr[o]; val sw = pw[o]; val sh = ph[o]
            stamp(o, -1)
            if (place(o, depth + 1)) {
                if (grid[gi(cc[0], cc[1])] == -1) {
                    pc[t] = nc(cc[0]); pr[t] = nr(cc[1]); pw[t] = 1; ph[t] = 1
                    stamp(t, t); return true
                }
                stamp(o, -1)
            }
            pc[o] = sc; pr[o] = sr; pw[o] = sw; ph[o] = sh
            stamp(o, o)
        }
        // 4) far donor: any shrinkable non-active card gives up one edge cell
        for (oo in 0 until n) {
            if (oo == t || oo == ai || tiles[oo].index == activeIndex) continue
            if (pw[oo] * ph[oo] <= 1) continue
            val dl = ArrayList<IntArray>(4)
            if (pw[oo] > 1) {
                dl.add(intArrayOf(pc[oo], pr[oo], pw[oo] - 1, ph[oo], pc[oo] + pw[oo] - 1, pr[oo]))
                dl.add(intArrayOf(pc[oo] + 1, pr[oo], pw[oo] - 1, ph[oo], pc[oo], pr[oo]))
            }
            if (ph[oo] > 1) {
                dl.add(intArrayOf(pc[oo], pr[oo], pw[oo], ph[oo] - 1, pc[oo], pr[oo] + ph[oo] - 1))
                dl.add(intArrayOf(pc[oo], pr[oo] + 1, pw[oo], ph[oo] - 1, pc[oo], pr[oo]))
            }
            for (q in dl) {
                val nc2 = q[0]; val nr2 = q[1]; val nw2 = q[2]; val nh2 = q[3]; val fc = q[4]; val fr = q[5]
                if (nc2 < 0 || nc2 + nw2 > cols || nr2 < 0 || nr2 + nh2 > rows) continue
                var ok2 = true
                outer2@ for (x in 0 until nw2) for (y in 0 until nh2) {
                    val k = gi(nc2 + x, nr2 + y)
                    if (grid[k] != -1 && grid[k] != oo) { ok2 = false; break@outer2 }
                }
                if (!ok2) continue
                val s0 = intArrayOf(pc[oo], pr[oo], pw[oo], ph[oo])
                stamp(oo, -1)
                pc[oo] = nc2; pr[oo] = nr2; pw[oo] = nw2; ph[oo] = nh2
                stamp(oo, oo)
                if (grid[gi(fc, fr)] == -1) {
                    pc[t] = nc(fc); pr[t] = nr(fr); pw[t] = 1; ph[t] = 1
                    stamp(t, t); return true
                }
                stamp(oo, -1)
                pc[oo] = s0[0]; pr[oo] = s0[1]; pw[oo] = s0[2]; ph[oo] = s0[3]
                stamp(oo, oo)
            }
        }
        return false
    }

    /** Grow neighbors back into leftover holes; localized window keeps cost O(20x20). */
    fun fillHoles(loC: Int, hiC: Int, loR: Int, hiR: Int) {
        fun tryMoveInto(o: Int, c: Int, r: Int): Boolean {
            if (grid[gi(c, r)] != -1) return false
            stamp(o, -1)
            pc[o] = nc(c); pr[o] = nr(r)
            stamp(o, o); return true
        }
        var moved = true; var guard = 0
        val seen = HashSet<Long>()
        while (moved && guard++ < 64) {
            moved = false
            var sig = 1469598103934665603L
            for (r in loR..hiR) for (c in loC..hiC) {
                sig = (sig xor (gi(c, r) * 31L + grid[gi(c, r)])) * 1099511628211L
            }
            if (!seen.add(sig)) break
            for (r in loR..hiR) for (c in loC..hiC) {
                if (grid[gi(c, r)] != -1) continue
                val ranked = ArrayList<Triple<Int, Int, Int>>(4)
                for (b in arrayOf(intArrayOf(c - 1, r), intArrayOf(c + 1, r),
                                  intArrayOf(c, r - 1), intArrayOf(c, r + 1))) {
                    val o = grid[gi(b[0], b[1])]
                    if (o >= 0 && tiles[o].index != activeIndex) ranked.add(Triple(abs(pc[o] - c) + abs(pr[o] - r), b[0], b[1]))
                }
                ranked.sortBy { it.first }
                var filledByNeighbor = false
                for (rk in ranked) {
                    val o = grid[gi(rk.second, rk.third)]
                    if (o < 0) continue
                    val oc = pc[o]; val or0 = pr[o]; val ow = pw[o]; val oh = ph[o]
                    fun trySet(ncc: Int, nrr: Int, nww: Int, nhh: Int): Boolean {
                        if (ncc < 0 || ncc + nww > cols || nrr < 0 || nrr + nhh > rows) return false
                        for (dc in 0 until nww) for (dr in 0 until nhh) {
                            val k = gi(ncc + dc, nrr + dr)
                            if (grid[k] != -1 && grid[k] != o) return false
                        }
                        stamp(o, -1)
                        pc[o] = nc(ncc); pr[o] = nr(nrr); pw[o] = nww; ph[o] = nhh
                        stamp(o, o); return true
                    }
                    var done = false
                    if (ow == 1 && oh == 1) {
                        done = when {
                            nc(c) == nc(oc + 1) && nr(r) == nr(or0) -> trySet(oc, or0, 2, 1)
                            nc(c) == nc(oc - 1) && nr(r) == nr(or0) -> trySet(oc - 1, or0, 2, 1)
                            nr(r) == nr(or0 + 1) && nc(c) == nc(oc) -> trySet(oc, or0, 1, 2)
                            nr(r) == nr(or0 - 1) && nc(c) == nc(oc) -> trySet(oc, or0 - 1, 1, 2)
                            else -> false
                        }
                        if (!done) done = trySet(c, r, 1, 1)
                    } else if (ow == 2 && oh == 1) {
                        done = when {
                            nr(r) == nr(or0 + 1) -> trySet(oc, or0, 2, 2)
                            nr(r) == nr(or0 - 1) -> trySet(oc, or0 - 1, 2, 2)
                            else -> false
                        }
                    } else if (ow == 1 && oh == 2) {
                        done = when {
                            nc(c) == nc(oc + 1) -> trySet(oc, or0, 2, 2)
                            nc(c) == nc(oc - 1) -> trySet(oc - 1, or0, 2, 2)
                            else -> false
                        }
                    }
                    if (done) { moved = true; filledByNeighbor = true; break }
                }
                if (!filledByNeighbor) {
                    var bestO = -1; var bestD = Int.MAX_VALUE
                    val candLoR = loR - SOLVE_LOCAL_PAD - 2; val candHiR = hiR + SOLVE_LOCAL_PAD + 2
                    val candLoC = loC - SOLVE_LOCAL_PAD - 2; val candHiC = hiC + SOLVE_LOCAL_PAD + 2
                    for (i in 0 until n) {
                        if (i == ai || tiles[i].index == activeIndex) continue
                        if (pw[i] != 1 || ph[i] != 1) continue
                        val br0 = tiles[i].row; val bc0 = tiles[i].col
                        if (br0 < candLoR || br0 > candHiR || bc0 < candLoC || bc0 > candHiC) continue
                        val d = abs(pc[i] - c) + abs(pr[i] - r)
                        if (d < bestD) { bestD = d; bestO = i }
                    }
                    if (bestO >= 0 && tryMoveInto(bestO, c, r)) moved = true
                }
            }
        }
    }

    /** Active card swallows adjacent fully-empty rows/columns (whitespace seams disappear). */
    fun activeSwallowHoles() {
        val AREA_CAP = 12
        var grew = true
        while (grew && pw[ai] * ph[ai] < AREA_CAP) {
            grew = false
            if (pw[ai] * ph[ai] < AREA_CAP && pc[ai] + pw[ai] < cols) {
                var ok = true
                for (dr in 0 until ph[ai]) if (grid[gi(pc[ai] + pw[ai], pr[ai] + dr)] != -1) { ok = false; break }
                if (ok) { pw[ai]++; stamp(ai, ai); grew = true }
            }
            if (pw[ai] * ph[ai] < AREA_CAP && pr[ai] + ph[ai] < rows) {
                var ok = true
                for (dc in 0 until pw[ai]) if (grid[gi(pc[ai] + dc, pr[ai] + ph[ai])] != -1) { ok = false; break }
                if (ok) { ph[ai]++; stamp(ai, ai); grew = true }
            }
            if (pw[ai] * ph[ai] < AREA_CAP && pc[ai] > 0) {
                var ok = true
                for (dr in 0 until ph[ai]) if (grid[gi(pc[ai] - 1, pr[ai] + dr)] != -1) { ok = false; break }
                if (ok) { pc[ai]--; stamp(ai, ai); grew = true }
            }
            if (pw[ai] * ph[ai] < AREA_CAP && pr[ai] > 0) {
                var ok = true
                for (dc in 0 until pw[ai]) if (grid[gi(pc[ai] + dc, pr[ai] - 1)] != -1) { ok = false; break }
                if (ok) { pr[ai]--; stamp(ai, ai); grew = true }
            }
        }
    }

    /** Generic rectangle growth to eat irregular residual holes near the active zone. */
    fun growFill(loC: Int, hiC: Int, loR: Int, hiR: Int) {
        var grew = true; var guard = 0
        while (grew && guard++ < 64) {
            grew = false
            for (i in 0 until n) {
                if (i == ai || tiles[i].index == activeIndex) continue
                if (pw[i] <= 0 || ph[i] <= 0) continue
                if (pc[i] < loC || pc[i] > hiC || pr[i] < loR || pr[i] > hiR) continue
                fun tryGrow(nc2: Int, nr2: Int, nw: Int, nh: Int): Boolean {
                    if (nc2 < 0 || nc2 + nw > cols || nr2 < 0 || nr2 + nh > rows) return false
                    for (dc in 0 until nw) for (dr in 0 until nh) {
                        val k = gi(nc2 + dc, nr2 + dr)
                        if (grid[k] != -1 && grid[k] != i) return false
                    }
                    stamp(i, -1)
                    pc[i] = nc2; pr[i] = nr2; pw[i] = nw; ph[i] = nh
                    stamp(i, i); return true
                }
                var done = false
                if (pc[i] + pw[i] < cols && tryGrow(pc[i], pr[i], pw[i] + 1, ph[i])) done = true
                else if (pr[i] + ph[i] < rows && tryGrow(pc[i], pr[i], pw[i], ph[i] + 1)) done = true
                else if (pc[i] > 0 && tryGrow(pc[i] - 1, pr[i], pw[i] + 1, ph[i])) done = true
                else if (pr[i] > 0 && tryGrow(pc[i], pr[i] - 1, pw[i], ph[i] + 1)) done = true
                if (done) { grew = true; continue }
            }
        }
    }

    fun verifyNoOverlap(): Boolean {
        val chk = IntArray(cols * rows) { -1 }
        for (i in 0 until n) {
            if (pw[i] <= 0 || ph[i] <= 0) continue
            for (dc in 0 until pw[i]) for (dr in 0 until ph[i]) {
                val k = gi(pc[i] + dc, pr[i] + dr)
                if (chk[k] != -1) return false
                chk[k] = i
            }
        }
        return true
    }

    fun shrinkInPlace(o: Int, tzC: Int, tzR: Int): Boolean {
        val oc = pc[o]; val or0 = pr[o]; val ow = pw[o]; val oh = ph[o]
        for (w in ow downTo 1) for (h in oh downTo 1) {
            if (w == ow && h == oh) continue
            val anchors = arrayOf(
                intArrayOf(oc, or0), intArrayOf(oc + ow - w, or0),
                intArrayOf(oc, or0 + oh - h), intArrayOf(oc + ow - w, or0 + oh - h)
            )
            for (a in anchors) {
                val cc = a[0]; val rr = a[1]
                if (cc < 0 || cc + w > cols || rr < 0 || rr + h > rows) continue
                var ok = true
                outerS@ for (dc in 0 until w) for (dr in 0 until h) {
                    val c = cc + dc; val r = rr + dr
                    if (c >= tzC && c < tzC + TW && r >= tzR && r < tzR + TH) { ok = false; break@outerS }
                    val k = gi(c, r)
                    if (grid[k] != -1 && grid[k] != o) { ok = false; break@outerS }
                }
                if (!ok) continue
                stamp(o, -1)
                pc[o] = nc(cc); pr[o] = nr(rr); pw[o] = w; ph[o] = h
                stamp(o, o); return true
            }
        }
        return false
    }

    for (cand in cands) {
        val c0 = cand[0]; val r0 = cand[1]
        val evict = LinkedHashSet<Int>()
        for (dc in 0 until TW) for (dr in 0 until TH) {
            val o = grid[gi(c0 + dc, r0 + dr)]
            if (o < 0 || o == ai) continue
            evict.add(o)
        }
        val doomed = ArrayList<Int>()
        for (o in evict) { if (!shrinkInPlace(o, c0, r0)) doomed.add(o) }
        stamp(ai, -1)
        for (o in doomed) stamp(o, -1)
        pc[ai] = nc(c0); pr[ai] = nr(r0); pw[ai] = TW; ph[ai] = TH
        stamp(ai, ai)
        for (o in doomed) {
            // relocation failure => drop the tile (canvas allows whitespace; the playing
            // card expanding is more important than showing every neighbor)
            if (!place(o, 0)) { pw[o] = 0; ph[o] = 0 }
        }
        val pad = SOLVE_LOCAL_PAD
        val loC = (c0 - pad).coerceAtLeast(0)
        val hiC = (c0 + TW - 1 + pad).coerceAtMost(cols - 1)
        val loR = (r0 - pad).coerceAtLeast(0)
        val hiR = (r0 + TH - 1 + pad).coerceAtMost(rows - 1)
        fillHoles(loC, hiC, loR, hiR)
        activeSwallowHoles()
        growFill(loC, hiC, loR, hiR)
        if (verifyNoOverlap()) {
            return (0 until n).map { SolvedTile(tiles[it], pc[it], pr[it], pw[it], ph[it], it == ai) }
        }
        rollback()
    }

    // Hard fallback for tiny canvases: force-expand by discarding everything intersecting
    // the target zone. Never reached for normal queues (>=5 songs).
    for (cand in cands) {
        val c0 = cand[0]; val r0 = cand[1]
        stamp(ai, -1)
        val zone = HashSet<Int>()
        for (dc in 0 until TW) for (dr in 0 until TH) zone.add(gi(c0 + dc, r0 + dr))
        for (o in 0 until n) {
            if (o == ai) continue
            var inter = false
            for (dc in 0 until pw[o]) for (dr in 0 until ph[o]) {
                if (gi(pc[o] + dc, pr[o] + dr) in zone) inter = true
            }
            if (inter) { stamp(o, -1); pw[o] = 0; ph[o] = 0 }
        }
        pc[ai] = nc(c0); pr[ai] = nr(r0); pw[ai] = TW; ph[ai] = TH
        stamp(ai, ai)
        activeSwallowHoles()
        growFill(
            (c0 - SOLVE_LOCAL_PAD).coerceAtLeast(0), (c0 + TW - 1 + SOLVE_LOCAL_PAD).coerceAtMost(cols - 1),
            (r0 - SOLVE_LOCAL_PAD).coerceAtLeast(0), (r0 + TH - 1 + SOLVE_LOCAL_PAD).coerceAtMost(rows - 1)
        )
        if (verifyNoOverlap()) {
            return (0 until n).map { SolvedTile(tiles[it], pc[it], pr[it], pw[it], ph[it], it == ai) }
        }
        rollback()
    }
    // all candidates failed: keep base geometry, still mark active (no expansion, no overlap)
    return (0 until n).map {
        SolvedTile(tiles[it], tiles[it].col, tiles[it].row, tiles[it].spanW, tiles[it].spanH, it == ai)
    }
}
