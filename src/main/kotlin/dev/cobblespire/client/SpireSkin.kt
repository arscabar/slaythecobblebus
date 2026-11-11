package dev.cobblespire.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/** Shared in-game skin; all labels remain live text, never baked into art. */
object SpireSkin {
    val gold=0xFFC5A46B.toInt()
    val teal=0xFF70DDD0.toInt()
    fun panel(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,accent:Int=gold){
        g.fill(x+3,y+4,x+w+3,y+h+4,0x65000000)
        g.fill(x,y,x+w,y+h,0xF00C2027.toInt())
        g.fill(x,y,x+w,y+1,accent);g.fill(x,y+h-1,x+w,y+h,0xFF4B5147.toInt())
        g.fill(x,y,x+1,y+h,0xFF686451.toInt());g.fill(x+w-1,y,x+w,y+h,0xFF686451.toInt())
        for(xx in listOf(x+3,x+w-5))for(yy in listOf(y+3,y+h-5))g.fill(xx,yy,xx+2,yy+2,accent)
    }
    fun gem(g:GuiGraphics,x:Int,y:Int,r:Int,lit:Boolean=true){
        for(d in -r..r){val half=r-kotlin.math.abs(d);g.fill(x-half,y+d,x+half+1,y+d+1,if(lit)teal else 0xFF30434B.toInt())}
        if(lit)g.fill(x-1,y-r+2,x+1,y-r+4,0xFFE0FFF3.toInt())
    }
}

class SpireButton(x:Int,y:Int,w:Int,h:Int,label:Component,onPress:Button.OnPress):Button(x,y,w,h,label,onPress,DEFAULT_NARRATION){
    var selected=false
    var primary=false
    var pile=false
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val hot=isHoveredOrFocused&&active
        val edge=if(selected||hot)SpireSkin.teal else if(active)SpireSkin.gold else 0xFF46545A.toInt()
        if(pile){for(i in 2 downTo 1)SpireSkin.panel(g,x+i*2,y-i*3,width,height,0xFF52696C.toInt())}
        SpireSkin.panel(g,x,y,width,height,edge)
        g.fillGradient(x+2,y+2,x+width-2,y+height-2,
            if(!active)0xFF192B30.toInt()else if(primary||selected)0xFF24564F.toInt()else if(hot)0xFF334D50.toInt()else 0xFF25383C.toInt(),0xFF10232C.toInt())
        if(selected)g.fill(x+2,y+3,x+4,y+height-3,SpireSkin.teal)
        val font=Minecraft.getInstance().font
        val label=font.plainSubstrByWidth(message.string,(width-10).coerceAtLeast(1))
        g.drawCenteredString(font,label,x+width/2,y+(height-8)/2,if(!active)0x71817F else if(hot||selected)0xE5FFF2 else 0xE8D7AD)
    }
}
