package dev.cobblespire

import java.util.UUID
import kotlin.random.Random
import kotlin.math.floor

/** Pure server-side rules; no real-world Pokemon/store/inventory objects are modified. */
data class SpireMove(val id:String,val cost:Int,val power:Int=0,val type:String="normal",val accuracy:Int=100,val status:String?=null,val duration:Int=0,val healFraction:Double=0.0,val recoilFraction:Double=0.0,val exhaust:Boolean=false,val selfTarget:Boolean=false,val targetAll:Boolean=false,val drawCards:Int=0,val trainer:Boolean=false)
data class MoveInstance(val uid:Long,val move:SpireMove,var upgraded:Boolean=false,var retain:Boolean=false)
data class TimedAffliction(val id:String,var remaining:Int,val appliedRound:Int)
class SpireCombatant(val uid:String,val species:String,val types:Set<String>,maxHp:Int,var deck:BattleDeck?=null){
    var maxHp=maxHp;private set
    var hp=maxHp
    val alive get()=hp>0
    val stages=mutableMapOf<String,Int>()
    val statuses=linkedMapOf<String,TimedAffliction>()
    var baseForm="base"
    var form=baseForm
    fun heal(amount:Int){if(alive)hp=(hp+amount.coerceAtLeast(0)).coerceAtMost(maxHp)}
    fun revive(fraction:Double){if(!alive)hp=floor(maxHp*fraction).toInt().coerceAtLeast(1)}
    fun growHp(amount:Int){require(amount>=0);val live=alive;maxHp+=amount;if(live)hp+=amount}
    fun afflict(id:String,round:Int,duration:Int=2){statuses[id]=TimedAffliction(id,duration,round)}
    fun leaveActive(){stages.clear();statuses.remove("leechseed");statuses.remove("trapping")}
    fun endBattle(){stages.clear();statuses.clear();form=baseForm}
}
class SpireBattlePlayer(val id:UUID,val party:List<SpireCombatant>){
    init{require(party.size in 1..3)}
    var activeIndex=0
    val active get()=party[activeIndex]
    val alive get()=party.any{it.alive}
    val forcedSwitch get()=!active.alive&&alive
    var energy=3
    var normalSwitchUsed=false
    var ended=false
}
enum class SpireBattlePhase{PLAYER,ENEMY,VICTORY,DEFEAT}
data class EnemyIntent(val actor:Int,val move:SpireMove)
data class SpireBattleEvent(val name:String,val round:Int,val actor:String="",val target:String="",val amount:Int=0,val moveId:String="",val moveType:String="",val multiplier:Double=1.0)
fun interface SpireBattleHook{fun dispatch(event:SpireBattleEvent,battle:SpireBattle)}

interface SpireEffects {
    fun cost(card:MoveInstance)=card.move.cost
    fun drawCount(card:MoveInstance)=card.move.drawCards
    fun healing(source:SpireCombatant,move:SpireMove)=floor(source.maxHp*move.healFraction).toInt()
    fun validate(source:SpireCombatant,card:MoveInstance){}
    fun beforeAction(source:SpireCombatant,move:SpireMove,battle:SpireBattle)=true
    fun canHit(source:SpireCombatant,target:SpireCombatant,move:SpireMove,battle:SpireBattle)=true
    fun accuracy(source:SpireCombatant,target:SpireCombatant,move:SpireMove)=move.accuracy
    fun damage(source:SpireCombatant,target:SpireCombatant,move:SpireMove,upgraded:Boolean,multiplier:Double):Double=move.power*multiplier*(if(upgraded)1.2 else 1.0)
    fun hit(source:SpireCombatant,target:SpireCombatant,move:SpireMove,damage:Int,battle:SpireBattle){}
    fun resolved(source:SpireCombatant,move:SpireMove,totalDamage:Int,battle:SpireBattle){}
    fun switched(out:SpireCombatant,into:SpireCombatant,battle:SpireBattle){}
    fun periodic(mon:SpireCombatant,battle:SpireBattle){if("poison" in mon.statuses||"burn" in mon.statuses)mon.hp=(mon.hp-(mon.maxHp/16).coerceAtLeast(1)).coerceAtLeast(0)}
}

