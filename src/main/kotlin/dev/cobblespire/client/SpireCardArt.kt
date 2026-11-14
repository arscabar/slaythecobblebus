package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import com.cobblemon.mod.common.api.moves.Moves
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack

/** One shared card face for hand, flight, preview and deck inspection. */
object SpireCardArt {
    private val frame=ResourceLocation.fromNamespaceAndPath("cobblespire","textures/gui/card-frame-v1.png")
    fun tint(c:JsonObject):Int=if(c.get("trainer")?.asBoolean==true)0xFFB6A5EC.toInt()else mapOf(
        "fire" to 0xFFE59A60.toInt(),"water" to 0xFF77BDE6.toInt(),"grass" to 0xFF9BCB79.toInt(),"electric" to 0xFFF2D666.toInt(),
        "psychic" to 0xFFEB99BE.toInt(),"ghost" to 0xFFB59DD3.toInt(),"poison" to 0xFFD19ADB.toInt(),"ice" to 0xFF91DFDC.toInt(),
        "normal" to 0xFFD7CEAF.toInt(),"ground" to 0xFFD5B47A.toInt(),"rock" to 0xFFC1AC6B.toInt(),"steel" to 0xFFB6C8D4.toInt(),"flying" to 0xFFA7C3EC.toInt(),"bug" to 0xFFC4D77B.toInt(),"dark" to 0xFFB9A89E.toInt(),"fighting" to 0xFFE5A485.toInt(),"dragon" to 0xFFA7A1EE.toInt(),"fairy" to 0xFFF0B5DC.toInt()).getOrDefault(c.text("type"),0xFFB8C5AC.toInt())
    fun description(c:JsonObject)=if(c.get("trainer")?.asBoolean==true)"카드 ${c.number("draw",2)}장을 뽑습니다. 사용 후 소멸합니다."else Moves.getByName(c.text("move"))?.description?.string.orEmpty()
    private fun icon(c:JsonObject)=if(c.get("trainer")?.asBoolean==true)Items.EXPERIENCE_BOTTLE else if(CardSemantics.role(c)==CardRole.DEFENCE)Items.SHIELD else if(CardSemantics.role(c)==CardRole.SUPPORT)Items.BOOK else when(c.text("type")){
        "fire"->Items.BLAZE_POWDER;"water"->Items.HEART_OF_THE_SEA;"grass"->Items.OAK_SAPLING;"electric"->Items.GLOWSTONE_DUST
        "ice"->Items.SNOWBALL;"ghost"->Items.GHAST_TEAR;"poison"->Items.SPIDER_EYE;"psychic"->Items.AMETHYST_SHARD
        "fighting"->Items.IRON_SWORD;"steel"->Items.IRON_INGOT;"ground"->Items.POINTED_DRIPSTONE;"rock"->Items.FLINT
        "dragon"->Items.DRAGON_BREATH;"fairy"->Items.PINK_PETALS;"dark"->Items.ENDER_PEARL;"flying"->Items.FEATHER
        "bug"->Items.HONEYCOMB;else->Items.QUARTZ
    }
    fun draw(g:GuiGraphics,c:JsonObject,x:Int,y:Int,w:Int,h:Int,hover:Boolean=false,affordable:Boolean=true,key:String=""){
        val font=Minecraft.getInstance().font;val role=CardSemantics.role(c)
        g.fill(x+3,y+4,x+w+3,y+h+4,0x85000000.toInt())
        g.fill(x-1,y-1,x+w+1,y+h+1,role.color)
        if(hover){g.fill(x-2,y-2,x+w+2,y+h+2,SpireSkin.teal)}
        g.blit(frame,x,y,0f,0f,w,h,w,h)
        g.fill(x,y,x+w,y+h,(role.color and 0x00FFFFFF) or 0xA0000000.toInt())
        if(w<64||h<100){compact(g,c,x,y,w,h,affordable,key);return}
        // Render live content in a fixed design space, so all sizes share the same frame alignment.
        g.pose().pushPose();g.pose().translate(x.toDouble(),y.toDouble(),0.0);g.pose().scale(w/100f,h/150f,1f)
        fun fitted(s:String,xx:Int,yy:Int,max:Int,col:Int){
            val scale=minOf(.85f,max.toFloat()/font.width(s).coerceAtLeast(1)).coerceAtLeast(.45f)
            g.pose().pushPose();g.pose().translate(xx.toDouble(),yy.toDouble(),1.0);g.pose().scale(scale,scale,1f)
            g.drawString(font,font.plainSubstrByWidth(s,(max/scale).toInt()),0,0,col,false);g.pose().popPose()
        }
        fitted(c.number("cost").toString(),12,16,10,0xEFFFFB)
        fitted(SpireWidgets.move(c.text("move"))+(if(c.get("upgraded")?.asBoolean==true)"+"else ""),29,16,59,0x3A2C1E)
        fun badge(s:String,xx:Int,yy:Int,ww:Int,col:Int){fitted(s,(xx+(ww-font.width(s)*.85f)/2).toInt(),yy,ww-4,col)}
        g.fill(8,29,92,42,role.color);badge(role.label,8,32,84,0x142026)
        g.fill(10,44,90,84,role.dark);g.fill(10,84,90,86,role.color)
        if(role==CardRole.ATTACK){
            // Independent type chip: card colour means role; this badge means attack type.
            g.fill(48,45,89,57,0xFF0B171F.toInt());g.fill(50,44,87,58,0xFF0B171F.toInt())
            g.fill(49,47,88,55,tint(c));g.fill(51,45,86,57,tint(c))
            badge(DamageLabels.type(c.text("type")),49,48,39,0x142026)
        }
        val iconY=if(role==CardRole.ATTACK)59.0 else 52.0;val scale=if(role==CardRole.ATTACK)1.4f else 1.8f
        g.pose().pushPose();g.pose().translate(35.0,iconY,0.0);g.pose().scale(scale,scale,1f);g.renderItem(ItemStack(icon(c)),0,0);g.pose().popPose()
        g.fill(9,89,91,126,role.paper)
        val preview=c.getAsJsonObject("displayPreview")
        g.pose().pushPose();g.pose().translate(10.0,91.0,1.0);g.pose().scale(.98f,.98f,1f)
        g.drawString(font,font.plainSubstrByWidth(DamageLabels.headline(c,preview),82),0,0,0x362A1D,false);g.pose().popPose()
        val metrics=if(role==CardRole.ATTACK)"위력 ${c.number("power")} · "+(preview?.let{DamageLabels.matchup(DamageLabels.multiplier(it))}?:"명중 ${c.number("accuracy")}%")else "대상: ${CardSemantics.target(c)}"
        fitted(metrics,10,103,80,0x51422D)
        val line=font.plainSubstrByWidth(description(c),105)
        fitted(line,10,115,80,0x51422D)
        val flags=listOfNotNull(if(c.get("exhaust")?.asBoolean==true)"소멸"else null,if(c.get("retain")?.asBoolean==true)"보존"else null).joinToString(" · ").ifBlank{role.label+" 카드"}
        fitted(flags,30,133,43,0x4A3420)
        if(key.isNotBlank())fitted(key,83,133,8,0x4A3420)
        g.pose().popPose()
        if(!affordable)g.fill(x,y,x+w,y+h,0x65081824)
    }
    /** Native-sized glyphs for the ordinary HUD; no tiny paragraphs or fractional bitmap scaling. */
    private fun compact(g:GuiGraphics,c:JsonObject,x:Int,y:Int,w:Int,h:Int,enabled:Boolean,key:String){
        val font=Minecraft.getInstance().font;val role=CardSemantics.role(c)
        fun line(s:String,xx:Int,yy:Int,ww:Int,col:Int){val t=if(font.width(s)<=ww)s else font.plainSubstrByWidth(s,(ww-6).coerceAtLeast(0))+"…";g.drawString(font,t,xx,yy,col,false)}
        g.fill(x+2,y+2,x+w-2,y+h-2,role.dark)
        g.fill(x+3,y+3,x+14,y+14,role.color);line(c.number("cost").toString(),x+5,y+4,8,0x16242A)
        line(SpireWidgets.move(c.text("move")),x+17,y+4,w-21,0xF1E7D4)
        g.fill(x+3,y+16,x+w-3,y+26,role.color);line(role.label,x+6,y+17,w-12,0x12232B)
        val preview=c.obj("displayPreview")
        if(role==CardRole.ATTACK){
            g.fill(x+4,y+29,x+w-4,y+40,tint(c));line(DamageLabels.type(c.text("type")),x+7,y+30,w-14,0x142026)
            val result=if(preview!=null)DamageLabels.damage(preview)else "위력 ${c.number("power")}"
            line(result,x+5,y+h-12,w-if(key.isBlank())10 else 22,0xF3E5CF)
        }else{
            val effect=c.text("effectSummary").replace("랭크","").replace("카드 ","뽑기 ").replace(" 뽑기","")
            line(effect,x+5,y+31,w-10,0xF1E5D5)
            if(h>64)line(CardSemantics.target(c),x+5,y+h-12,w-10,0xC9D6D3)
        }
        if(key.isNotBlank())line(key,x+w-10,y+h-12,8,role.color)
        if(!enabled)g.fill(x,y,x+w,y+h,0x55081824)
    }

}
