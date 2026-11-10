package dev.cobblespire.client
import dev.cobblespire.*
import com.google.gson.JsonObject
import net.minecraft.client.gui.GuiGraphics

class PileScreen(private val parent:SpireBattleScreen,data:JsonObject,private val pile:String):SpirePane(data){
    private var scroll=0
    private fun cards()=data.getAsJsonObject("piles").getAsJsonArray(pile).map{it.asJsonObject}.let{if(pile=="draw")it else it.reversed()}
    override fun init(){button(px+12,py+ph-28,pw-24,20,"전투로 돌아가기"){onClose()}}
    override fun onClose(){minecraft?.setScreen(parent)}
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val title=when(pile){"draw"->"뽑기 덱 · 남은 카드 (순서 비공개)";"discard"->"버린 카드 · 최근 사용 순";else->"소멸 덱 · 이번 전투에서 다시 뽑지 않음"}
        text(g,title,px+12,py+12,0xE7C98F);val cards=cards();val cols=4;val cw=(pw-30)/cols;val ch=62;val top=py+36;val bottom=py+ph-36
        val total=((cards.size+cols-1)/cols)*ch;scroll=scroll.coerceIn(0,(total-(bottom-top)).coerceAtLeast(0))
        g.enableScissor(px+10,top,px+pw-10,bottom)
        cards.forEachIndexed{i,c->val x=px+12+(i%cols)*cw;val y=top+(i/cols)*ch-scroll
            if(y+ch>=top&&y<=bottom){g.fill(x,y,x+cw-5,y+ch-5,0xFF294047.toInt());text(g,short(SpireWidgets.move(c.text("move")),cw-15),x+6,y+8);text(g,"비용 ${c.number("cost")}",x+6,y+24,0xE7C98F);text(g,if(c.get("exhaust")?.asBoolean==true)"소멸"else if(c.get("retain")?.asBoolean==true)"보존"else "",x+6,y+40,0x9FC5CB)}
        };g.disableScissor();if(cards.isEmpty())text(g,"카드가 없습니다.",px+12,top+12,0x9FC5CB)
    }
    override fun mouseScrolled(mx:Double,my:Double,h:Double,v:Double):Boolean{scroll-= (v*28).toInt();return true}
}
