package dev.cobblespire.client
import com.google.gson.JsonObject
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

abstract class SpirePane(protected var data:JsonObject):Screen(Component.literal("CobbleSpire")){
    protected val pw get()=minOf(580,width-16)
    protected val ph get()=minOf(320,height-16)
    protected val px get()=(width-pw)/2
    protected val py get()=(height-ph)/2
    private var error=""
    protected fun button(x:Int,y:Int,w:Int,h:Int,label:String,enabled:Boolean=true,click:()->Unit)=addRenderableWidget(Button.builder(Component.literal(label)){click()}.bounds(x,y,w,h).build().also{it.active=enabled})
    protected fun text(g:GuiGraphics,s:String,x:Int,y:Int,c:Int=0xE8EBE7){g.drawString(font,s,x,y,c)}
    protected fun short(s:String,w:Int)=if(font.width(s)<=w)s else font.plainSubstrByWidth(s,(w-8).coerceAtLeast(0))+"…"
    fun showError(s:String){error=s}
    open fun accept(value:JsonObject){data=value;error="";rebuildWidgets()}
    override fun renderBlurredBackground(delta:Float){}
    override fun isPauseScreen()=false
    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        g.fill(px,py,px+pw,py+ph,0xF0152229.toInt());g.fill(px,py,px+pw,py+2,0xFFC6AA72.toInt())
        content(g,mx,my,delta);super.render(g,mx,my,delta)
        if(error.isNotBlank())g.renderTooltip(font,font.split(Component.literal(error),pw-24),px+12,py+ph-37)
    }
    protected abstract fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float)
}
