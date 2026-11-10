const fs=require('fs'),path=require('path');
if(!process.argv[2])throw Error('Usage: node scripts/export_damage_reference.cjs <installed-showdown-directory>');
const ROOT=path.resolve(__dirname,'..');
const {BattleStream,Dex}=require(path.join(path.resolve(process.argv[2]),'sim'));
const cases=[
 ['Charizard','No Ability','Rattata','No Ability','flamethrower'],
 ['Blastoise','No Ability','Rattata','No Ability','watergun'],
 ['Venusaur','No Ability','Blastoise','No Ability','gigadrain'],
 ['Scizor','Technician','Rattata','No Ability','bulletpunch'],
 ['Scizor','Technician','Rattata','No Ability','visegrip'],
 ['Medicham','Pure Power','Rattata','No Ability','drainpunch'],
 ['Azumarill','Huge Power','Rattata','No Ability','aquajet'],
 ['Heracross','Guts','Rattata','No Ability','closecombat',{status:'brn'}],
 ['Heracross','No Ability','Rattata','No Ability','closecombat',{status:'brn'}],
 ['Charizard','No Ability','Snorlax','Thick Fat','flamethrower'],
 ['Blastoise','No Ability','Camerupt','Solid Rock','watergun'],
 ['Nidoking','Sheer Force','Rattata','No Ability','poisonjab'],
 ['Nidoking','Sheer Force','Rattata','No Ability','octazooka'],
 ['Charizard','Blaze','Rattata','No Ability','flamethrower',{lowHp:true}],
 ['Houndoom','Flash Fire','Rattata','No Ability','flamethrower',{flashfire:true}],
 ['Charizard','No Ability','Rattata','No Ability','slash',{atk:-2,def:2}],
 ['Charizard','No Ability','Rattata','Battle Armor','slash'],
 ['Charizard','No Ability','Rattata','No Ability','swift',{spread:true}],
 ['Lucario','No Ability','Tyranitar','No Ability','aurasphere'],
 ['Charizard','No Ability','Blastoise','No Ability','flamethrower'],
];
const out=[];let id=0;
for(const [source,ability,target,targetAbility,move,extra={}]of cases){
 const stream=new BattleStream({noCatch:false});stream.write('>start '+JSON.stringify({formatid:'gen9customgame@@@!teampreview',seed:[1,2,3,4]}));
 function mon(species,ability){const moves=[move];return {name:species,species,ability,moves,level:50,uuid:'spire-fixture-'+(++id),movesInfo:moves.map(m=>({pp:Dex.moves.get(m).pp,maxPp:Dex.moves.get(m).pp})),evs:{},ivs:{},item:''};}
 stream.write('>player p1 '+JSON.stringify({name:'p1',team:[mon(source,ability)]}));stream.write('>player p2 '+JSON.stringify({name:'p2',team:[mon(target,targetAbility)]}));
 const b=stream.battle,a=b.p1.active[0],t=b.p2.active[0];if(extra.status)a.status=extra.status;if(extra.lowHp)a.hp=Math.floor(a.maxhp/4);if(extra.flashfire){if(!a.addVolatile('flashfire'))throw Error('Flash Fire fixture failed');}
 if(extra.atk)a.boosts.atk=extra.atk;if(extra.def)t.boosts.def=extra.def;
 const record=p=>({species:p.species.id,types:p.types.map(t=>t.toLowerCase()),ability:p.ability,hp:p.hp,maxHp:p.maxhp,stats:{...p.storedStats,hp:p.maxhp},boosts:p.boosts,status:p.status||''});
 for(const critical of[false,true])for(let variance=85;variance<=100;variance++){
  b.randomizer=n=>Math.floor(n*variance/100);const nativeMove={...Dex.moves.get(move),willCrit:critical,spreadHit:!!extra.spread};b.runEvent('ModifyMove',a,t,nativeMove,nativeMove);const value=b.actions.getDamage(a,t,nativeMove);
  if(typeof value!=='number')throw Error(source+' '+move+' '+value);
  out.push({source:record(a),target:record(t),move,critical,variance,spread:!!extra.spread,flashfire:!!extra.flashfire,damage:value});
 }
}
fs.mkdirSync(path.join(ROOT,'src/test/resources'),{recursive:true});fs.writeFileSync(path.join(ROOT,'src/test/resources/damage_reference_cases.json'),JSON.stringify({source:'Installed Cobblemon Showdown actions.getDamage, Gen9 level50 IV31 EV0 neutral nature',vectors:out},null,2)+'\n');console.log('Native damage reference vectors:',out.length);
