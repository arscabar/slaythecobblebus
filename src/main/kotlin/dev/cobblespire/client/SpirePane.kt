package dev.cobblespire.client
import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

abstract class SpirePane(protected var data:JsonObject):Screen(Component.literal("CobbleSpire")){
    protected open val maximumWidth=580
    protected open val maximumHeight=320
    protected val pw get()=minOf(maximumWidth,width-16)
    protected val ph get()=minOf(maximumHeight,height-16)
    protected val px get()=(width-pw)/2
    protected val py get()=(height-ph)/2
    private var error=""
    protected val openedAt=SpireAnim.now()
    protected open val supportsAdventureMenu=false
    protected var adventureMenuOpen=false
    protected fun addAdventureMenuButton(x:Int,y:Int,w:Int=42,h:Int=14)=button(x,y,w,h,"≡ 메뉴"){adventureMenuOpen=!adventureMenuOpen}
    protected open fun performAdventureQuit(){
        SpireClient.send("quit"){
            if(data.has("session"))addProperty("session",data.text("session"))
            if(data.has("battleNumber"))addProperty("battleNumber",data.number("battleNumber"))
            if(data.has("revision"))addProperty("revision",data.number("revision"))
        }
    }
    private fun menuRect():IntArray{val mw=minOf(224,width-24);val mh=112;return intArrayOf((width-mw)/2,(height-mh)/2,mw,mh)}
    private fun menuItemRect(i:Int):IntArray{val r=menuRect();return intArrayOf(r[0]+14,r[1]+30+i*24,r[2]-28,20)}
    override fun onClose(){
        if(supportsAdventureMenu){adventureMenuOpen=!adventureMenuOpen;return}
        super.onClose()
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean{
        if(adventureMenuOpen){
            if(button==0){
                for(i in 0..2){
                    val b=menuItemRect(i)
                    if(mx>=b[0]&&mx<b[0]+b[2]&&my>=b[1]&&my<b[1]+b[3]){
                        SpireMusic.cardHover()
                        when(i){
                            0->adventureMenuOpen=false
                            1->{adventureMenuOpen=false;minecraft?.setScreen(null)}
                            else->{adventureMenuOpen=false;performAdventureQuit()}
                        }
                        return true
                    }
                }
                val r=menuRect()
                if(mx<r[0]||mx>=r[0]+r[2]||my<r[1]||my>=r[1]+r[3])adventureMenuOpen=false
            }
            return true
        }
        return super.mouseClicked(mx,my,button)
    }
    override fun mouseScrolled(mx:Double,my:Double,scrollX:Double,scrollY:Double)=if(adventureMenuOpen)true else super.mouseScrolled(mx,my,scrollX,scrollY)
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double)=if(adventureMenuOpen)true else super.mouseDragged(mx,my,button,dx,dy)
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean{
        if(adventureMenuOpen){
            if(key==256){adventureMenuOpen=false;return true}
            return true
        }
        return super.keyPressed(key,scan,mods)
    }
    protected fun button(x:Int,y:Int,w:Int,h:Int,label:String,enabled:Boolean=true,click:()->Unit)=addRenderableWidget(SpireButton(x,y,w,h,Component.literal(label),Button.OnPress{click()}).also{it.active=enabled})
    protected fun text(g:GuiGraphics,s:String,x:Int,y:Int,c:Int=0xE8EBE7){g.drawString(font,s,x,y,c)}
    protected fun short(s:String,w:Int)=if(font.width(s)<=w)s else font.plainSubstrByWidth(s,(w-8).coerceAtLeast(0))+"…"
    open fun showError(s:String){error=s}
    open fun accept(value:JsonObject){data=value;error="";rebuildWidgets()}
    override fun renderBlurredBackground(delta:Float){}
    override fun isPauseScreen()=false
    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        background(g)
        content(g,mx,my,delta);super.render(g,mx,my,delta)
        if(adventureMenuOpen)renderAdventureMenu(g,mx,my)
        if(error.isNotBlank())g.renderTooltip(font,font.split(Component.literal(error),pw-24),px+12,py+ph-37)
    }
    private fun renderAdventureMenu(g:GuiGraphics,mx:Int,my:Int){
        g.pose().pushPose();g.pose().translate(0.0,0.0,450.0)
        g.fill(0,0,width,height,0xAA050608.toInt())
        val r=menuRect()
        SpireSkin.panel(g,r[0],r[1],r[2],r[3],SpireSkin.gold)
        g.drawCenteredString(font,"모험 메뉴 · 일시 정지",r[0]+r[2]/2,r[1]+11,0xFFE7C98F.toInt())
        val labels=listOf("계속 진행 (ESC)","화면 잠시 접기 (F10 복귀)","모험 종료 · 원래 위치 복귀")
        labels.forEachIndexed{i,lbl->
            val b=menuItemRect(i)
            val hot=mx>=b[0]&&mx<b[0]+b[2]&&my>=b[1]&&my<b[1]+b[3]
            val danger=(i==2)
            val edge=when{danger&&hot->0xFFFF7A7A.toInt();danger->0xFFB8433E.toInt();hot->SpireSkin.teal;else->SpireSkin.gold}
            SpireSkin.panel(g,b[0],b[1],b[2],b[3],edge)
            val top=when{danger&&hot->0xFF7D2420.toInt();danger->0xFF551A17.toInt();hot->0xFF285852.toInt();else->0xFF242120.toInt()}
            val bot=when{danger->0xFF2B0E0D.toInt();hot->0xFF16332F.toInt();else->0xFF141316.toInt()}
            g.fillGradient(b[0]+2,b[1]+2,b[0]+b[2]-2,b[1]+b[3]-2,top,bot)
            g.drawCenteredString(font,lbl,b[0]+b[2]/2,b[1]+6,if(danger)0xFFFFDFD6.toInt()else 0xFFE9DEC4.toInt())
        }
        g.pose().popPose()
    }
    protected open fun background(g:GuiGraphics){
        SpireSkin.panel(g,px,py,pw,ph)
        val age=SpireAnim.now()-openedAt
        if(age in 0L..260L){
            val t=SpireAnim.easeOutCubic(age/260f)
            SpireAnim.drawSheen(g,px+2,py+2,pw-4,ph-4,t,SpireAnim.withAlpha(0xFFE7B8,(1f-t)*0.22f))
        }
    }
    protected abstract fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float)
}
