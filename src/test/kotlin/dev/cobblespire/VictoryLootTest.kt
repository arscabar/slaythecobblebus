package dev.cobblespire
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class VictoryLootTest {
    private fun loot()=VictoryLoot(1,(0..2).map{LootCard(it,MoveInstance(-it.toLong()-1,SpireMove("tackle",1,40)))})
    @Test fun `one choice settles the victory and duplicate requests cannot duplicate cards`(){
        val l=loot();assertFalse(l.settled);assertEquals(1,l.claim(1)!!.owner);assertTrue(l.settled)
        assertThrows(IllegalArgumentException::class.java){l.claim(0)};assertEquals(1,l.selected)
    }
    @Test fun `skip grants no card and invalid candidates do not consume the reward`(){
        val l=loot();assertThrows(IllegalArgumentException::class.java){l.claim(3)};assertFalse(l.settled)
        assertNull(l.claim(-1));assertEquals(-1,l.selected);assertThrows(IllegalArgumentException::class.java){l.claim(2)}
    }
}
