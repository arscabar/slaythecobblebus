package dev.cobblespire
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BattleFeedbackTest {
    @Test fun `feedback describes real changes and suppresses generic zero damage hit messages`(){
        val own=setOf("p");val names=mapOf("p" to "거북왕","e" to "꼬렛")
        assertEquals("내 방어 1단계 하락",BattleFeedback.text(SpireBattleEvent("STAGE_CHANGED",1,"e","p",-1,detail="def"),own,names))
        assertEquals("꼬렛에게 물 피해 24",BattleFeedback.text(SpireBattleEvent("MOVE_HIT",1,"p","e",24,moveType="water"),own,names))
        assertNull(BattleFeedback.text(SpireBattleEvent("MOVE_HIT",1,"e","p",0),own,names))
    }
}
