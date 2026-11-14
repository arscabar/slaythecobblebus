package dev.cobblespire.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/** Shared Slay the Spire / Darkest Dungeon inspired skin; all labels remain live text. */
object SpireSkin {
    val gold=0xFFD1AE6C.toInt()
    val teal=0xFF6CD9C8.toInt()
    val crimson=0xFFB8433E.toInt()
    val bone=0xFFE9DEC4.toInt()

    fun panel(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,accent:Int=gold){
        if(w<4||h<4)return
        // Soft drop shadow
        g.fill(x+3,y+4,x+w+3,y+h+4,0x78000000)
        // Notched dark obsidian-slate body (clipped 1px corners for forged frame look)
        g.fill(x+1,y,x+w-1,y+h,0xF0141316.toInt())
        g.fill(x,y+1,x+w,y+h-1,0xF0141316.toInt())
        g.fillGradient(x+2,y+2,x+w-2,y+h-2,0xC0211D1B.toInt(),0xD80D0D10.toInt())
        // Outer wrought-iron rim
        g.fill(x+1,y,x+w-1,y+1,0xFF0A0A0C.toInt())
        g.fill(x+1,y+h-1,x+w-1,y+h,0xFF0A0A0C.toInt())
        g.fill(x,y+1,x+1,y+h-1,0xFF0A0A0C.toInt())
        g.fill(x+w-1,y+1,x+w,y+h-1,0xFF0A0A0C.toInt())
        // Inner tarnished brass/accent bevel
        g.fill(x+2,y+1,x+w-2,y+2,accent)
        g.fill(x+2,y+h-2,x+w-2,y+h-1,0xFF4A3D2A.toInt())
        g.fill(x+1,y+2,x+2,y+h-2,0xFF6D5A3D.toInt())
        g.fill(x+w-2,y+2,x+w-1,y+h-2,0xFF3E3323.toInt())
        // Corner rivets
        if(w>=12&&h>=12){
            for(xx in listOf(x+3,x+w-5))for(yy in listOf(y+3,y+h-5)){
                g.fill(xx,yy,xx+2,yy+2,accent)
            }
        }
    }

    fun banner(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,accent:Int=gold){
        g.fill(x,y+2,x+w,y+h+2,0x66000000)
        g.fillGradient(x,y,x+w,y+h,0xF01B1614.toInt(),0xE80E0E11.toInt())
        g.fill(x,y,x+w,y+1,0xFF5A4830.toInt())
        g.fill(x,y+h-2,x+w,y+h-1,accent)
        g.fill(x,y+h-1,x+w,y+h,0xFF0A0A0C.toInt())
    }

    fun orb(g:GuiGraphics,cx:Int,cy:Int,r:Int,lit:Boolean=true,accent:Int=teal){
        // Outer iron & brass ring
        for(d in -(r+2)..(r+2)){
            val half=(r+2)-kotlin.math.abs(d)/2
            val limit=kotlin.math.sqrt(((r+2)*(r+2)-d*d).toDouble()).toInt()
            val span=minOf(half,limit)
            g.fill(cx-span,cy+d,cx+span+1,cy+d+1,0xFF0A0A0C.toInt())
        }
        for(d in -(r+1)..(r+1)){
            val limit=kotlin.math.sqrt(((r+1)*(r+1)-d*d).toDouble()).toInt()
            g.fill(cx-limit,cy+d,cx+limit+1,cy+d+1,if(lit)gold else 0xFF594933.toInt())
        }
        for(d in -r..r){
            val limit=kotlin.math.sqrt((r*r-d*d).toDouble()).toInt()
            val fill=if(!lit)0xFF1F2428.toInt()else if(d<0)accent else 0xFF1E6A63.toInt()
            g.fill(cx-limit,cy+d,cx+limit+1,cy+d+1,fill)
        }
        if(lit&&r>=4){
            g.fill(cx-r/2,cy-r+2,cx+r/3,cy-r+4,0xB0FFFFFF.toInt())
        }
    }

    fun gem(g:GuiGraphics,x:Int,y:Int,r:Int,lit:Boolean=true){
        for(d in -r..r){
            val half=r-kotlin.math.abs(d)
            g.fill(x-half-1,y+d,x+half+2,y+d+1,0xFF0B0B0E.toInt())
            g.fill(x-half,y+d,x+half+1,y+d+1,if(lit)teal else 0xFF282D32.toInt())
        }
        if(lit)g.fill(x-1,y-r+2,x+1,y-r+4,0xFFE5FFF6.toInt())
    }
}

