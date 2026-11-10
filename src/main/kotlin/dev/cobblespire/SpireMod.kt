package dev.cobblespire
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

class SpireMod:ModInitializer {
    override fun onInitialize(){
        Definitions.validate();SpirePayload.register()
        ServerLifecycleEvents.SERVER_STARTED.register{catalogue=PokemonCatalogue().also{it.reload();it.writeAudit()};logger.info("CobbleSpire registry: {} forms",catalogue!!.entries.size)}
        ServerLifecycleEvents.SERVER_STOPPED.register{catalogue=null}
        ServerPlayNetworking.registerGlobalReceiver(SpirePayload.TYPE){payload,ctx->ctx.server().execute{
            runCatching {val j=JsonParser.parseString(payload.json).asJsonObject;val c=requireNotNull(catalogue)
                require(j.text("action") in setOf("open","catalog")){"Unknown action"}
                send(ctx.player(),c.page(j.text("query").take(64),j.number("page")))
            }.onFailure{ctx.player().sendSystemMessage(Component.literal("[CobbleSpire] ${it.message}"))}
        }}
        CommandRegistrationCallback.EVENT.register{d,_,_->d.register(Commands.literal("cobblespire").executes{send(it.source.playerOrException,requireNotNull(catalogue).page("",0));1}.then(Commands.literal("audit").requires{it.hasPermission(2)}.executes{catalogue?.writeAudit();it.source.sendSuccess({Component.literal("CobbleSpire registry audit written")},false);1}))}
    }
    companion object {
        val logger=LoggerFactory.getLogger("cobblespire");val gson=GsonBuilder().setPrettyPrinting().create()
        var catalogue:PokemonCatalogue?=null
        fun send(player:ServerPlayer,data:com.google.gson.JsonObject){if(ServerPlayNetworking.canSend(player,SpirePayload.TYPE))ServerPlayNetworking.send(player,SpirePayload(data.toString()))}
    }
}
