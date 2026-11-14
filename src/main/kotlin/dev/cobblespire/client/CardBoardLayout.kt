package dev.cobblespire.client
import kotlin.math.min
import kotlin.math.max

data class CardBox(val x:Double,val y:Double,val w:Double,val h:Double){val cx get()=x+w/2;val cy get()=y+h/2;fun contains(mx:Double,my:Double)=mx>=x&&mx<x+w&&my>=y&&my<y+h}
/** All coordinates are relative to the battle panel; piles never occupy the hand region. */
class CardBoardLayout(val width:Int,val height:Int,val count:Int){
    val compact=height<270
    val drawW=min(44.0,max(34.0,width*.068))
    val discardW=(drawW*0.80).coerceAtLeast(26.0)
    val exhaustW=(discardW*0.76).coerceAtLeast(20.0)
    val side=drawW
    val cardH=min(54.0,(height-(if(compact)110 else 148)-31).toDouble()).coerceAtLeast(30.0)
    val handY=height-cardH-31
    private val drawH=min(56.0,cardH+(if(compact)2.0 else 6.0)).coerceAtLeast(32.0)
    private val discardH=(drawH*0.72).coerceAtLeast(22.0)
    private val exhaustH=(discardH*0.68).coerceAtLeast(16.0)
    val draw=CardBox(8.0,handY+cardH-drawH,drawW,drawH)
    val discard=CardBox(width-discardW-8,handY+cardH-discardH,discardW,discardH)
    val exhaust=CardBox(discard.cx-exhaustW/2.0,discard.y-exhaustH-4,exhaustW,exhaustH)
    private val left=draw.x+draw.w+6
    private val available=discard.x-6-left
    val cardW=min(40.0,((available-12)/5.0)).coerceAtLeast(22.0)
    private val spacing=if(count<=1)0.0 else min(cardW+4,(available-cardW)/(count-1))
    fun hand(index:Int):CardBox {
        val n=count.coerceAtLeast(1);val total=cardW+(n-1)*spacing
        val bend=if(n<2)0.0 else kotlin.math.abs(index-(n-1)/2.0)/((n-1)/2.0)*2.5
        return CardBox(left+(available-total)/2+index*spacing,handY+bend,cardW,cardH)
    }
    fun anchor(place:String,index:Int=0):CardBox=when(place){"DRAW"->draw;"DISCARD"->discard;"EXHAUST"->exhaust;"HAND"->hand(index.coerceIn(0,(count-1).coerceAtLeast(0)));else->CardBox(width/2.0-cardW/2,handY-cardH*.35,cardW,cardH)}
}
