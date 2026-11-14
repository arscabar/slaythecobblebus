package dev.cobblespire

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActMapTest {
    @Test
    fun `each act generates 10 to 15 rows ending at boss with full reachability`() {
        val observedHeights = mutableSetOf<Int>()
        for (seedIdx in 0L until 30L) {
            val seed = 10000L + seedIdx * 7919L
            for (act in 1..3) {
                val map = ActMap.generate(act, seed)
                assertTrue(map.rowCount in 10..15, "act=$act seed=$seed rowCount=${map.rowCount} expected 10..15")
                observedHeights.add(map.rowCount)

                val byRow = map.nodes.groupBy { it.row }
                for (r in 0 until map.rowCount) {
                    assertTrue(byRow[r].orEmpty().isNotEmpty(), "Row $r missing nodes")
                }

                val bossNodes = byRow[map.rowCount - 1].orEmpty()
                assertEquals(1, bossNodes.size)
                assertEquals(NodeKind.BOSS, bossNodes.single().kind)
                assertTrue(byRow[map.rowCount - 2].orEmpty().any { it.kind == NodeKind.REST })
                assertTrue(byRow[map.rowCount / 2].orEmpty().any { it.kind == NodeKind.REST })

                // Every node in rows 1..last must be reachable from row 0, and every node must have a path to Boss.
                val reachable = ArrayDeque(byRow[0].orEmpty().map { it.id })
                val seen = reachable.toMutableSet()
                while (reachable.isNotEmpty()) {
                    val cur = map.node(reachable.removeFirst())!!
                    for (nxt in cur.next) {
                        if (seen.add(nxt)) reachable.addLast(nxt)
                    }
                }
                assertEquals(map.nodes.size, seen.size, "Not all nodes reachable from row 0")
                for (node in map.nodes) {
                    if (node.row < map.rowCount - 1) {
                        assertTrue(node.next.isNotEmpty(), "Dead-end node ${node.id} at row ${node.row}")
                    }
                }

                val json = map.json()
                assertEquals(map.rowCount, json.get("rowCount").asInt)
            }
        }
        assertTrue(observedHeights.size >= 4, "Expected varied heights across 10..15, saw $observedHeights")
    }
}
