const fs=require('fs'),path=require('path'),crypto=require('crypto');
if(!process.argv[2])throw Error('Usage: node scripts/export_move_snapshot.cjs <installed-showdown-directory>');
const ROOT=path.resolve(__dirname,'..');
const {Dex}=require(path.join(path.resolve(process.argv[2]),'sim'));
const fields=['id','name','basePower','type','category','accuracy','target','priority','pp','status','volatileStatus','boosts','self','secondary','secondaries','heal','drain','recoil','multihit','critRatio','willCrit','flags','weather','terrain','sideCondition','pseudoWeather','selfdestruct','damage'];
const moves={};
for(const m of Dex.moves.all()){
 let row={};for(const f of fields)if(m[f]!==undefined)row[f]=m[f];
 const callbacks=Object.keys(m).filter(k=>typeof m[k]==='function');
 const seen=new WeakSet();function walk(v,p){if(typeof v==='function'){callbacks.push(p);return;}if(v&&typeof v==='object'&&!seen.has(v)){seen.add(v);for(const[k,x]of Object.entries(v))walk(x,p+'.'+k);}}
 for(const[k,v]of Object.entries(row))walk(v,k);
 row.callbacks=[...new Set(callbacks)];
 const annotation=new Set([...fields,'exists','num','contestType','desc','shortDesc','fullname','effectType','gen','sourceEffect','baseMoveType','pressureTarget','maxMove','zMove','ignoreImmunity']);
 row.extraEffects=Object.fromEntries(Object.entries(m).filter(([k,v])=>!annotation.has(k)&&typeof v!=='function'&&v!=null&&v!==false&&v!==''&&v!==0));
 moves[m.id]=row;
}
const types={};for(const t of Dex.types.all())types[t.name.toLowerCase()]=t.damageTaken;
const out={source:'Installed COBBLEVERSE 1.7.42 Cobblemon Showdown data',cobblemon:'1.7.3',moves,types};
const dest=path.join(ROOT,'design/data/move_effect_snapshot.json');fs.writeFileSync(dest,JSON.stringify(out,null,2)+'\n');console.log(Object.keys(moves).length,'moves exported');
