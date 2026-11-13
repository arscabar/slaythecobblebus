package dev.cobblespire

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Dedicated world presentation. These unowned entities never enter a player's Pokemon store. */
class WorldArena(private val server:MinecraftServer){
    data class Scene(val cx:Int,val entities:MutableMap<String,PokemonEntity> = mutableMapOf(),val players:MutableSet<UUID> = mutableSetOf())
    private val scenes=mutableMapOf<UUID,Scene>()
    private val playerSession=mutableMapOf<UUID,Pair<UUID,Int>>()
    private var nextSlot=0
    private val presentationIds=mutableSetOf<UUID>()
    fun presentation(id:UUID)=id in presentationIds
    private fun remove(e:PokemonEntity){presentationIds.remove(e.uuid);e.discard()}
    private val world get()=requireNotNull(server.getLevel(KEY)){"월드 경기장 차원이 없습니다. 서버를 다시 시작해 주세요."}
    private fun record(id:UUID)=server.getWorldPath(LevelResource.ROOT).resolve("data/cobblespire-returns/$id.json")
    private fun seatZ(slot:Int)=-9.0+slot*6.0
    fun enter(p:ServerPlayer,sessionId:UUID=p.uuid,slot:Int=0){
        require(p.uuid !in playerSession)
        require(p.level().dimension()!=KEY){"원래 위치 복구 후 다시 시작하세요."}
        val w=world;val isNew=sessionId !in scenes;val scene=scenes.getOrPut(sessionId){Scene(nextSlot++*128)}
        val returnData=JsonObject().apply{addProperty("dimension",p.level().dimension().location().toString());addProperty("x",p.x);addProperty("y",p.y);addProperty("z",p.z);addProperty("yaw",p.yRot);addProperty("pitch",p.xRot)}
        val path=record(p.uuid);Files.createDirectories(path.parent)
        val tmp=path.resolveSibling(path.fileName.toString()+".tmp")
        Files.writeString(tmp,returnData.toString());Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING)
        scene.players.add(p.uuid);playerSession[p.uuid]=sessionId to slot
        try{
            if(isNew){
                // Build only in our empty dimension, never in the player's existing world.
                for(x in -24..24)for(z in -20..20){
                    val edge=x in setOf(-24,24)||z in setOf(-20,20)
                    val material=if(edge)Blocks.POLISHED_DEEPSLATE else if((x/4+z/4)%2==0)Blocks.SMOOTH_SANDSTONE else Blocks.CUT_SANDSTONE
                    w.setBlock(BlockPos(scene.cx+x,63,z),material.defaultBlockState(),3)
                    if(edge)w.setBlock(BlockPos(scene.cx+x,64,z),Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(),3)
                }
                for(x in listOf(-21,21))for(z in listOf(-17,17)){
                    for(y in 64..69)w.setBlock(BlockPos(scene.cx+x,y,z),Blocks.DEEPSLATE_BRICKS.defaultBlockState(),3)
                    w.setBlock(BlockPos(scene.cx+x,70,z),Blocks.SEA_LANTERN.defaultBlockState(),3)
                }
                for(z in -15..15){w.setBlock(BlockPos(scene.cx-16,64,z),Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),3)}
            }
            p.teleportTo(w,scene.cx-20.5,64.0,seatZ(slot),-105f,12f)
        }catch(e:Exception){leave(p);throw e}
    }
    fun sync(p:ServerPlayer,data:JsonObject):JsonObject{
        val (sessionId,_)=requireNotNull(playerSession[p.uuid]);val s=requireNotNull(scenes[sessionId]);val w=world
        val allies=data.array("arenaAllies")?.map{it.asJsonObject}?:listOf(data.getAsJsonArray("party")[data.number("active")].asJsonObject)
        val slots=mutableListOf<Triple<JsonObject,Double,Double>>()
        allies.forEachIndexed{i,a->slots.add(Triple(a,s.cx+(i-(allies.size-1)/2.0)*5.5,6.0))}
        val enemies=data.getAsJsonArray("enemies")
        enemies.forEachIndexed{i,v->slots.add(Triple(v.asJsonObject,s.cx+(i-(enemies.size()-1)/2.0)*7,-6.0))}
        val alive=slots.filter{it.first.number("hp")>0}.map{it.first.text("uid")}.toSet()
        s.entities.keys.filter{it !in alive}.toList().forEach{s.entities.remove(it)?.let(::remove)}
        for((c,x,z) in slots){
            if(c.number("hp")<=0)continue
            val id=c.text("uid")
            val e=s.entities.getOrPut(id){
                PokemonProperties.parse("${c.text("species")} level=50 uncatchable=true").createEntity(w).also{e->
                    e.pokemon.scaleModifier *= (1.2f/e.bbHeight.coerceAtLeast(.1f)).coerceAtLeast(1f).coerceAtMost(4f)
                    e.isNoAi=true;e.isInvulnerable=true;e.isSilent=true;e.setNoGravity(true);e.setPersistenceRequired()
                    e.addTag(TAG);e.entityData.set(PokemonEntity.UNBATTLEABLE,true)
                    e.moveTo(x,64.0,z,if(z>0)180f else 0f,0f)
                    require(e.pokemon.isUncatchable()){ "전장 포켓몬 포획 차단 설정에 실패했습니다." };presentationIds.add(e.uuid)
                    require(w.addFreshEntity(e)){"전장 포켓몬 생성에 실패했습니다."}
                }
            }
            e.moveTo(x,64.0,z,if(z>0)180f else 0f,0f);e.yHeadRot=e.yRot;e.yBodyRot=e.yRot
            e.customName=Component.literal(c.text("name")+"  ${c.number("hp")}/${c.number("maxHp")} HP")
            e.isCustomNameVisible=true
        }
        val extra=(allies.size-1).coerceAtLeast(0)
        return JsonObject().apply{
            addProperty("dimension",KEY.location().toString());addProperty("cameraX",s.cx-12.0-extra*1.5);addProperty("cameraY",79.0+extra*1.2);addProperty("cameraZ",19.0+extra*1.8)
            addProperty("yaw",-147.72);addProperty("pitch",44.0)
            add("entities",JsonObject().also{o->s.entities.forEach{(k,v)->o.addProperty(k,v.id)}})
        }
    }
    fun tick(){playerSession.forEach{(id,pair)->val s=scenes[pair.first]?:return@forEach;server.playerList.getPlayer(id)?.let{p->
        if(p.level().dimension()!=KEY||p.x !in (s.cx-23.0)..(s.cx-17.0)||p.z !in -17.0..17.0||p.y<63||p.y>70)
            p.teleportTo(world,s.cx-20.5,64.0,seatZ(pair.second),-105f,12f)
    }}}
    fun leave(p:ServerPlayer){
        val (sessionId,_)=playerSession.remove(p.uuid)?:run{recover(p);return}
        scenes[sessionId]?.let{s->s.players.remove(p.uuid);if(s.players.isEmpty())scenes.remove(sessionId)?.entities?.values?.forEach(::remove)}
        recover(p)
    }
    fun recover(p:ServerPlayer){
        val path=record(p.uuid)
        if(!Files.exists(path)){
            if(p.level().dimension()==KEY)p.teleportTo(server.overworld(),server.overworld().sharedSpawnPos.x+.5,server.overworld().sharedSpawnPos.y.toDouble(),server.overworld().sharedSpawnPos.z+.5,0f,0f)
            return
        }
        val d=JsonParser.parseString(Files.readString(path)).asJsonObject
        val key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(d.text("dimension")))
        val target=server.getLevel(key)?:server.overworld()
        p.teleportTo(target,d.get("x").asDouble,d.get("y").asDouble,d.get("z").asDouble,d.get("yaw").asFloat,d.get("pitch").asFloat)
        Files.delete(path)
    }
    fun close(){playerSession.keys.toList().forEach{server.playerList.getPlayer(it)?.let(::leave)}}
    companion object {
        val KEY:ResourceKey<Level> = ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("cobblespire","arena"))
        const val TAG="cobblespire.presentation"
        fun inside(level:Level)=level.dimension()==KEY
    }
}
