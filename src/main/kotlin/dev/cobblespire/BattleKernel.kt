package dev.cobblespire

import java.util.UUID
import kotlin.random.Random
import kotlin.math.floor

/** Pure server-side rules; no real-world Pokemon/store/inventory objects are modified. */
data class SpireMove(val id:String,val cost:Int,val power:Int=0,val type:String="normal",val accuracy:Int=100,val status:String?=null,val duration:Int=0,val healFraction:Double=0.0,val recoilFraction:Double=0.0,val exhaust:Boolean=false,val selfTarget:Boolean=false,val targetAll:Boolean=false,val drawCards:Int=0,val trainer:Boolean=false,val minHits:Int=1,val maxHits:Int=1)
data class MoveInstance(val uid:Long,val move:SpireMove,var upgraded:Boolean=false,var retain:Boolean=false)
data class TimedAffliction(val id:String,var remaining:Int,val appliedRound:Int)
class SpireCombatant(val uid:String,var species:String,var types:Set<String>,maxHp:Int,var deck:BattleDeck?=null){
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
    var maxEnergy=3
    var energy=3
    var normalSwitchUsed=false
    var ended=false
}
enum class SpireBattlePhase{PLAYER,ENEMY,VICTORY,DEFEAT}
data class EnemyIntent(val actor:Int,val move:SpireMove)
data class SpireBattleEvent(val name:String,val round:Int,val actor:String="",val target:String="",val amount:Int=0,val moveId:String="",val moveType:String="",val multiplier:Double=1.0)
data class TimelineEntry(val uid:String,val isPlayer:Boolean,val playerId:UUID?,val av:Int,val gaugePct:Int)
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
    private val hook:SpireBattleHook=SpireBattleHook{_,_->},val effects:SpireEffects=object:SpireEffects{},
    private val speedOf:((SpireCombatant)->Int)?=null){
    init{require(players.size in 1..4);require(enemies.size in 1..3);require(players.map{it.id}.distinct().size==players.size)}
    private val hitRandom=Random(seed)
    val events=mutableListOf<SpireBattleEvent>()
    private val requests=linkedSetOf<Pair<UUID,String>>()
    private val departed=mutableSetOf<UUID>()
    private val offlinePlayers=mutableSetOf<UUID>()
    var round=1;private set
    var revision=0;private set
    var phase=SpireBattlePhase.PLAYER;private set
    var victoryCount=0;private set
    var intents:List<EnemyIntent> = emptyList()
    val winningSurvivors=mutableSetOf<String>()
    private val playerAv=mutableMapOf<UUID,Double>()
    private val enemyAv=mutableMapOf<String,Double>()
    private val lastSpeed=mutableMapOf<String,Int>()
    private var anyPlayerTurnStarted=false
    private fun spd(mon:SpireCombatant):Int=(speedOf?.invoke(mon)?:100).coerceAtLeast(20)
    private fun baseAv(mon:SpireCombatant):Double=10000.0/spd(mon)
    private fun syncSpeeds(){
        if(speedOf==null)return
        for(p in activePlayers()){
            val cur=spd(p.active);val prev=lastSpeed[p.active.uid]
            if(prev!=null&&prev!=cur&&(playerAv[p.id]?:0.0)>0.0)playerAv[p.id]=(playerAv[p.id]!!*prev/cur).coerceAtLeast(1.0)
            lastSpeed[p.active.uid]=cur
        }
        for(e in enemies.filter{it.alive}){
            val cur=spd(e);val prev=lastSpeed[e.uid]
            if(prev!=null&&prev!=cur&&(enemyAv[e.uid]?:0.0)>0.0)enemyAv[e.uid]=(enemyAv[e.uid]!!*prev/cur).coerceAtLeast(1.0)
            lastSpeed[e.uid]=cur
        }
    }
    fun actionValue(uid:String):Int{
        if(speedOf==null)return 0
        val p=activePlayers().firstOrNull{it.active.uid==uid}
        if(p!=null)return kotlin.math.round(playerAv[p.id]?:baseAv(p.active)).toInt().coerceAtLeast(0)
        return kotlin.math.round(enemyAv[uid]?:0.0).toInt().coerceAtLeast(0)
    }
    fun gaugePercent(uid:String):Int{
        if(speedOf==null)return 0
        val p=activePlayers().firstOrNull{it.active.uid==uid}
        val (rem,full)=if(p!=null)(playerAv[p.id]?:baseAv(p.active)) to baseAv(p.active)
        else{val e=enemies.firstOrNull{it.uid==uid}?:return 0;(enemyAv[uid]?:baseAv(e)) to baseAv(e)}
        return kotlin.math.round(((1.0-rem/full.coerceAtLeast(1.0))*100.0).coerceIn(0.0,100.0)).toInt()
    }
    fun timelinePreview(limit:Int=6):List<TimelineEntry>{
        if(speedOf==null)return emptyList()
        syncSpeeds()
        val pAv=onlineAlivePlayers().associate{it.id to (playerAv[it.id]?:baseAv(it.active))}.toMutableMap()
        val eAv=enemies.filter{it.alive}.associate{it.uid to (enemyAv[it.uid]?:baseAv(it))}.toMutableMap()
        val out=mutableListOf<TimelineEntry>()
        var elapsed=0.0
        repeat(limit){
            val bestP=pAv.entries.minByOrNull{it.value}
            val bestE=eAv.entries.minByOrNull{it.value}
            if(bestP==null&&bestE==null)return out
            val pVal=bestP?.value?:Double.MAX_VALUE
            val eVal=bestE?.value?:Double.MAX_VALUE
            if(pVal<=eVal+0.01&&bestP!=null){
                val step=pVal.coerceAtLeast(0.0);elapsed+=step
                pAv.keys.forEach{pAv[it]=pAv[it]!!-step};eAv.keys.forEach{eAv[it]=eAv[it]!!-step}
                val pl=players.first{it.id==bestP.key}
                out.add(TimelineEntry(pl.active.uid,true,pl.id,kotlin.math.round(elapsed).toInt(),if(step<=0.01)100 else gaugePercent(pl.active.uid)))
                pAv[bestP.key]=baseAv(pl.active)
            }else if(bestE!=null){
                val step=eVal.coerceAtLeast(0.0);elapsed+=step
                pAv.keys.forEach{pAv[it]=pAv[it]!!-step};eAv.keys.forEach{eAv[it]=eAv[it]!!-step}
                val en=enemies.first{it.uid==bestE.key}
                out.add(TimelineEntry(en.uid,false,null,kotlin.math.round(elapsed).toInt(),gaugePercent(en.uid)))
                eAv[bestE.key]=baseAv(en)
            }
        }
        return out
    }
    private fun activePlayers()=players.filter{it.id !in departed}
    private fun onlineAlivePlayers()=activePlayers().filter{it.alive&&it.id !in offlinePlayers}
    private fun event(name:String,actor:String="",target:String="",amount:Int=0,move:SpireMove?=null,multiplier:Double=1.0){val e=SpireBattleEvent(name,round,actor,target,amount,move?.id.orEmpty(),move?.type.orEmpty(),multiplier);events.add(e);hook.dispatch(e,this)}
    private fun participant(id:UUID)=requireNotNull(players.firstOrNull{it.id==id&&it.id !in departed&&it.id !in offlinePlayers}){"모험 참가자가 아닙니다."}
    fun setPlayerOnline(id:UUID,online:Boolean){
        val p=players.firstOrNull{it.id==id&&it.id !in departed}?:return
        if(!online){
            if(!offlinePlayers.add(id))return
            p.ended=true;revision++
            val remaining=onlineAlivePlayers()
            if(phase==SpireBattlePhase.PLAYER&&remaining.isNotEmpty()&&remaining.all{it.ended}){
                if(speedOf!=null)advanceGauge(opening=false)else enemyPhase()
            }
        }else{
            if(!offlinePlayers.remove(id))return
            if(speedOf!=null&&id !in playerAv){playerAv[id]=baseAv(p.active);lastSpeed[p.active.uid]=spd(p.active)}
            if(phase==SpireBattlePhase.PLAYER&&p.forcedSwitch)p.ended=false
            revision++
        }
    }
    fun removePlayer(id:UUID){
        val p=players.firstOrNull{it.id==id}?:return
        offlinePlayers.remove(id)
        if(!departed.add(id))return
        p.party.forEach{it.hp=0};p.ended=true;revision++
        if(decideResult())return
        val remaining=onlineAlivePlayers()
        if(phase==SpireBattlePhase.PLAYER&&remaining.isNotEmpty()&&remaining.all{it.ended}){if(speedOf!=null)advanceGauge(opening=false)else enemyPhase()}
    }
    private fun validateRequest(id:UUID,request:String,token:Int){
        require(request.length in 1..80);require(id to request !in requests){"이미 처리한 요청입니다."}
        require(token==revision){"화면이 갱신되었습니다."};require(phase==SpireBattlePhase.PLAYER){"플레이어 차례가 아닙니다."}
    }
    private fun commit(id:UUID,request:String){requests.add(id to request);while(requests.size>512)requests.remove(requests.first());revision++}
    fun start(){
        event("BATTLE_START")
        if(speedOf==null){
            onlineAlivePlayers().forEach{it.active.deck?.beginTurn(round)}
            event("ROUND_START")
        }else{
            onlineAlivePlayers().forEach{p->playerAv[p.id]=baseAv(p.active);lastSpeed[p.active.uid]=spd(p.active)}
            enemies.filter{it.alive}.forEach{e->enemyAv[e.uid]=baseAv(e);lastSpeed[e.uid]=spd(e)}
            event("REFRESH_INTENTS")
            advanceGauge(opening=true)
        }
    }
    private fun hitCount(move:SpireMove):Int=when{
        move.maxHits<=1->1
        move.minHits>=move.maxHits->move.minHits
        move.minHits==2&&move.maxHits==5->when(hitRandom.nextInt(20)){in 0..6->2;in 7..13->3;in 14..16->4;else->5}
        else->hitRandom.nextInt(move.minHits,move.maxHits+1)
    }
    private fun applyTargetHits(actor:SpireCombatant,target:SpireCombatant,move:SpireMove,upgraded:Boolean,multiplier:Double):Int{
        var total=0
        val count=hitCount(move)
        for(hitIndex in 0 until count){
            if(hitIndex>0&&!target.alive)break
            val actual=if(move.power>0&&multiplier>0.0){
                val raw=effects.damage(actor,target,move,upgraded,multiplier)
                val n=if(raw<=0)0 else floor(raw).toInt().coerceAtLeast(1)
                n.coerceAtMost(target.hp)
            }else 0
            target.hp-=actual;total+=actual
            if(move.status!=null&&target.alive)target.afflict(move.status,round,move.duration)
            effects.hit(actor,target,move,actual,this)
        }
        return total
    }
    fun switch(id:UUID,index:Int,request:String,token:Int){
        validateRequest(id,request,token);val p=participant(id)
        require(!p.ended);require(index in p.party.indices&&index!=p.activeIndex&&p.party[index].alive){"교체할 생존 포켓몬을 선택하세요."}
        val forced=p.forcedSwitch
        require(forced||!p.normalSwitchUsed&&p.energy>=1){"일반 교체는 라운드당 1회, 에너지 1이 필요합니다."}
        if(!forced){p.energy--;p.normalSwitchUsed=true}
        val out=p.active;out.deck?.discardHand(round,"SWITCH_OUT");out.leaveActive();p.activeIndex=index;effects.switched(out,p.active,this);p.active.deck?.switchIn(round)
        if(speedOf!=null){lastSpeed[p.active.uid]=spd(p.active)}
        commit(id,request);event(if(forced)"FORCED_SWITCH" else "NORMAL_SWITCH",id.toString(),p.active.uid)
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
        val targets=if(move.selfTarget)listOf(self)else if(move.targetAll)enemies.filter{it.alive}else listOf(foe)
        val track=if(move.trainer) emptyList() else (targets+self).distinctBy{it.uid}
        val beforeStatuses=track.associate{it.uid to it.statuses.keys.toSet()}
        var inflicted=0;var failed=false
        if(!move.trainer&&"paralysis" in self.statuses&&hitRandom.nextInt(100)<25){
            failed=true;event("MOVE_PARALYZED",self.uid,foe.uid,move=move)
        }else{
            val hpBefore=self.hp
            if(!effects.beforeAction(self,move,this)){
                failed=true
                if(self.hp<hpBefore){
                    event("STATUS_DAMAGE",self.uid,self.uid,hpBefore-self.hp,SpireMove("confusion",0))
                    event("MOVE_CONFUSED",self.uid,self.uid,move=move)
                }else event("MOVE_FLINCHED",self.uid,self.uid,move=move)
            }
        }
        if(!failed){
            for(t in targets){
                val hit=effects.canHit(self,t,move,this)&&hitRandom.nextInt(100)<effects.accuracy(self,t,move).coerceIn(0,100)
                if(hit){
                    val multiplier=effectiveness(move.type,t.types)
                    if(move.power>0&&multiplier==0.0){event("MOVE_MISS",self.uid,t.uid,move=move,multiplier=0.0);continue}
                    val actual=applyTargetHits(self,t,move,card.upgraded,multiplier)
                    inflicted+=actual;event("MOVE_HIT",self.uid,t.uid,actual,move,multiplier)
                }else event("MOVE_MISS",self.uid,t.uid,move=move)
            }
        }
        if(inflicted>0&&move.recoilFraction>0)self.hp=(self.hp-floor(inflicted*move.recoilFraction).toInt().coerceAtLeast(1)).coerceAtLeast(0)
        if(!failed&&move.healFraction>0)self.heal(effects.healing(self,move))
        effects.resolved(self,move,inflicted,this)
        for(mon in track){val prev=beforeStatuses[mon.uid].orEmpty();mon.statuses.keys.filter{it !in prev}.forEach{st->event("STATUS_APPLIED",self.uid,mon.uid,move=SpireMove(st,0))}}
        event("MOVE_RESOLVED",self.uid,foe.uid)
        if(!failed&&effects.drawCount(card)>0){val n=deck.drawCards(effects.drawCount(card),round,"EFFECT").size;event("CARDS_DRAWN",self.uid,amount=n)}
        deck.resolve(card,round);syncSpeeds();event("CARD_PLAYED",self.uid,amount=cost)
        decideResult()
    }
    fun endTurn(id:UUID,request:String,token:Int){
        validateRequest(id,request,token);val p=participant(id);require(!p.ended);require(!p.forcedSwitch){"먼저 생존한 포켓몬으로 교체하세요."}
        p.party.forEach{it.deck?.finishTurn(round)}
        p.ended=true;commit(id,request)
        if(speedOf!=null){
            playerAv[p.id]=baseAv(p.active);lastSpeed[p.active.uid]=spd(p.active)
            if(p.active.alive)tickStatuses(p.active)
            if(decideResult())return
            if(onlineAlivePlayers().all{it.ended})advanceGauge(opening=false)
        }else if(onlineAlivePlayers().all{it.ended})enemyPhase()
    }
    private fun tickStatuses(mon:SpireCombatant){
        val beforeHp=mon.hp
        effects.periodic(mon,this)
        val dmg=(beforeHp-mon.hp).coerceAtLeast(0)
        if(dmg>0){
            val cause=when{"toxic" in mon.statuses->"toxic";"poison" in mon.statuses->"poison";"burn" in mon.statuses->"burn";else->"status"}
            event("STATUS_DAMAGE",mon.uid,mon.uid,dmg,SpireMove(cause,0))
        }
        mon.statuses.values.filter{it.id in setOf("sleep","confusion")&&it.appliedRound<round}.forEach{it.remaining--}
        mon.statuses.entries.removeIf{it.value.id in setOf("sleep","confusion")&&it.value.remaining<=0}
    }
    private fun executeEnemyMove(intent:EnemyIntent){
        val actor=enemies[intent.actor];val move=intent.move
        if(!actor.alive)return
        if("sleep" in actor.statuses){event("MOVE_SLEEP",actor.uid,actor.uid,move=move);return}
        if("paralysis" in actor.statuses&&hitRandom.nextInt(100)<25){event("MOVE_PARALYZED",actor.uid,actor.uid,move=move);return}
        val hpBefore=actor.hp
        if(!effects.beforeAction(actor,move,this)){
            if(actor.hp<hpBefore){
                event("STATUS_DAMAGE",actor.uid,actor.uid,hpBefore-actor.hp,SpireMove("confusion",0))
                event("MOVE_CONFUSED",actor.uid,actor.uid,move=move)
            }else event("MOVE_FLINCHED",actor.uid,actor.uid,move=move)
            return
        }
        event("ENEMY_MOVE_STARTED",actor.uid,move.id)
        val targets=if(move.selfTarget)listOf(actor)else activePlayers().map{it.active}.filter{it.alive}
        val track=(targets+actor).distinctBy{it.uid}
        val beforeStatuses=track.associate{it.uid to it.statuses.keys.toSet()}
        var inflicted=0
        for(target in targets){
            if(effects.canHit(actor,target,move,this)&&hitRandom.nextInt(100)<effects.accuracy(actor,target,move).coerceIn(0,100)){
                val mul=effectiveness(move.type,target.types)
                if(move.power>0&&mul==0.0){event("MOVE_MISS",actor.uid,target.uid,move=move,multiplier=0.0);continue}
                val n=applyTargetHits(actor,target,move,false,mul)
                inflicted+=n;event("AFTER_DAMAGE_TAKEN",actor.uid,target.uid,n,move,mul)
            }else event("MOVE_MISS",actor.uid,target.uid,move=move)
        }
        effects.resolved(actor,move,inflicted,this)
        for(mon in track){val prev=beforeStatuses[mon.uid].orEmpty();mon.statuses.keys.filter{it !in prev}.forEach{st->event("STATUS_APPLIED",actor.uid,mon.uid,move=SpireMove(st,0))}}
        if(move.healFraction>0)actor.heal(effects.healing(actor,move))
        if(move.recoilFraction>0&&inflicted>0)actor.hp=(actor.hp-floor(inflicted*move.recoilFraction).toInt().coerceAtLeast(1)).coerceAtLeast(0)
    }
    private fun advanceGauge(opening:Boolean){
        val openingActed=mutableSetOf<String>()
        while(phase !in setOf(SpireBattlePhase.VICTORY,SpireBattlePhase.DEFEAT)){
            syncSpeeds()
            val alivePlayers=onlineAlivePlayers()
            val aliveEnemies=enemies.filter{it.alive}
            if(alivePlayers.isEmpty()||aliveEnemies.isEmpty()){decideResult();return}
            val forced=alivePlayers.firstOrNull{it.forcedSwitch}
            if(forced!=null){
                alivePlayers.forEach{p->p.ended=(p.id!=forced.id)}
                playerAv[forced.id]=0.0;phase=SpireBattlePhase.PLAYER;revision++;event("REFRESH_INTENTS");return
            }
            val pCandidates=alivePlayers.map{it to (playerAv[it.id]?:baseAv(it.active))}
            val eCandidates=aliveEnemies.filter{opening&&!anyPlayerTurnStarted&&(it.uid !in openingActed)||!(opening&&!anyPlayerTurnStarted)}.map{it to (enemyAv[it.uid]?:baseAv(it))}
            val minP=pCandidates.minOfOrNull{it.second}?:Double.MAX_VALUE
            val minE=eCandidates.minOfOrNull{it.second}?:Double.MAX_VALUE
            val dt=minOf(minP,minE).coerceAtLeast(0.0)
            for(p in alivePlayers)playerAv[p.id]=((playerAv[p.id]?:baseAv(p.active))-dt).coerceAtLeast(0.0)
            for(e in aliveEnemies){
                val next=((enemyAv[e.uid]?:baseAv(e))-dt).coerceAtLeast(0.0)
                enemyAv[e.uid]=if(opening&&!anyPlayerTurnStarted&&e.uid in openingActed)next.coerceAtLeast(0.5)else next
            }
            if(minP<=minE+0.01){
                val firstTurn=!anyPlayerTurnStarted
                anyPlayerTurnStarted=true
                if(!firstTurn)round++
                phase=SpireBattlePhase.PLAYER
                for(p in alivePlayers){
                    val ready=(playerAv[p.id]?:0.0)<=0.01
                    if(ready){
                        p.ended=false;p.normalSwitchUsed=false;p.energy=p.maxEnergy;playerAv[p.id]=0.0
                        if(p.active.alive)p.active.deck?.beginTurn(round)
                    }else p.ended=true
                }
                revision++;event("REFRESH_INTENTS");event("ROUND_START");return
            }
            val actor=aliveEnemies.filter{!opening||anyPlayerTurnStarted||it.uid !in openingActed}.minByOrNull{enemyAv[it.uid]?:Double.MAX_VALUE}?:continue
            val intent=intents.firstOrNull{enemies.getOrNull(it.actor)?.uid==actor.uid}?:run{event("REFRESH_INTENTS");intents.firstOrNull{enemies.getOrNull(it.actor)?.uid==actor.uid}}
            if(intent!=null){
                phase=SpireBattlePhase.ENEMY;event("ENEMY_PHASE_START")
                executeEnemyMove(intent)
                event("ENEMY_ACTION_RESOLVED",actor.uid)
                event("ENEMY_PHASE_END")
                if(decideResult())return
                tickStatuses(actor)
                if(decideResult())return
            }
            if(opening&&!anyPlayerTurnStarted)openingActed.add(actor.uid)
            enemyAv[actor.uid]=baseAv(actor);lastSpeed[actor.uid]=spd(actor)
            intents=intents.filter{enemies.getOrNull(it.actor)?.uid!=actor.uid}
            event("REFRESH_INTENTS")
        }
    }
    private fun enemyPhase(){
        phase=SpireBattlePhase.ENEMY;event("ENEMY_PHASE_START")
        val actions=intents.filter{it.actor in enemies.indices&&enemies[it.actor].alive}
        for((index,intent) in actions.withIndex()){
            val actor=enemies[intent.actor]
            executeEnemyMove(intent)
            event("ENEMY_ACTION_RESOLVED",actor.uid)
            if(index==actions.lastIndex){event("ENEMY_PHASE_END");if(decideResult())return}
            else if(decideResult())return
        }
        if(actions.isEmpty()){event("ENEMY_PHASE_END");if(decideResult())return}
        // Active-only periodic effects. Bench timers and poison/burn HP do not tick.
        for(mon in activePlayers().map{it.active}.filter{it.alive}+enemies.filter{it.alive})tickStatuses(mon)
        event("ROUND_END");if(decideResult())return
        round++;phase=SpireBattlePhase.PLAYER
        activePlayers().forEach{pl->
            if(pl.id in offlinePlayers){pl.ended=true}
            else{pl.ended=false;pl.normalSwitchUsed=false;pl.energy=pl.maxEnergy;if(pl.active.alive)pl.active.deck?.beginTurn(round)}
        }
        revision++;event("ROUND_START")
    }
    private fun decideResult():Boolean {
        if(phase in setOf(SpireBattlePhase.VICTORY,SpireBattlePhase.DEFEAT))return true
        if(activePlayers().none{it.alive}){phase=SpireBattlePhase.DEFEAT;activePlayers().flatMap{it.party}.forEach{it.endBattle()};event("BATTLE_LOST");return true}
        if(enemies.none{it.alive}){
            phase=SpireBattlePhase.VICTORY;victoryCount++
            val all=activePlayers().flatMap{it.party};all.filter{it.alive}.forEach{winningSurvivors.add(it.uid)}
            event("PROGRESSION");all.filter{it.uid !in winningSurvivors}.forEach{it.revive(0.2)}
            event("BATTLE_WON");all.forEach{it.endBattle()};return true
        }
        return false
    }
    fun rest(player:UUID){require(phase==SpireBattlePhase.VICTORY);participant(player).party.filter{it.alive}.forEach{it.heal(floor(it.maxHp*0.3).toInt())}}
}

