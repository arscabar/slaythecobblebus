package dev.cobblespire.client
import dev.cobblespire.*
import com.google.gson.JsonObject
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf

object SpireWidgets {
    private val states=mutableMapOf<String,FloatingState>()
    fun speciesName(species:String,fallback:String="")=species.takeIf{it.isNotBlank()}?.let{PokemonSpecies.getByIdentifier(ResourceLocation.parse(it))?.translatedName?.string?.takeIf{s->s.isNotBlank()}}?:fallback.ifBlank{species}
    fun name(c:JsonObject)=speciesName(c.text("species"),c.text("name"))
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
        val targetRatio=(c.number("hp").toDouble()/c.number("maxHp",1).coerceAtLeast(1)).coerceIn(0.0,1.0).toFloat()
        val key=c.text("uid").ifBlank{c.text("key")}.ifBlank{"${c.text("species")}@$x,$y"}
        val(ratio,trail,healFlash)=SpireAnim.stepHp(key,targetRatio)
        val trailW=(w*trail).toInt()
        val fillW=(w*ratio).toInt()
        if(trailW>fillW){
            g.fill(x+fillW,y,x+trailW,y+5,0xFFF0B35A.toInt())
        }
        if(fillW>0){
            val topBase=if(ratio>.5f)0xFF56B890.toInt()else if(ratio>.2f)0xFFE2AE4B.toInt()else 0xFFC74643.toInt()
            val botBase=if(ratio>.5f)0xFF286B52.toInt()else if(ratio>.2f)0xFF8C5D1E.toInt()else 0xFF701E1D.toInt()
            val top=if(healFlash>0f)SpireAnim.lerpColor(topBase,0xFFB8FFD8.toInt(),healFlash*0.7f) else topBase
            val bot=if(healFlash>0f)SpireAnim.lerpColor(botBase,0xFF5CE09B.toInt(),healFlash*0.5f) else botBase
            g.fillGradient(x,y,x+fillW,y+5,top,bot)
            g.fill(x,y,x+fillW,y+1,0x44FFFFFF)
        }
        for(seg in 1..3){val sx=x+(w*seg)/4;g.fill(sx,y,sx+1,y+5,0x550A0A0C)}
    }
    private val stageLabels=mapOf("atk" to "공","def" to "방","spa" to "특공","spd" to "특방","spe" to "속","accuracy" to "명","evasion" to "회","flashfire" to "타불")
    private val statusNames=mapOf("sleep" to "수면","paralysis" to "마비","poison" to "독","toxic" to "맹독","burn" to "화상","flinch" to "풀죽음","confusion" to "혼란","leechseed" to "씨뿌리기","trapping" to "구속")
    private val statusDescs=mapOf(
        "burn" to "화상: 매 턴 최대 HP 1/16 피해 · 물리 기술 피해 50% 감소",
        "paralysis" to "마비: 속도 50% 감소 · 25% 확률로 행동 불가",
        "poison" to "독: 매 턴 최대 HP 1/8 피해",
        "toxic" to "맹독: 매 턴 피해량 증가",
        "sleep" to "수면: 지속 중 공격 불가",
        "confusion" to "혼란: 1/3 확률로 자신에게 피해",
        "flinch" to "풀죽음: 다음 행동 1회 불가",
        "leechseed" to "씨뿌리기: HP 지속 흡수",
        "trapping" to "구속: 지속 피해 및 제한"
    )
    fun statusEntries(c:JsonObject):List<Pair<String,Int>> =c.array("statuses")?.map{v->val p=v.asString.split(' ');p[0] to (p.getOrNull(1)?.toIntOrNull()?:0)}.orEmpty()
    fun stageEntries(c:JsonObject):List<Pair<String,Int>>{
        val obj=c.getAsJsonObject("stages")?:return emptyList()
        return obj.entrySet().mapNotNull{(k,v)->if(k=="toxic-counter")null else v.asInt.takeIf{it!=0}?.let{k to it}}
    }
    fun hasIcons(c:JsonObject)=statusEntries(c).isNotEmpty()||stageEntries(c).isNotEmpty()
    fun status(c:JsonObject)=statusEntries(c).joinToString(" · "){(id,rem)->(statusNames[id]?:id)+if(rem>0)" $rem"else ""}
    fun stages(c:JsonObject)=stageEntries(c).joinToString(" "){(k,n)->(stageLabels[k]?:k)+(if(n>0)"+$n"else "$n")}
    fun badges(c:JsonObject)=listOf(status(c),stages(c)).filter{it.isNotBlank()}.joinToString(" · ")
    fun detailedConditions(c:JsonObject):List<String> =buildList{
        statusEntries(c).forEach{(id,rem)->val tail=if(rem>0)" (${rem}턴 남음)"else "";add((statusDescs[id]?:(statusNames[id]?:id))+tail)}
        val fullLabels=mapOf("atk" to "공격","def" to "방어","spa" to "특수공격","spd" to "특수방어","spe" to "스피드","accuracy" to "명중률","evasion" to "회피율","flashfire" to "타오르는불꽃")
        stageEntries(c).forEach{(k,n)->add("${fullLabels[k]?:k} ${if(n>0)"+$n"else "$n"}랭크")}
    }
    fun drawSoftGlow(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,rgb:Int,layers:Int,baseAlpha:Float,innerAlpha:Float=0f){
        for(r in 1..layers){
            val a=(baseAlpha/r).coerceIn(0f,0.6f)
            val col=SpireAnim.withAlpha(rgb,a)
            g.fill(x-r,y-r,x+w+r,y-r+1,col)
            g.fill(x-r,y+h+r-1,x+w+r,y+h+r,col)
            g.fill(x-r,y-r+1,x-r+1,y+h+r-1,col)
            g.fill(x+w+r-1,y-r+1,x+w+r,y+h+r-1,col)
        }
        if(innerAlpha>0.005f)g.fill(x+2,y+2,x+w-2,y+h-2,SpireAnim.withAlpha(rgb,innerAlpha))
    }
    private fun drawStatusGlyph(g:GuiGraphics,id:String,x:Int,y:Int){
        when(id){
            "burn"->{
                g.fill(x+3,y+1,x+5,y+2,0xFFFFE066.toInt())
                g.fill(x+2,y+2,x+6,y+5,0xFFFF7A2F.toInt())
                g.fill(x+1,y+4,x+7,y+7,0xFFE03A1E.toInt())
                g.fill(x+3,y+4,x+5,y+6,0xFFFFD84D.toInt())
            }
            "paralysis"->{
                g.fill(x+4,y+1,x+6,y+3,0xFFFFF066.toInt())
                g.fill(x+2,y+3,x+6,y+4,0xFFFFD426.toInt())
                g.fill(x+3,y+4,x+5,y+6,0xFFFFF066.toInt())
                g.fill(x+2,y+6,x+4,y+7,0xFFE6B800.toInt())
            }
            "poison","toxic"->{
                val col=if(id=="toxic")0xFFE65CFF.toInt() else 0xFFB86BFF.toInt()
                g.fill(x+2,y+2,x+5,y+5,col);g.fill(x+3,y+1,x+4,y+2,0xFFF0C4FF.toInt())
                if(id=="toxic"){g.fill(x+5,y+4,x+7,y+7,0xFFFF80FF.toInt())}
            }
            "sleep"->{
                g.fill(x+1,y+2,x+5,y+3,0xFFAAC4FF.toInt());g.fill(x+3,y+3,x+4,y+4,0xFFAAC4FF.toInt());g.fill(x+2,y+4,x+3,y+5,0xFFAAC4FF.toInt());g.fill(x+1,y+5,x+5,y+6,0xFFAAC4FF.toInt())
                g.fill(x+5,y+1,x+7,y+2,0xFFDCE8FF.toInt());g.fill(x+5,y+3,x+7,y+4,0xFFDCE8FF.toInt())
            }
            "confusion"->{
                g.fill(x+2,y+1,x+6,y+2,0xFFFF88D8.toInt());g.fill(x+5,y+2,x+6,y+5,0xFFFF88D8.toInt())
                g.fill(x+2,y+5,x+6,y+6,0xFFE052B0.toInt());g.fill(x+2,y+3,x+3,y+5,0xFFE052B0.toInt())
                g.fill(x+3,y+3,x+5,y+4,0xFFFFD6F2.toInt())
            }
            "flinch"->{
                g.fill(x+3,y+1,x+5,y+4,0xFFFFC95C.toInt());g.fill(x+3,y+5,x+5,y+7,0xFFFFC95C.toInt())
            }
            "leechseed"->{
                g.fill(x+3,y+4,x+5,y+7,0xFF56C271.toInt());g.fill(x+1,y+2,x+4,y+4,0xFF8BE87A.toInt());g.fill(x+4,y+1,x+7,y+3,0xFF8BE87A.toInt())
            }
            else->{
                g.fill(x+2,y+2,x+6,y+6,0xFFD89A6C.toInt());g.fill(x+3,y+3,x+5,y+5,0xFF2B221D.toInt())
            }
        }
    }
    fun drawStatusStatIcons(g:GuiGraphics,c:JsonObject,x:Int,y:Int,maxW:Int,mx:Int=-1,my:Int=-1):Pair<Int,String?>{
        val font=Minecraft.getInstance().font
        var cx=x
        var hoveredTip:String?=null
        val statuses=statusEntries(c)
        val stages=stageEntries(c)
        val items=statuses.size+stages.size
        var drawn=0
        for((id,rem) in statuses){
            val hasNum=rem>0
            val w=if(hasNum)16 else 10
            if(cx+w>x+maxW)break
            val (bg,border)=when(id){
                "burn"->0xFF3A1610.toInt() to 0xFFFF6D3B.toInt()
                "paralysis"->0xFF33290B.toInt() to 0xFFFFD84D.toInt()
                "poison","toxic"->0xFF2B1338.toInt() to 0xFFC86BFF.toInt()
                "sleep"->0xFF161E3A.toInt() to 0xFF8AA8FF.toInt()
                "confusion"->0xFF38162E.toInt() to 0xFFF078C8.toInt()
                "flinch"->0xFF362712.toInt() to 0xFFE6A23C.toInt()
                "leechseed"->0xFF14301A.toInt() to 0xFF7BE068.toInt()
                else->0xFF2B221D.toInt() to 0xFFD18B5A.toInt()
            }
            g.fill(cx,y,cx+w,y+10,border);g.fill(cx+1,y+1,cx+w-1,y+9,bg)
            drawStatusGlyph(g,id,cx+1,y+1)
            if(hasNum){
                g.pose().pushPose();g.pose().translate(cx+10.0,y+2.5,2.0);g.pose().scale(0.68f,0.68f,1f)
                g.drawString(font,"$rem",0,0,0xFFEFE6,false);g.pose().popPose()
            }
            if(mx in cx until (cx+w)&&my in y until (y+10)){
                hoveredTip=(statusDescs[id]?:(statusNames[id]?:id))+(if(rem>0)" (${rem}턴)"else "")
            }
            cx+=w+2;drawn++
        }
        for((k,n) in stages){
            val lbl=(stageLabels[k]?:k.take(1))+(if(n>0)"▲$n"else "▼${-n}")
            val w=(font.width(lbl)*0.68f).toInt()+5
            if(cx+w>x+maxW-(if(drawn<items-1)14 else 0))break
            val up=n>0
            val bg=if(up)0xFF13332B.toInt() else 0xFF39161B.toInt()
            val border=if(up)0xFF4ADE80.toInt() else 0xFFF87171.toInt()
            val ink=if(up)0xFF86EFAC.toInt() else 0xFFFCA5A5.toInt()
            g.fill(cx,y,cx+w,y+10,border);g.fill(cx+1,y+1,cx+w-1,y+9,bg)
            g.pose().pushPose();g.pose().translate(cx+2.5,y+2.5,2.0);g.pose().scale(0.68f,0.68f,1f)
            g.drawString(font,lbl,0,0,ink,false);g.pose().popPose()
            if(mx in cx until (cx+w)&&my in y until (y+10)){
                hoveredTip="$lbl 랭크"
            }
            cx+=w+2;drawn++
        }
        val rest=items-drawn
        if(rest>0&&cx+13<=x+maxW){
            g.fill(cx,y,cx+13,y+10,0xFF6B5D4A.toInt());g.fill(cx+1,y+1,cx+12,y+9,0xFF1C1A1F.toInt())
            g.pose().pushPose();g.pose().translate(cx+2.0,y+2.5,2.0);g.pose().scale(0.66f,0.66f,1f)
            g.drawString(font,"+$rest",0,0,0xFFE7C98F.toInt(),false);g.pose().popPose()
            cx+=15
        }
        return (cx-x) to hoveredTip
    }
}
