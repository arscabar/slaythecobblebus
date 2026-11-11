package dev.cobblespire.client
import dev.cobblespire.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class SpireClient:ClientModInitializer {
    override fun onInitializeClient(){
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register{_,_->SeenDeckMotions.clear();WorldBattleView.clear()}
        val key=KeyBindingHelper.registerKeyBinding(KeyMapping("key.cobblespire.open",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F10,"key.categories.cobblespire"))
        ClientTickEvents.END_CLIENT_TICK.register{mc->WorldBattleView.tick(mc);while(key.consumeClick())if(mc.player!=null&&mc.screen==null){
            if(ClientPlayNetworking.canSend(SpirePayload.TYPE))send("open")else mc.player?.displayClientMessage(Component.literal("서버에 CobbleSpire가 설치되어 있지 않습니다."),false)
        }}
        ClientPlayNetworking.registerGlobalReceiver(SpirePayload.TYPE){payload,ctx->ctx.client().execute{
            val d=JsonParser.parseString(payload.json).asJsonObject
            if(ctx.client().player==null)return@execute
            if(d.number("protocol")!=6){ctx.client().player?.displayClientMessage(Component.literal("서버와 클라이언트의 CobbleSpire 버전을 맞춰주세요."),false);return@execute}
            when(d.text("kind")){
                "error"->{(ctx.client().screen as? SpirePane)?.showError(d.text("message"))?:ctx.client().player?.displayClientMessage(Component.literal(d.text("message")),false)}
                "catalog"->ctx.client().setScreen(RegistryScreen(d))
                "menu"->{WorldBattleView.clear();ctx.client().setScreen(SpireMenuScreen(d))}
                "battle"->{
                    WorldBattleView.update(d);val current=ctx.client().screen
                    if(d.text("phase") in setOf("VICTORY","DEFEAT")){
                        if(current is BattleResultScreen)current.accept(d)else ctx.client().setScreen(BattleResultScreen(d))
                    }else if(current is SpireBattleScreen)current.accept(d)
                    else if(current is BattleResultScreen||d.get("open")?.asBoolean==true)ctx.client().setScreen(SpireBattleScreen(d))
                    else SeenDeckMotions.set(d.text("session")+":"+d.number("battleNumber")+":"+d.text("deckOwner"),d.get("motionSequence")?.asLong?:0)
                }
            }
        }}
    }
    companion object {fun send(action:String,body:JsonObject.()->Unit={}){ClientPlayNetworking.send(SpirePayload(JsonObject().apply{addProperty("action",action);addProperty("protocol",6);body()}.toString()))}}
}