object SpirePartyRules {
    fun disjoint(types:List<Set<String>>):Boolean=types.indices.all{i->types.indices.all{j->i==j||types[i].intersect(types[j]).isEmpty()}}
    fun stageBaseBudget(act:Int,tier:String,encounterId:String):Int{
        val late=tier in setOf("ELITE","BOSS")||encounterId.endsWith("03")||encounterId.endsWith("04")||encounterId.endsWith("05")||encounterId.endsWith("06")||encounterId.endsWith("07")
        return when(act){
            1->if(late)240 else 180
            2->if(late)455 else 368
            else->if(late)760 else 640
        }
    }
    fun gradeMultiplier(tier:String):Double=when(tier){"ELITE"->1.55;"BOSS"->2.4;else->1.0}
    fun encounterHpBudget(act:Int,tier:String,encounterId:String,weights:List<Double>,players:Int):List<Int> =
        hpBudget(stageBaseBudget(act,tier,encounterId),gradeMultiplier(tier),weights,players)
    fun hpBudget(base:Int,grade:Double,weights:List<Double>,players:Int):List<Int>{
        require(players in 1..4&&weights.size in 1..3&&weights.all{it>0})
        val party=listOf(1.0,1.85,2.70,3.55)[players-1]
        val count=listOf(1.0,1.10,1.15)[weights.size-1]
        val total=kotlin.math.round(base*grade*party*count).toInt()
        var allocated=0
        return weights.mapIndexed{i,w->if(i==weights.lastIndex)total-allocated else floor(total*w/weights.sum()).toInt().also{allocated+=it}}
    }
}
