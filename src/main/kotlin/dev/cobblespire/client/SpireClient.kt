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
        val key=KeyBindingHelper.registerKeyBinding(KeyMapping("key.cobblespire.open",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F10,"key.categories.cobblespire"))
        ClientTickEvents.END_CLIENT_TICK.register{mc->while(key.consumeClick())if(mc.player!=null&&mc.screen==null){
            if(ClientPlayNetworking.canSend(SpirePayload.TYPE))send("open")else mc.player?.displayClientMessage(Component.literal("서버에 CobbleSpire가 설치되어 있지 않습니다."),false)
        }}
        ClientPlayNetworking.registerGlobalReceiver(SpirePayload.TYPE){payload,ctx->ctx.client().execute{
            val d=JsonParser.parseString(payload.json).asJsonObject
            when(d.text("kind")){
                "error"->{(ctx.client().screen as? SpirePane)?.showError(d.text("message"))?:ctx.client().player?.displayClientMessage(Component.literal(d.text("message")),false)}
                "catalog"->ctx.client().setScreen(RegistryScreen(d))
                "menu"->ctx.client().setScreen(SpireMenuScreen(d))
                "battle"->{val current=ctx.client().screen as? SpireBattleScreen;if(current!=null)current.accept(d)else if(d.get("open")?.asBoolean==true)ctx.client().setScreen(SpireBattleScreen(d))}
            }
        }}
    }
    companion object {fun send(action:String,body:JsonObject.()->Unit={}){ClientPlayNetworking.send(SpirePayload(JsonObject().apply{addProperty("action",action);body()}.toString()))}}
}
