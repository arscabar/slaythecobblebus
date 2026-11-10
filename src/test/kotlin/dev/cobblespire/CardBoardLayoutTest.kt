package dev.cobblespire
import dev.cobblespire.client.CardBoardLayout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class CardBoardLayoutTest {
 @Test fun `draw is left discard right and exhaust is above discard across GUI sizes`(){for((w,h)in listOf(304 to 224,464 to 254,760 to 430)){val l=CardBoardLayout(w,h,5);assertTrue(l.draw.cx<l.hand(0).cx);assertTrue(l.discard.cx>l.hand(4).cx);assertTrue(l.exhaust.y+l.exhaust.h<l.discard.y);for(i in 0..4){val c=l.hand(i);assertTrue(c.x>=0&&c.x+c.w<=w);assertTrue(c.y+c.h<h-28)}}}
 @Test fun `additional cards remain within the board instead of requiring pagination`(){val l=CardBoardLayout(464,254,10);for(i in 0..9){val c=l.hand(i);assertTrue(c.x>=0&&c.x+c.w<=464);assertTrue(c.y+c.h<226)}}
}
