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
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val hot=isHoveredOrFocused&&active
        val edge=when{
            !active->0xFF3E3A35.toInt()
            primary->if(hot)0xFFF3C66F.toInt()else 0xFFC78D44.toInt()
            selected||hot->SpireSkin.teal
            else->SpireSkin.gold
        }
        if(pile){
            for(i in 2 downTo 1)SpireSkin.panel(g,x+i*2,y-i*3,width,height,0xFF5A4E3B.toInt())
        }
        SpireSkin.panel(g,x,y,width,height,edge)
        val top=when{
            !active->0xFF171618.toInt()
            primary->if(hot)0xFF8C3328.toInt()else 0xFF6B221B.toInt()
            selected->0xFF21524B.toInt()
            hot->0xFF383028.toInt()
            else->0xFF262220.toInt()
        }
        val bottom=when{
            !active->0xFF101012.toInt()
            primary->0xFF33100D.toInt()
            selected->0xFF122C29.toInt()
            else->0xFF131214.toInt()
        }
        g.fillGradient(x+2,y+2,x+width-2,y+height-2,top,bottom)
        if(hot&&active){
            g.fill(x+3,y+2,x+width-3,y+3,0x55FFFFFF)
        }
        if(selected){
            g.fill(x+2,y+3,x+4,y+height-3,SpireSkin.teal)
        }
        val font=Minecraft.getInstance().font
        val label=font.plainSubstrByWidth(message.string,(width-10).coerceAtLeast(1))
        val ink=when{
            !active->0x6B665E
            primary->if(hot)0xFFFDF0D0.toInt()else 0xF2D8A7
            hot||selected->0xE9FFF5
            else->0xE8DEC4
        }
        g.drawCenteredString(font,label,x+width/2,y+(height-8)/2,ink)
    }
}
