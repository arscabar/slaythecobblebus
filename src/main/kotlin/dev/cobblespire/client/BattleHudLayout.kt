package dev.cobblespire.client

import kotlin.math.min

/** Same geometry for the ordinary HUD and cursor-based card controls. */
class BattleHudLayout(val width:Int,val height:Int,val count:Int,val expanded:Boolean){
    val margin=10
    val side=if(width<500)46 else 60
    val cardH=(height*(if(expanded).30 else .19)).coerceIn(if(expanded)76.0 else 68.0,if(expanded)150.0 else 96.0)
    val cardW=min(cardH*2/3,((width-2*(side+24)-24)/5.0).coerceAtLeast(26.0))
    val handY=height-34-cardH
    private val left=side+22.0
    private val available=width-2*left
    private val spacing=if(count<=1)0.0 else min(cardW+6,(available-cardW)/(count-1))
    fun hand(i:Int):CardBox{val total=cardW+(count.coerceAtLeast(1)-1)*spacing;return CardBox(left+(available-total)/2+i*spacing,handY,cardW,cardH)}
    val draw=CardBox(10.0,handY+cardH-22,side.toDouble(),20.0)
    val discard=CardBox(width-side-10.0,draw.y,side.toDouble(),20.0)
    val exhaust=CardBox(discard.x,discard.y-25,side.toDouble(),20.0)
    val stripY=(handY-25).toInt()
    val toastY=stripY-23
    val partyY=height-25
    fun enemy(i:Int,n:Int):CardBox{val w=min(166.0,(width-24.0-(n-1)*6)/n);val total=n*w+(n-1)*6;return CardBox((width-total)/2+i*(w+6),32.0,w,44.0)}
    fun anchor(place:String,index:Int=0):CardBox=when(place){"DRAW"->draw;"DISCARD"->discard;"EXHAUST"->exhaust;"HAND"->hand(index.coerceIn(0,(count-1).coerceAtLeast(0)));else->CardBox(width/2-cardW/2,handY-cardH*.4,cardW,cardH)}
}