class SpireBattle(val players:List<SpireBattlePlayer>,val enemies:List<SpireCombatant>,seed:Long,
    private val effectiveness:(String,Set<String>)->Double={_,_->1.0},
    private val hook:SpireBattleHook=SpireBattleHook{_,_->},val effects:SpireEffects=object:SpireEffects{}){
    init{require(players.size in 1..4);require(enemies.size in 1..3);require(players.map{it.id}.distinct().size==players.size)}
    private val hitRandom=Random(seed)
    val events=mutableListOf<SpireBattleEvent>()
    private val requests=linkedSetOf<Pair<UUID,String>>()
    var round=1;private set
    var revision=0;private set
    var phase=SpireBattlePhase.PLAYER;private set
    var victoryCount=0;private set
    var intents:List<EnemyIntent> = emptyList()
    val winningSurvivors=mutableSetOf<String>()
    private fun event(name:String,actor:String="",target:String="",amount:Int=0,move:SpireMove?=null,multiplier:Double=1.0){val e=SpireBattleEvent(name,round,actor,target,amount,move?.id.orEmpty(),move?.type.orEmpty(),multiplier);events.add(e);hook.dispatch(e,this)}
    private fun participant(id:UUID)=requireNotNull(players.firstOrNull{it.id==id}){"모험 참가자가 아닙니다."}
    private fun validateRequest(id:UUID,request:String,token:Int){
        require(request.length in 1..80);require(id to request !in requests){"이미 처리한 요청입니다."}
        require(token==revision){"화면이 갱신되었습니다."};require(phase==SpireBattlePhase.PLAYER){"플레이어 차례가 아닙니다."}
    }
    private fun commit(id:UUID,request:String){requests.add(id to request);while(requests.size>512)requests.remove(requests.first());revision++}
    fun start(){event("BATTLE_START");players.filter{it.active.alive}.forEach{it.active.deck?.beginTurn(round)};event("ROUND_START")}
    fun switch(id:UUID,index:Int,request:String,token:Int){
        validateRequest(id,request,token);val p=participant(id)
        require(!p.ended);require(index in p.party.indices&&index!=p.activeIndex&&p.party[index].alive){"교체할 생존 포켓몬을 선택하세요."}
        val forced=p.forcedSwitch
        require(forced||!p.normalSwitchUsed&&p.energy>=1){"일반 교체는 라운드당 1회, 에너지 1이 필요합니다."}
        if(!forced){p.energy--;p.normalSwitchUsed=true}
        val out=p.active;out.deck?.discardHand(round,"SWITCH_OUT");out.leaveActive();p.activeIndex=index;effects.switched(out,p.active,this);p.active.deck?.switchIn(round);commit(id,request);event(if(forced)"FORCED_SWITCH" else "NORMAL_SWITCH",id.toString(),p.active.uid)
    }
    fun useMove(id:UUID,slot:Int,instance:Long,target:Int,request:String,token:Int){
        validateRequest(id,request,token);val p=participant(id);require(!p.ended&&p.active.alive){"생존한 포켓몬으로 교체하세요."}
        val deck=requireNotNull(p.active.deck);val card=requireNotNull(deck.hand.getOrNull(slot));require(card.uid==instance){"손패가 변경되었습니다."}
        require(card.move.trainer||"sleep" !in p.active.statuses){"수면 중에는 지원 카드·교체·차례 종료를 사용할 수 있습니다."}
        val move=card.move;val cost=effects.cost(card);effects.validate(p.active,card)
        require(cost>=0&&p.energy>=cost){"에너지가 부족합니다."}
        require(target in enemies.indices&&enemies[target].alive){"살아 있는 적을 선택하세요."}
        p.energy-=cost;deck.take(slot,round);commit(id,request);event("BEFORE_MOVE_COST",p.active.uid,amount=cost)
        val self=p.active;val foe=enemies[target]
        val failed=(!move.trainer&&"paralysis" in self.statuses&&hitRandom.nextInt(100)<25)||!effects.beforeAction(self,move,this)
        val targets=if(move.selfTarget)listOf(self)else if(move.targetAll)enemies.filter{it.alive}else listOf(foe)
        var inflicted=0
        for(t in targets){
            val hit=!failed&&effects.canHit(self,t,move,this)&&hitRandom.nextInt(100)<effects.accuracy(self,t,move).coerceIn(0,100)
            if(hit){
                val multiplier=effectiveness(move.type,t.types)
                if(move.power>0&&multiplier==0.0){event("MOVE_MISS",self.uid,t.uid,move=move,multiplier=0.0);continue}
                val actual=if(move.power>0){val raw=effects.damage(self,t,move,card.upgraded,multiplier);val n=if(raw<=0)0 else floor(raw).toInt().coerceAtLeast(1);n.coerceAtMost(t.hp)}else 0
                t.hp-=actual;inflicted+=actual;event("MOVE_HIT",self.uid,t.uid,actual,move,multiplier)
                if(move.status!=null&&t.alive)t.afflict(move.status,round,move.duration)
                effects.hit(self,t,move,actual,this)
            }else event("MOVE_MISS",self.uid,t.uid,move=move)
        }
        if(inflicted>0&&move.recoilFraction>0)self.hp=(self.hp-floor(inflicted*move.recoilFraction).toInt().coerceAtLeast(1)).coerceAtLeast(0)
        if(!failed&&move.healFraction>0)self.heal(effects.healing(self,move))
        effects.resolved(self,move,inflicted,this)
        event("MOVE_RESOLVED",self.uid,foe.uid)
        if(!failed&&effects.drawCount(card)>0){val n=deck.drawCards(effects.drawCount(card),round,"EFFECT").size;event("CARDS_DRAWN",self.uid,amount=n)}
        deck.resolve(card,round);event("CARD_PLAYED",self.uid,amount=cost)
        decideResult()
    }
    fun endTurn(id:UUID,request:String,token:Int){
        validateRequest(id,request,token);val p=participant(id);require(!p.ended);require(!p.forcedSwitch){"먼저 생존한 포켓몬으로 교체하세요."}
        p.party.forEach{it.deck?.finishTurn(round)}
        p.ended=true;commit(id,request)
        if(players.filter{it.alive}.all{it.ended})enemyPhase()
    }
    private fun enemyPhase(){
        phase=SpireBattlePhase.ENEMY;event("ENEMY_PHASE_START")
        val actions=intents.filter{it.actor in enemies.indices&&enemies[it.actor].alive}
        for((index,intent) in actions.withIndex()){
            val actor=enemies[intent.actor];val move=intent.move
            if(!actor.alive)continue
            val canAct="sleep" !in actor.statuses&&!("paralysis" in actor.statuses&&hitRandom.nextInt(100)<25)&&effects.beforeAction(actor,move,this)
            if(canAct){
                event("ENEMY_MOVE_STARTED",actor.uid,move.id)
                val targets=if(move.selfTarget)listOf(actor)else players.map{it.active}.filter{it.alive}
                var inflicted=0
                for(target in targets){
                    if(effects.canHit(actor,target,move,this)&&hitRandom.nextInt(100)<effects.accuracy(actor,target,move).coerceIn(0,100)){
                        val mul=effectiveness(move.type,target.types)
                        if(move.power>0&&mul==0.0){event("MOVE_MISS",actor.uid,target.uid,move=move,multiplier=0.0);continue}
                        val raw=if(move.power>0&&mul>0)effects.damage(actor,target,move,false,mul)else 0.0
                        val n=if(raw<=0)0 else floor(raw).toInt().coerceAtLeast(1).coerceAtMost(target.hp)
                        target.hp-=n;inflicted+=n;event("AFTER_DAMAGE_TAKEN",actor.uid,target.uid,n,move,mul)
                        if(move.status!=null&&target.alive)target.afflict(move.status,round,move.duration)
                        effects.hit(actor,target,move,n,this)
                    }else event("MOVE_MISS",actor.uid,target.uid,move=move)
                }
                effects.resolved(actor,move,inflicted,this)
                if(move.healFraction>0)actor.heal(effects.healing(actor,move))
                if(move.recoilFraction>0&&inflicted>0)actor.hp=(actor.hp-floor(inflicted*move.recoilFraction).toInt().coerceAtLeast(1)).coerceAtLeast(0)
            }
            event("ENEMY_ACTION_RESOLVED",actor.uid)
            if(index==actions.lastIndex){event("ENEMY_PHASE_END");if(decideResult())return}
            else if(decideResult())return
        }
        if(actions.isEmpty()){event("ENEMY_PHASE_END");if(decideResult())return}
        // Active-only periodic effects. Bench timers and poison/burn HP do not tick.
        for(mon in players.map{it.active}.filter{it.alive}+enemies.filter{it.alive}){
            effects.periodic(mon,this)
            mon.statuses.values.filter{it.id in setOf("sleep","paralysis","confusion")&&it.appliedRound<round}.forEach{it.remaining--}
            mon.statuses.entries.removeIf{it.value.id in setOf("sleep","paralysis","confusion")&&it.value.remaining<=0}
        }
        event("ROUND_END");if(decideResult())return
        round++;phase=SpireBattlePhase.PLAYER
        players.forEach{it.ended=false;it.normalSwitchUsed=false;it.energy=3;if(it.active.alive)it.active.deck?.beginTurn(round)}
        revision++;event("ROUND_START")
    }
    private fun decideResult():Boolean {
        if(phase in setOf(SpireBattlePhase.VICTORY,SpireBattlePhase.DEFEAT))return true
        if(players.none{it.alive}){phase=SpireBattlePhase.DEFEAT;players.flatMap{it.party}.forEach{it.endBattle()};event("BATTLE_LOST");return true}
        if(enemies.none{it.alive}){
            phase=SpireBattlePhase.VICTORY;victoryCount++
            val all=players.flatMap{it.party};all.filter{it.alive}.forEach{winningSurvivors.add(it.uid)}
            event("PROGRESSION");all.filter{it.uid !in winningSurvivors}.forEach{it.revive(0.2)}
            event("BATTLE_WON");all.forEach{it.endBattle()};return true
        }
        return false
    }
    fun rest(player:UUID){require(phase==SpireBattlePhase.VICTORY);participant(player).party.filter{it.alive}.forEach{it.heal(floor(it.maxHp*0.3).toInt())}}
}

object SpirePartyRules {
    fun disjoint(types:List<Set<String>>):Boolean=types.indices.all{i->types.indices.all{j->i==j||types[i].intersect(types[j]).isEmpty()}}
    fun hpBudget(base:Int,grade:Double,weights:List<Double>,players:Int):List<Int>{
        require(players in 1..4&&weights.size in 1..3&&weights.all{it>0})
        val party=listOf(1.0,1.85,2.70,3.55)[players-1]
        val count=listOf(1.0,1.10,1.15)[weights.size-1]
        val total=kotlin.math.round(base*grade*party*count).toInt()
        var allocated=0
        return weights.mapIndexed{i,w->if(i==weights.lastIndex)total-allocated else floor(total*w/weights.sum()).toInt().also{allocated+=it}}
    }
}
