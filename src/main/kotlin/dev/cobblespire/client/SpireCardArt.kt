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
    fun description(c:JsonObject)=if(c.get("trainer")?.asBoolean==true)TrainerCards.description(c.text("move"))else Moves.getByName(c.text("move"))?.description?.string.orEmpty()
    private fun icon(c:JsonObject)=if(c.get("trainer")?.asBoolean==true)Items.EXPERIENCE_BOTTLE else when(c.text("type")){
        "fire"->Items.BLAZE_POWDER;"water"->Items.HEART_OF_THE_SEA;"grass"->Items.OAK_SAPLING;"electric"->Items.GLOWSTONE_DUST
        "ice"->Items.SNOWBALL;"ghost"->Items.GHAST_TEAR;"poison"->Items.SPIDER_EYE;"psychic"->Items.AMETHYST_SHARD
        "fighting"->Items.IRON_SWORD;"steel"->Items.IRON_INGOT;"ground"->Items.POINTED_DRIPSTONE;"rock"->Items.FLINT
        "dragon"->Items.DRAGON_BREATH;"fairy"->Items.PINK_PETALS;"dark"->Items.ENDER_PEARL;"flying"->Items.FEATHER
        "bug"->Items.HONEYCOMB;else->Items.QUARTZ
    }
    fun draw(g:GuiGraphics,c:JsonObject,x:Int,y:Int,w:Int,h:Int,hover:Boolean=false,affordable:Boolean=true,key:String=""){
        val font=Minecraft.getInstance().font
        g.fill(x+3,y+4,x+w+3,y+h+4,0x85000000.toInt())
        if(hover){g.fill(x-2,y-2,x+w+2,y+h+2,SpireSkin.teal)}
        g.blit(frame,x,y,0f,0f,w,h,w,h)
        // Render live content in a fixed design space, so all sizes share the same frame alignment.
        g.pose().pushPose();g.pose().translate(x.toDouble(),y.toDouble(),0.0);g.pose().scale(w/100f,h/150f,1f)
        fun fitted(s:String,xx:Int,yy:Int,max:Int,col:Int){
            val scale=minOf(.85f,max.toFloat()/font.width(s).coerceAtLeast(1)).coerceAtLeast(.45f)
            g.pose().pushPose();g.pose().translate(xx.toDouble(),yy.toDouble(),1.0);g.pose().scale(scale,scale,1f)
            g.drawString(font,font.plainSubstrByWidth(s,(max/scale).toInt()),0,0,col,false);g.pose().popPose()
        }
        fitted(c.number("cost").toString(),12,16,10,0xEFFFFB)
        fitted(SpireWidgets.move(c.text("move"))+(if(c.get("upgraded")?.asBoolean==true)"+"else ""),29,16,59,0x3A2C1E)
        val type=if(c.get("trainer")?.asBoolean==true)"지원"else Component.translatable("cobblemon.type.${c.text("type")}").string
        g.fill(20,32,80,33,tint(c));fitted(type,37,35,43,tint(c))
        g.pose().pushPose();g.pose().translate(35.0,47.0,0.0);g.pose().scale(1.8f,1.8f,1f);g.renderItem(ItemStack(icon(c)),0,0);g.pose().popPose()
        val preview=c.getAsJsonObject("displayPreview")
        g.pose().pushPose();g.pose().translate(10.0,91.0,1.0);g.pose().scale(.98f,.98f,1f)
        g.drawString(font,font.plainSubstrByWidth(DamageLabels.headline(c,preview),82),0,0,0x362A1D,false);g.pose().popPose()
        val metrics=if(c.number("power")>0)"위력 ${c.number("power")} · "+(preview?.let{DamageLabels.matchup(DamageLabels.multiplier(it))}?:"명중 ${c.number("accuracy")}%")else "보조 / 지원"
        fitted(metrics,10,103,80,0x51422D)
        val line=font.plainSubstrByWidth(description(c),105)
        fitted(line,10,115,80,0x51422D)
        val flags=listOfNotNull(if(c.get("exhaust")?.asBoolean==true)"소멸"else null,if(c.get("retain")?.asBoolean==true)"보존"else null).joinToString(" · ").ifBlank{"기술 카드"}
        fitted(flags,30,133,43,0x4A3420)
        if(key.isNotBlank())fitted(key,83,133,8,0x4A3420)
        g.pose().popPose()
        if(!affordable)g.fill(x,y,x+w,y+h,0x65081824)
    }
}
