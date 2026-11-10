package dev.cobblespire
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

class BattleKernelTest {
    private val id=UUID(1,2)
    private fun mon(name:String,hp:Int=100,move:SpireMove=SpireMove("tackle",1,10))=SpireCombatant(name,name,setOf("normal"),hp,BattleDeck((1L..8L).map{MoveInstance(it,move)},Random(name.hashCode())))
    private fun battle(party:List<SpireCombatant>,enemies:List<SpireCombatant> = listOf(mon("enemy",999)),hook:SpireBattleHook=SpireBattleHook{_,_->})=SpireBattle(listOf(SpireBattlePlayer(id,party)),enemies,42,hook=hook).also{it.start()}
    private fun use(b:SpireBattle,target:Int=0){val slot=b.players[0].active.deck!!.slots[0]!!;b.useMove(id,0,slot.uid,target,UUID.randomUUID().toString(),b.revision)}
    private fun end(b:SpireBattle)=b.endTurn(id,UUID.randomUUID().toString(),b.revision)
    private fun deck(d:BattleDeck)=listOf(d.slots.map{it?.uid},d.draw.map{it.uid},d.discard.map{it.uid},d.exhausted.map{it.uid})
    @Test fun `A to B to A preserves independent deck order piles and slots`(){
        val a=mon("a");val b=mon("b");val run=battle(listOf(a,b));use(run);val before=deck(a.deck!!)
        run.switch(id,1,"switch",run.revision);end(run);run.switch(id,0,"return",run.revision)
        assertEquals(before,deck(a.deck));assertEquals(2,run.players[0].energy)
    }
    @Test fun `normal switching costs one and is limited to once per round`(){
        val b=battle(listOf(mon("a"),mon("b")));b.switch(id,1,"one",b.revision)
        assertEquals(2,b.players[0].energy);assertThrows(IllegalArgumentException::class.java){b.switch(id,0,"two",b.revision)}
        assertEquals(2,b.players[0].energy)
    }
    @Test fun `sleep and paralysis permit switching and bench timers freeze`(){
        val a=mon("a");a.afflict("sleep",1,3);a.afflict("paralysis",1,2);val b=battle(listOf(a,mon("b")))
        b.switch(id,1,"s",b.revision);repeat(3){end(b)}
        assertEquals(3,a.statuses["sleep"]!!.remaining);assertEquals(2,a.statuses["paralysis"]!!.remaining)
    }
    @Test fun `switching clears ranks seed and trap but keeps mega and poison`(){
        val a=mon("a");a.form="mega";a.stages["atk"]=2;listOf("leechseed","trapping","poison").forEach{a.afflict(it,1)}
        val b=battle(listOf(a,mon("b")));b.switch(id,1,"s",b.revision)
        assertTrue(a.stages.isEmpty());assertEquals(setOf("poison"),a.statuses.keys);assertEquals("mega",a.form)
        val hp=a.hp;end(b);assertEquals(hp,a.hp)
    }
    @Test fun `new sleep is not decremented in its application round`(){
        val b=battle(listOf(mon("a")));b.intents=listOf(EnemyIntent(0,SpireMove("sleep",1,status="sleep",duration=2)))
        end(b);assertEquals(2,b.players[0].active.statuses["sleep"]!!.remaining)
        b.intents=emptyList();end(b);assertEquals(1,b.players[0].active.statuses["sleep"]!!.remaining)
    }
    @Test fun `an enemy action never spills into a benched replacement`(){
        val a=mon("a",2);val bench=mon("b",99);val b=battle(listOf(a,bench),listOf(mon("x"),mon("y")))
        b.intents=listOf(EnemyIntent(0,SpireMove("attack",1,10)),EnemyIntent(1,SpireMove("attack",1,10)));end(b)
        assertEquals(0,a.hp);assertEquals(99,bench.hp);assertTrue(b.players[0].forcedSwitch)
        b.switch(id,1,"forced",b.revision);assertEquals(3,b.players[0].energy);assertFalse(b.players[0].normalSwitchUsed)
    }
    @Test fun `a simultaneous final knockout is defeat when the party has no survivor`(){
        val a=mon("a",1,SpireMove("recoil",1,10,recoilFraction=1.0));val b=battle(listOf(a),listOf(mon("x",1)));use(b)
        assertEquals(SpireBattlePhase.DEFEAT,b.phase);assertEquals(0,b.victoryCount);assertEquals(0,a.hp)
    }
    @Test fun `a surviving bench wins and only fainted Pokemon receive victory revival`(){
        val a=mon("a",19,SpireMove("recoil",1,10,recoilFraction=1.0));a.hp=1
        val bench=mon("b",100);bench.hp=10;val b=battle(listOf(a,bench),listOf(mon("x",1)));use(b)
        assertEquals(SpireBattlePhase.VICTORY,b.phase);assertEquals(3,a.hp);assertEquals(10,bench.hp);assertEquals(setOf("b"),b.winningSurvivors);assertEquals(1,b.victoryCount)
    }
    @Test fun `victory revival floors fractions and has a minimum of one HP`(){
        val a=mon("a",19);val b=mon("b",4);a.hp=0;b.hp=0;a.revive(.2);b.revive(.2);assertEquals(3,a.hp);assertEquals(1,b.hp)
    }
    @Test fun `rest heals each living Pokemon by its own maximum and does not revive`(){
        val a=mon("a",100);a.hp=40;val b=mon("b",200);b.hp=180;val c=mon("c",100);c.hp=0
        listOf(a,b,c).filter{it.alive}.forEach{it.heal((it.maxHp*.3).toInt())};assertEquals(listOf(70,200,0),listOf(a.hp,b.hp,c.hp))
    }
    @Test fun `duplicate requests never consume energy or a second slot`(){
        val b=battle(listOf(mon("a")));val p=b.players[0];val m=p.active.deck!!.slots[0]!!;val rev=b.revision
        b.useMove(id,0,m.uid,0,"same",rev);val hp=b.enemies[0].hp;val before=deck(p.active.deck!!)
        assertThrows(IllegalArgumentException::class.java){b.useMove(id,0,m.uid,0,"same",b.revision)}
        assertEquals(2,p.energy);assertEquals(hp,b.enemies[0].hp);assertEquals(before,deck(p.active.deck!!))
    }
    @Test fun `real enemy phase end runs rescue before result judgment`(){
        val owner=SpireBattlePlayer(id,listOf(mon("owner",100)));val friend=SpireBattlePlayer(UUID(3,4),listOf(mon("friend",19).also{it.hp=1}))
        var rescue=0;val b=SpireBattle(listOf(owner,friend),listOf(mon("enemy")),42,hook=SpireBattleHook{e,_->if(e.name=="ENEMY_PHASE_END"&&owner.alive&&!friend.alive){friend.active.revive(.2);rescue++}})
        b.intents=listOf(EnemyIntent(0,SpireMove("attack",1,5)));b.start();b.endTurn(id,"one",b.revision);b.endTurn(friend.id,"two",b.revision)
        assertEquals(1,rescue);assertEquals(3,friend.active.hp);assertEquals(SpireBattlePhase.PLAYER,b.phase)
    }
    @Test fun `early defeat does not synthesize enemy phase end`(){
        var ended=0;val b=battle(listOf(mon("a",1)),listOf(mon("x"),mon("y")),SpireBattleHook{e,_->if(e.name=="ENEMY_PHASE_END")ended++})
        b.intents=listOf(EnemyIntent(0,SpireMove("attack",1,5)),EnemyIntent(1,SpireMove("attack",1,5)));end(b)
        assertEquals(SpireBattlePhase.DEFEAT,b.phase);assertEquals(0,ended)
    }
    @Test fun `attack type immunity is softened only for player attacks`(){
        val a=SpireBattlePlayer(id,listOf(mon("a",100,SpireMove("normal",1,10))));val enemy=mon("enemy",100)
        val b=SpireBattle(listOf(a),listOf(enemy),42,effectiveness={_,_->0.0});b.start();use(b);assertEquals(95,enemy.hp)
        b.intents=listOf(EnemyIntent(0,SpireMove("immune",1,10)));end(b);assertEquals(100,a.active.hp)
    }
    @Test fun `party type constraints compare both types pairwise`(){
        assertTrue(SpirePartyRules.disjoint(listOf(setOf("fire","flying"),setOf("water"),setOf("grass","poison"))))
        assertFalse(SpirePartyRules.disjoint(listOf(setOf("fire","flying"),setOf("water","flying"),setOf("grass"))))
    }
    @Test fun `HP budget applies party and enemy count multipliers once`(){
        val hp=SpirePartyRules.hpBudget(240,1.0,listOf(1.0,1.0,1.0),3);assertEquals(745,hp.sum());assertEquals(listOf(248,248,249),hp)
    }
    @Test fun `training points and act minimum levels follow the source workbook`(){
        assertEquals(1,SpireProgression.level(0));assertEquals(5,SpireProgression.level(19));assertEquals(8,SpireProgression.level(40));assertEquals(10,SpireProgression.level(100));assertEquals(32,SpireProgression.enterAct(32,2));assertEquals(40,SpireProgression.enterAct(2,3))
    }
}
