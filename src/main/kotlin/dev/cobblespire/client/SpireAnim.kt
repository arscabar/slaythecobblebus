package dev.cobblespire.client

import net.minecraft.client.gui.GuiGraphics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Lightweight client-side 60fps easing, color blending, ripple, and HP trail helpers. */
object SpireAnim {
    private data class HpTrail(var target:Float,var trail:Float,var fill:Float,var lastMs:Long,var flashUntil:Long)
    private val hpTrails=mutableMapOf<String,HpTrail>()

    fun clear(){
        hpTrails.clear()
    }

    fun now()=System.currentTimeMillis()

    fun easeOutCubic(t:Float):Float{
        val x=t.coerceIn(0f,1f)
        return 1f-(1f-x).pow(3)
    }

    fun easeOutBack(t:Float):Float{
        val x=t.coerceIn(0f,1f)
        val c1=1.70158f;val c3=c1+1f
        return 1f+c3*(x-1f).pow(3)+c1*(x-1f).pow(2)
    }

    fun pulse(periodMs:Long=1600L,offsetMs:Long=0L):Float{
        val t=((now()+offsetMs)%periodMs).toDouble()/periodMs.toDouble()
        return (0.5+0.5*sin(t*2.0*PI)).toFloat()
    }

    fun approach(current:Float,target:Float,dtSec:Float,speed:Float=14f):Float{
        if(kotlin.math.abs(target-current)<0.002f)return target
        val alpha=(1.0-kotlin.math.exp((-speed*dtSec).toDouble())).toFloat().coerceIn(0f,1f)
        return current+(target-current)*alpha
    }

    fun lerp(a:Float,b:Float,t:Float)=a+(b-a)*t.coerceIn(0f,1f)

    fun withAlpha(rgb:Int,alpha01:Float):Int{
        val a=(alpha01.coerceIn(0f,1f)*255f).roundToInt().coerceIn(0,255)
        return (a shl 24) or (rgb and 0xFFFFFF)
    }

    fun lerpColor(c1:Int,c2:Int,t:Float):Int{
        val k=t.coerceIn(0f,1f)
        val a1=(c1 ushr 24) and 0xFF;val r1=(c1 ushr 16) and 0xFF;val g1=(c1 ushr 8) and 0xFF;val b1=c1 and 0xFF
        val a2=(c2 ushr 24) and 0xFF;val r2=(c2 ushr 16) and 0xFF;val g2=(c2 ushr 8) and 0xFF;val b2=c2 and 0xFF
        val a=(a1+(a2-a1)*k).roundToInt().coerceIn(0,255)
        val r=(r1+(r2-r1)*k).roundToInt().coerceIn(0,255)
        val g=(g1+(g2-g1)*k).roundToInt().coerceIn(0,255)
        val b=(b1+(b2-b1)*k).roundToInt().coerceIn(0,255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Crisp 1-2px circular ring for vote ripples and selection pulses. */
    fun drawRing(g:GuiGraphics,cx:Int,cy:Int,r:Int,col:Int){
        if(r<=1||((col ushr 24) and 0xFF)==0)return
        val rOuter=r.toDouble()+0.45
        val rInner=(r-1.35).coerceAtLeast(0.0)
        for(dy in -r..r){
            val ay=dy.toDouble()
            val outX=sqrt((rOuter*rOuter-ay*ay).coerceAtLeast(0.0)).toInt()
            val inX=if(kotlin.math.abs(ay)<rInner)sqrt((rInner*rInner-ay*ay).coerceAtLeast(0.0)).toInt() else -1
            if(inX<0){
                g.fill(cx-outX,cy+dy,cx+outX+1,cy+dy+1,col)
            }else{
                if(outX>=inX){
                    g.fill(cx-outX,cy+dy,cx-inX,cy+dy+1,col)
                    g.fill(cx+inX+1,cy+dy,cx+outX+1,cy+dy+1,col)
                }
            }
        }
    }

    /** Subtle diagonal/horizontal light sweep inside a framed box. */
    fun drawSheen(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,phase01:Float,col:Int=0x38FFF5D0){
        if(w<8||h<4)return
        val bandW=(w/5).coerceIn(10,34)
        val sx=(x-bandW+(w+bandW*2)*phase01.coerceIn(0f,1f)).toInt()
        val left=sx.coerceIn(x,x+w)
        val right=(sx+bandW).coerceIn(x,x+w)
        if(right>left)g.fill(left,y,right,y+h,col)
    }

    /** Returns (fillRatio, trailRatio, healFlash01) smoothed over time for a key. */
    fun stepHp(key:String,targetRatio:Float):Triple<Float,Float,Float>{
        val tNow=now()
        if(hpTrails.size>128)hpTrails.clear()
        val clamped=targetRatio.coerceIn(0f,1f)
        val entry=hpTrails.getOrPut(key){HpTrail(clamped,clamped,clamped,tNow,0L)}
        val dt=((tNow-entry.lastMs).coerceIn(0L,100L))/1000f
        entry.lastMs=tNow
        if(clamped>entry.target+0.005f)entry.flashUntil=tNow+420L
        entry.target=clamped
        entry.fill=if(clamped<entry.fill)approach(entry.fill,clamped,dt,18f) else approach(entry.fill,clamped,dt,8f)
        entry.trail=if(entry.trail<entry.fill)entry.fill else approach(entry.trail,clamped,dt,4.5f)
        val flash=if(tNow<entry.flashUntil)((entry.flashUntil-tNow)/420f).coerceIn(0f,1f) else 0f
        return Triple(entry.fill,entry.trail,flash)
    }

    fun loadingDots(periodMs:Long=420L):String=".".repeat(((now()/periodMs)%3L).toInt()+1)
}
