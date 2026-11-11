package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.Minecraft
import net.minecraft.client.CameraType
import net.minecraft.world.entity.decoration.ArmorStand

/** The camera is detached; the real player remains on the arena's side balcony. */
object WorldBattleView {
    private var arena:JsonObject?=null
    private var camera:ArmorStand?=null
    private var previousType:CameraType?=null
    private var previousHideGui:Boolean?=null
    private var yawOffset=0f;private var pitchOffset=0f
    fun rotate(yaw:Float,pitch:Float){yawOffset=(yawOffset+yaw).coerceIn(-35f,35f);pitchOffset=(pitchOffset+pitch).coerceIn(-20f,20f)}
    fun resetAngle(){yawOffset=0f;pitchOffset=0f}
    fun update(data:JsonObject){arena=data.getAsJsonObject("arena")}
    fun clear(){restore(Minecraft.getInstance());arena=null;camera=null}
    private fun restore(mc:Minecraft){
        if(camera!=null&&mc.cameraEntity===camera)mc.player?.let{mc.setCameraEntity(it)}
        previousType?.let{mc.options.cameraType=it};previousType=null
        previousHideGui?.let{mc.options.hideGui=it};previousHideGui=null
    }
    fun tick(mc:Minecraft){
        val d=arena;val level=mc.level
        val tactical=mc.screen is SpireBattleScreen||mc.screen is PileScreen||mc.screen is BattleResultScreen
        if(d==null||level==null||mc.player==null||!tactical||level.dimension().location().toString()!=d.text("dimension")){restore(mc);return}
        val e=camera?.takeIf{it.level()===level}?:ArmorStand(level,0.0,0.0,0.0).also{camera=it;it.isInvisible=true}
        e.setPos(d.get("cameraX").asDouble,d.get("cameraY").asDouble-e.eyeHeight,d.get("cameraZ").asDouble)
        e.yRot=d.get("yaw").asFloat+yawOffset;e.xRot=d.get("pitch").asFloat+pitchOffset+(if(mc.window.guiScaledHeight<316)8f else 0f)
        e.xo=e.x;e.yo=e.y;e.zo=e.z;e.xOld=e.x;e.yOld=e.y;e.zOld=e.z;e.yRotO=e.yRot;e.xRotO=e.xRot
        e.yHeadRot=e.yRot;e.yHeadRotO=e.yRot;e.yBodyRot=e.yRot;e.yBodyRotO=e.yRot
        if(previousType==null)previousType=mc.options.cameraType
        if(previousHideGui==null)previousHideGui=mc.options.hideGui
        mc.options.hideGui=true
        mc.options.cameraType=CameraType.FIRST_PERSON;if(mc.cameraEntity!==e)mc.setCameraEntity(e)
    }
}
