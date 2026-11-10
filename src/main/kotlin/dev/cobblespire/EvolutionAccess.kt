package dev.cobblespire

/** Credits are issued by trusted server event/badge nodes, never by a client packet. */
class EvolutionAccess {
    data class Credit(val id:Long,val source:String,val targets:Set<String>)
    private val badges=linkedSetOf<String>();private val events=mutableSetOf<String>();private val credits=linkedMapOf<Long,Credit>();private var serial=0L
    fun earnedBadges()=badges.toSet()
    fun available()=credits.values.toList()
    fun badge(id:String):Boolean{require(id.isNotBlank());if(!badges.add(id))return false;issue("badge:$id",emptySet());return true}
    fun event(instance:String,targets:Set<String> = emptySet()):Boolean{require(instance.isNotBlank());if(!events.add(instance))return false;issue("event:$instance",targets.toSet());return true}
    private fun issue(source:String,targets:Set<String>){val id=++serial;credits[id]=Credit(id,source,targets)}
    fun evolve(creditId:Long,target:String,nativeLegalTargets:Set<String>,apply:()->Unit){
        val c=requireNotNull(credits[creditId]){"진화 권한이 없습니다."}
        require(target in nativeLegalTargets&&(c.targets.isEmpty()||target in c.targets)){"현재 포켓몬의 허용된 진화가 아닙니다."}
        apply();credits.remove(creditId)
    }
}
