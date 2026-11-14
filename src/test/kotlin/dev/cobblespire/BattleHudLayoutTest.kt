package dev.cobblespire
import dev.cobblespire.client.BattleHudLayout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BattleHudLayoutTest {
    @Test fun `ordinary and expanded HUD keep all cards and opponent tiles inside the viewport`(){
        for((w,h)in listOf(304 to 224,428 to 240,640 to 360,960 to 504))for(expanded in listOf(false,true))for(n in listOf(1,5,10)){
            val l=BattleHudLayout(w,h,n,expanded)
            for(i in 0 until n){val b=l.hand(i);assertTrue(b.x>=0&&b.x+b.w<=w);assertTrue(b.y+b.h<l.partyY)}
            for(i in 0..2){val b=l.enemy(i,3);assertTrue(b.y+b.h<l.stripY);assertTrue(b.x>=0&&b.x+b.w<=w)}
        }
    }
}
