package dev.cobblespire.client
import dev.cobblespire.*
import com.google.gson.JsonObject
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies

/** Development registry view; independent Screen, no League UI inheritance. */
class RegistryScreen(private val data:JsonObject):Screen(Component.literal("CobbleSpire")){
    private lateinit var search:EditBox
    private val x get()=(width-440.coerceAtMost(width-16))/2
    private val w get()=440.coerceAtMost(width-16)
    override fun init(){
        search=EditBox(font,x+12,43,w-114,20,Component.literal("종족 검색")).also{it.value=data.text("query");addRenderableWidget(it)}
        addRenderableWidget(Button.builder(Component.literal("검색")){page(0)}.bounds(x+w-96,43,84,20).build())
        addRenderableWidget(Button.builder(Component.literal("이전")){page(data.number("page")-1)}.bounds(x+12,height-34,82,20).build().also{it.active=data.number("page")>0})
        addRenderableWidget(Button.builder(Component.literal("다음")){page(data.number("page")+1)}.bounds(x+100,height-34,82,20).build().also{it.active=(data.number("page")+1)*12<data.number("total")})
        addRenderableWidget(Button.builder(Component.literal("모험 화면")){SpireClient.send("open")}.bounds(x+w-184,height-34,82,20).build())
        addRenderableWidget(Button.builder(Component.literal("닫기")){onClose()}.bounds(x+w-96,height-34,84,20).build())
    }
    private fun page(p:Int){SpireClient.send("catalog"){addProperty("query",search.value);addProperty("page",p)}}
    override fun isPauseScreen()=false
    override fun renderBlurredBackground(delta:Float){}
    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        g.fill(x,12,x+w,height-10,0xF0162029.toInt());g.drawString(font,"CobbleSpire · 설치된 전체 포켓몬",x+12,21,0xF1D49D)
        g.drawString(font,"${data.number("speciesCount")}종 · 검색 ${data.number("total")}폼 · ${data.number("page")+1}페이지",x+12,33,0xA7CBD0)
        val rh=((height-120)/6).coerceAtMost(36)
        data.getAsJsonArray("entries")?.forEachIndexed{i,e->val c=e.asJsonObject;val xx=x+12+(i%2)*(w/2);val yy=74+(i/2)*rh
            val species=PokemonSpecies.getByIdentifier(ResourceLocation.parse(c.text("species")))
            val label=(species?.translatedName?.string?:c.text("name"))+if(c.text("form").equals("Normal",true))"" else " · ${c.text("form")}" 
            g.drawString(font,font.plainSubstrByWidth(label,w/2-20),xx,yy,0xE7E9E4)
            g.drawString(font,font.plainSubstrByWidth(c.text("species"),w/2-20),xx,yy+11,0x87A8B1)
        }
        super.render(g,mx,my,delta)
    }
}
