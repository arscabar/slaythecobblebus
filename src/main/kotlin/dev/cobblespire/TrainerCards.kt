package dev.cobblespire

/** New card-battle effects, explicitly separate from original Pokemon moves. */
object TrainerCards {
    const val ENCOURAGE="spire:encourage"
    val encourage=SpireMove(ENCOURAGE,1,selfTarget=true,exhaust=true,drawCards=2,trainer=true)
    fun name(id:String)=if(id==ENCOURAGE)"응원" else id
    fun description(id:String)=if(id==ENCOURAGE)"카드 2장을 뽑습니다. 사용 후 소멸합니다. (강화: 3장)" else ""
}
