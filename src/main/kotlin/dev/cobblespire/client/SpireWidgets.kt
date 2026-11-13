package dev.cobblespire.client
import dev.cobblespire.*
import com.google.gson.JsonObject
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf

object SpireWidgets {
    private val states=mutableMapOf<String,FloatingState>()
    fun name(c:JsonObject)=c.text("name").ifBlank{PokemonSpecies.getByIdentifier(ResourceLocation.parse(c.text("species")))?.translatedName?.string?:c.text("species")}
    fun move(id:String)=if(id==TrainerCards.ENCOURAGE)TrainerCards.name(id)else Moves.getByName(id)?.displayName?.string?:id
    fun portrait(g:GuiGraphics,c:JsonObject,x:Int,y:Int,scale:Float=12f){
        val s=PokemonSpecies.getByIdentifier(ResourceLocation.parse(c.text("species")))?:return
        val aspects=c.array("aspects")?.map{it.asString}?.toSet().orEmpty()
        if(states.size>128)states.clear()
        g.pose().pushPose()
        try{g.pose().translate(x.toDouble(),y.toDouble(),50.0);g.pose().scale(scale,scale,scale)
            drawProfilePokemon(RenderablePokemon(s,aspects),g.pose(),Quaternionf(),state=states.getOrPut(c.text("uid",c.text("key",c.text("species")))){FloatingState()},partialTicks=0f,scale=1f)
        }catch(_:Exception){/* Model failures must not break battle controls. */}finally{g.pose().popPose()}
    }
    fun hp(g:GuiGraphics,c:JsonObject,x:Int,y:Int,w:Int){
        g.fill(x-1,y-1,x+w+1,y+6,0xFF0A0A0C.toInt())
        g.fill(x,y,x+w,y+5,0xFF1D1A19.toInt())
        val ratio=(c.number("hp").toDouble()/c.number("maxHp",1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        val fillW=(w*ratio).toInt()
        if(fillW>0){
            val top=if(ratio>.5)0xFF56B890.toInt()else if(ratio>.2)0xFFE2AE4B.toInt()else 0xFFC74643.toInt()
            val bot=if(ratio>.5)0xFF286B52.toInt()else if(ratio>.2)0xFF8C5D1E.toInt()else 0xFF701E1D.toInt()
            g.fillGradient(x,y,x+fillW,y+5,top,bot)
            g.fill(x,y,x+fillW,y+1,0x44FFFFFF)
        }
        for(seg in 1..3){val sx=x+(w*seg)/4;g.fill(sx,y,sx+1,y+5,0x550A0A0C)}
    }
    fun status(c:JsonObject)=c.array("statuses")?.joinToString(" · "){v->val p=v.asString.split(' ');(mapOf("sleep" to "수면","paralysis" to "마비","poison" to "독","toxic" to "맹독","burn" to "화상","flinch" to "풀죽음","confusion" to "혼란")[p[0]]?:p[0])+if(p.size>1)" ${p[1]}"else ""}.orEmpty()
}
