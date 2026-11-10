package dev.cobblespire.client
import kotlin.math.min
import kotlin.math.max

data class CardBox(val x:Double,val y:Double,val w:Double,val h:Double){val cx get()=x+w/2;val cy get()=y+h/2;fun contains(mx:Double,my:Double)=mx>=x&&mx<x+w&&my>=y&&my<y+h}
/** All coordinates are relative to the battle panel; piles never occupy the hand region. */
class CardBoardLayout(val width:Int,val height:Int,val count:Int){
    val compact=height<300
    val side=min(68.0,max(42.0,width*.095))
    val cardH=min(136.0,(height-(if(compact)118 else 168)-36).toDouble()).coerceAtLeast(36.0)
    val handY=height-cardH-36
    val draw=CardBox(12.0,handY+cardH/2-23,side,46.0)
    val discard=CardBox(width-side-12,handY+cardH-46,side,46.0)
    val exhaust=CardBox(discard.x,discard.y-54,side,46.0)
    private val left=draw.x+draw.w+10
    private val available=discard.x-10-left
    val cardW=min(96.0,((available-16)/5.0)).coerceAtLeast(26.0)
    private val spacing=if(count<=1)0.0 else min(cardW+4,(available-cardW)/(count-1))
    fun hand(index:Int):CardBox {
        val n=count.coerceAtLeast(1);val total=cardW+(n-1)*spacing
        val bend=if(n<2)0.0 else kotlin.math.abs(index-(n-1)/2.0)/((n-1)/2.0)*4
        return CardBox(left+(available-total)/2+index*spacing,handY+bend,cardW,cardH)
    }
    fun anchor(place:String,index:Int=0):CardBox=when(place){"DRAW"->draw;"DISCARD"->discard;"EXHAUST"->exhaust;"HAND"->hand(index.coerceIn(0,(count-1).coerceAtLeast(0)));else->CardBox(width/2.0-cardW/2,handY-cardH*.35,cardW,cardH)}
}
