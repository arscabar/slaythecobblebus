package dev.cobblespire

import com.google.gson.JsonObject
import dev.cobblespire.client.CardTooltip
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CardSemanticsTest {
    @Test fun `draw and setup cards are not attacks while recovery and defensive buffs are defence`(){
        assertEquals(CardRole.SUPPORT,CardSemantics.role(TrainerCards.encourage))
        assertEquals(CardRole.SUPPORT,CardSemantics.role(MoveCompatibility.move("swordsdance")))
        assertEquals(CardRole.SUPPORT,CardSemantics.role(MoveCompatibility.move("growl")))
        assertEquals(CardRole.DEFENCE,CardSemantics.role(MoveCompatibility.move("irondefense")))
        assertEquals(CardRole.DEFENCE,CardSemantics.role(MoveCompatibility.move("recover")))
        assertEquals(CardRole.ATTACK,CardSemantics.role(MoveCompatibility.move("gigadrain")))
    }
    @Test fun `support tooltip rejects legacy attack annotations and enemy names`(){
        val c=JsonObject().apply{addProperty("trainer",true);addProperty("power",0);addProperty("type","normal");addProperty("cost",1);addProperty("draw",2);addProperty("exhaust",true);addProperty("role","ATTACK")}
        val p=JsonObject().apply{addProperty("targetName","아보");addProperty("multiplier",1.0);addProperty("accuracy",100)}
        val text=CardTooltip.lines(c,"응원","카드 2장을 뽑습니다.","노말",p).joinToString("\n")
        assertTrue(text.contains("지원"));assertTrue(text.contains("대상: 자신"));assertTrue(text.contains("소멸"))
        for(bad in listOf("아보","노말","위력","명중","피해","보통","×"))assertFalse(text.contains(bad),bad)
    }
    @Test fun `defence descriptions use actual boost effects and attack tooltip keeps its type and damage`(){
        assertEquals("방어 +2랭크",CardSemantics.summary(MoveCompatibility.move("irondefense")))
        assertEquals("카드 3장 뽑기",CardSemantics.summary(TrainerCards.encourage,3))
        val c=JsonObject().apply{addProperty("power",90);addProperty("cost",2);addProperty("role","ATTACK")}
        val p=JsonObject().apply{addProperty("targetName","꼬렛");addProperty("multiplier",2.0);addProperty("accuracy",100);addProperty("minDamage",60);addProperty("maxDamage",70)}
        val text=CardTooltip.lines(c,"화염방사","","불꽃",p).joinToString("\n")
        assertTrue(text.contains("공격"));assertTrue(text.contains("불꽃 타입"));assertTrue(text.contains("60~70 피해"));assertTrue(text.contains("×2"))
    }
}