class SpireButton(x:Int,y:Int,w:Int,h:Int,label:Component,onPress:Button.OnPress):Button(x,y,w,h,label,onPress,DEFAULT_NARRATION){
    var selected=false
    var primary=false
    var pile=false
    var hpRatio:Float?=null
    private var hoverAnim=0f
    private var lastFrameMs=SpireAnim.now()
    private var pressAt=0L
    override fun onPress(){
        pressAt=SpireAnim.now()
        super.onPress()
    }
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val now=SpireAnim.now()
        val dt=((now-lastFrameMs).coerceIn(0L,80L))/1000f
        lastFrameMs=now
        val hot=isHoveredOrFocused&&active
        hoverAnim=SpireAnim.approach(hoverAnim,if(hot)1f else 0f,dt,16f)
        val pressing=now-pressAt<150L
        val yOff=if(pressing)1 else 0
        val pulse=if(primary&&active)SpireAnim.pulse(1800L) else 0f

        val baseEdge=when{
            selected->0xFF7BF2DE.toInt()
            !active->0xFF3E3A35.toInt()
            primary->SpireAnim.lerpColor(0xFFC78D44.toInt(),0xFFF6CB70.toInt(),pulse*0.45f)
            else->SpireSkin.gold
        }
        val hotEdge=when{
            selected->0xFFA6FFF0.toInt()
            primary->0xFFFFDF85.toInt()
            else->SpireSkin.teal
        }
        val edge=if(!active)baseEdge else SpireAnim.lerpColor(baseEdge,hotEdge,hoverAnim)
        SpireSkin.panel(g,x,y+yOff,width,height,edge)

        val baseTop=when{
            selected->0xFF286960.toInt()
            !active->0xFF171618.toInt()
            primary->0xFF6B221B.toInt()
            else->0xFF262220.toInt()
        }
        val hotTop=when{
            selected->0xFF317E73.toInt()
            primary->0xFF8E3429.toInt()
            else->0xFF383028.toInt()
        }
        val top=if(!active)baseTop else SpireAnim.lerpColor(baseTop,hotTop,hoverAnim)
        val bottom=when{
            selected->0xFF153833.toInt()
            !active->0xFF101012.toInt()
            primary->0xFF33100D.toInt()
            else->0xFF131214.toInt()
        }
        g.fillGradient(x+2,y+2+yOff,x+width-2,y+height-2+yOff,top,bottom)
        if(hoverAnim>0.02f&&active){
            g.fill(x+3,y+2+yOff,x+width-3,y+3+yOff,SpireAnim.withAlpha(0xFFFFFF,hoverAnim*0.35f))
        }
        if(primary&&active){
            val cycle=((now%2600L)/2600f)
            if(cycle<0.42f)SpireAnim.drawSheen(g,x+2,y+2+yOff,width-4,height-4,cycle/0.42f,0x2CFFF4D0)
        }
        if(pressing){
            val pFade=1f-((now-pressAt)/150f)
            g.fill(x+2,y+2+yOff,x+width-2,y+height-2+yOff,SpireAnim.withAlpha(0xFFE7B8,pFade*0.28f))
        }
        if(selected){
            g.fill(x+2,y+2+yOff,x+5,y+height-2+yOff,0xFFF3C66F.toInt())
        }
        val font=Minecraft.getInstance().font
        val label=font.plainSubstrByWidth(message.string,(width-10).coerceAtLeast(1))
        val ink=when{
            selected->0xFFFFF6D6.toInt()
            !active->0x6B665E
            primary->SpireAnim.lerpColor(0xFFF2D8A7.toInt(),0xFFFFF6DC.toInt(),hoverAnim)
            else->SpireAnim.lerpColor(0xFFE8DEC4.toInt(),0xFFE9FFF5.toInt(),hoverAnim)
        }
        g.drawCenteredString(font,label,x+width/2,y+(height-8)/2+yOff,ink)
        hpRatio?.let{ratio->
            val r=ratio.coerceIn(0f,1f);val bw=(width-6).coerceAtLeast(4);val bx=x+3;val by=y+height-3+yOff
            g.fill(bx,by,bx+bw,by+2,0xFF141316.toInt())
            val fw=(bw*r).toInt()
            if(fw>0){val col=if(r>0.5f)0xFF56B890.toInt()else if(r>0.2f)0xFFE2AE4B.toInt()else 0xFFC74643.toInt();g.fill(bx,by,bx+fw,by+2,col)}
        }
    }
}
