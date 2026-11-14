package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.Minecraft
import net.minecraft.client.CameraType
import net.minecraft.world.entity.decoration.ArmorStand
import kotlin.math.*

/** Camera orbits the arena, never the player's body. Folding HUD does not change this state. */
object WorldBattleView {
    private var arena:JsonObject?=null
    private var scope=""
    private var camera:ArmorStand?=null
    private var previousType:CameraType?=null
    private var previousHideGui:Boolean?=null
    private var yawOffset=0f;private var elevationOffset=0f;private var distanceOffset=0f
    var bodyView=false
    fun orbit(yaw:Float,elevation:Float){bodyView=false;yawOffset=(yawOffset+yaw).coerceIn(-140f,140f);elevationOffset=(elevationOffset+elevation).coerceIn(-12f,18f)}
    fun zoom(amount:Float){bodyView=false;distanceOffset=(distanceOffset+amount).coerceIn(-6f,12f)}
    fun resetAngle(){bodyView=false;yawOffset=0f;elevationOffset=0f;distanceOffset=0f}
    fun update(data:JsonObject){if(scope!=data.text("session")){scope=data.text("session");resetAngle()};arena=data.obj("arena")}
    fun clear(){restore(Minecraft.getInstance());arena=null;camera=null;scope="";resetAngle()}
    @JvmStatic fun locksMovement()=BattleHudState.isActive()&&!bodyView
    fun inArena(mc:Minecraft)=arena?.let{d->mc.level?.dimension()?.location()?.toString()==d.text("dimension")}==true
    private fun restore(mc:Minecraft){
        if(camera!=null&&mc.cameraEntity===camera)mc.player?.let{mc.setCameraEntity(it)}
        previousType?.let{mc.options.cameraType=it};previousType=null
        previousHideGui?.let{mc.options.hideGui=it};previousHideGui=null
    }
    fun tick(mc:Minecraft){
        val d=arena;val level=mc.level
        if(d==null||level==null||mc.player==null||!BattleHudState.isActive()){restore(mc);return}
        if(previousHideGui==null)previousHideGui=mc.options.hideGui
        mc.options.hideGui=false
        if(bodyView){if(camera!=null&&mc.cameraEntity===camera)mc.setCameraEntity(mc.player!!);previousType?.let{mc.options.cameraType=it};previousType=null;return}
        val e=camera?.takeIf{it.level()===level}?:ArmorStand(level,0.0,0.0,0.0).also{camera=it;it.isInvisible=true}
        val yaw=-147.72f+yawOffset;val elevation=(34f+elevationOffset).coerceIn(20f,55f)
        val radius=22.5+distanceOffset;val rad=Math.toRadians(yaw.toDouble())
        val x=d.decimal("focusX")+sin(rad)*radius;val z=d.decimal("focusZ")-cos(rad)*radius
        val y=d.decimal("focusY",64.0)+tan(Math.toRadians(elevation.toDouble()))*radius
        e.setPos(x,y-e.eyeHeight,z);e.yRot=yaw;e.xRot=elevation+10f
        e.xo=e.x;e.yo=e.y;e.zo=e.z;e.xOld=e.x;e.yOld=e.y;e.zOld=e.z;e.yRotO=e.yRot;e.xRotO=e.xRot
        e.yHeadRot=e.yRot;e.yHeadRotO=e.yRot;e.yBodyRot=e.yRot;e.yBodyRotO=e.yRot
        if(previousType==null)previousType=mc.options.cameraType
        mc.options.cameraType=CameraType.FIRST_PERSON;if(mc.cameraEntity!==e)mc.setCameraEntity(e)
    }
}
