import json, pathlib, collections, sys
ROOT=pathlib.Path(__file__).resolve().parents[1] / 'data'
d=json.loads((ROOT/'all_definitions_v4.json').read_text(encoding='utf-8'))
reg=d['registries']
condition_ids=sorted(reg['conditions'])
action_ids=sorted(reg['actions'])
event_ids=sorted(reg['events'])
ai_cond=sorted(reg['enemy_ai_conditions'])
rule_schema={
 'type':'object',
 'required':['rule_id','event','conditions','actions','activation_limit','definition_status','dispatch_scope'],
 'properties':{
   'rule_id':{'type':'string','minLength':4},
   'event':{'enum':event_ids},
   'priority':{'type':'integer'},
   'conditions':{'type':'array','minItems':1,
        'items':{'type':'object','required':['op'],'properties':{'op':{'enum':condition_ids}}}},
   'actions':{'type':'array','minItems':1,
        'items':{'type':'object','required':['op','value','value_kind'],
                 'properties':{'op':{'enum':action_ids},'value':{'type':'number'},
                               'value_kind':{'enum':['scalar','fraction','multiplier','percentage_points']}}}},
   'activation_limit':{'type':'object','required':['scope','max_activations'],
        'properties':{'scope':{'type':'string'},'max_activations':{'type':['integer','null'],'minimum':1}}},
   'definition_status':{'const':'DEFINED_UNTESTED'},
   'dispatch_scope':{'enum':['PER_TARGET_DAMAGE','PER_TARGET_ATTACK','PER_MOVE','PER_SOURCE_EVENT']}
 }
}

relic_schema={'type':'object','required':['id','name_ko','grade','rules','scope_limit','test_status'],
 'properties':{'id':{'type':'string'},'name_ko':{'type':'string','minLength':2},'grade':{'enum':['일반','희귀','고급','고유']},
 'rules':{'type':'array','minItems':1,'items':rule_schema},'test_status':{'const':'NOT_IMPLEMENTED'},
 'scope_limit':{'type':'object','required':['once_per_run_grade_offer','same_id_duplicate_per_player']}}}
enemy_schema={'type':'object','required':['enemy_id','name_ko','species_id','move_pool','ai_rules','target_policy','ai_status'],
 'properties':{'enemy_id':{'type':'string'},'name_ko':{'type':'string'},'species_id':{'type':'string'},
 'move_pool':{'type':'array','minItems':3,'maxItems':5,'items':{'type':'object','required':['id','validation']}},
 'ai_rules':{'type':'array','minItems':1,'items':{'type':'object','required':['priority','condition','choose','cooldown_rounds','intent_kind'],
      'properties':{'priority':{'type':'integer'},'condition':{'type':'object','properties':{'op':{'enum':ai_cond}}},
                    'choose':{'type':'object','required':['type'],'properties':{'type':{'enum':['FIXED','WEIGHTED']}}},
                    'cooldown_rounds':{'type':'integer','minimum':0}}}},
 'target_policy':{'const':'ALL_ALIVE_PLAYERS'},'ai_status':{'const':'DEFINED_UNTESTED'}}}
enc_schema={'type':'object','required':['encounter_id','enemy_instances','enemy_count','tier','act','present_trainer_entity','encounter_director'],
 'properties':{'encounter_id':{'type':'string'},'enemy_instances':{'type':'array','minItems':1,'maxItems':3,'items':{'type':'object','required':['enemy_id','threat_weight']}},
  'enemy_count':{'type':'integer','minimum':1,'maximum':3},'tier':{'enum':['NORMAL','ELITE','BOSS']},'act':{'type':'integer','minimum':1,'maximum':3},
  'present_trainer_entity':{'const':False}}}
boss_schema={'type':'object','required':['profile_id','encounter_id','phase_count','phases','phase_change_policy'],
 'properties':{'profile_id':{'type':'string'},'encounter_id':{'type':'string'},'phase_count':{'type':'integer','minimum':2,'maximum':3},
  'phases':{'type':'array','minItems':2,'maxItems':3,'items':{'type':'object','required':['phase_index','entry_condition','ai_override','intent_visibility']}},
  'phase_change_policy':{'const':'ON_GROUP_HP_THRESHOLD_AFTER_MOVE_RESOLVED'}}}
v41_sections = ['pokemon_party', 'switch', 'status', 'victory', 'battle_result', 'enemy_targets', 'run_state', 'rest_heal', 'pending']
schema={'$schema':'https://json-schema.org/draft/2020-12/schema','title':'CobbleSpire v4 Declarative Rules','type':'object',
 'required':['schema_version','registries','global_rules','characters','relics','enemy_actors','encounters','boss_phases','event_processing_contract'],
 'properties':{'schema_version':{'type':'string'},'registries':{'type':'object'},'global_rules':{'type':'object','required':v41_sections},
  'characters':{'type':'array','minItems':50,'maxItems':50},'relics':{'type':'array','minItems':165,'maxItems':165,'items':relic_schema},
  'enemy_actors':{'type':'array','minItems':25,'maxItems':25,'items':enemy_schema},
  'encounters':{'type':'array','minItems':21,'maxItems':21,'items':enc_schema},
  'boss_phases':{'type':'array','minItems':6,'maxItems':6,'items':boss_schema},
  'event_processing_contract':{'type':'object'}}}
(ROOT/'cobblespire_v4.schema.json').write_text(json.dumps(schema,ensure_ascii=False,indent=2),encoding='utf-8')
errors=[]
try:
 import jsonschema
 jsonschema.validate(d,schema)
 schema_result='PASS_JSONSCHEMA_DRAFT_2020_12'
except ImportError:
 schema_result='SCHEMA_LIBRARY_MISSING_STRUCTURE_CHECKS_ONLY'
except Exception as e:
 schema_result='FAIL'
 errors.append('json schema: '+str(e))
# Extra cross checks beyond schema validation.
relics=d['relics']; enemy=d['enemy_actors']; enc=d['encounters']; boss=d['boss_phases']
if len({x['id'] for x in relics})!=165: errors.append('relic duplicate IDs')
if len({x['enemy_id'] for x in enemy})!=25: errors.append('enemy duplicate IDs')
if len({x['encounter_id'] for x in enc})!=21: errors.append('encounter duplicate IDs')
if len({x['profile_id'] for x in boss})!=6: errors.append('boss duplicate profiles')
if any(x['target_policy']!='ALL_ALIVE_PLAYERS' for x in enemy):errors.append('enemy targeting mismatch')
if any(x['present_trainer_entity'] for x in enc):errors.append('trainer entity exposed')
if any(len(x['enemy_instances'])!=x['enemy_count'] for x in enc):errors.append('encounter enemy count mismatch')
actor_ids={x['enemy_id'] for x in enemy}; phase_by_enc={x['encounter_id'] for x in boss}
for e in enc:
 if any(q['enemy_id'] not in actor_ids for q in e['enemy_instances']):errors.append('bad actor ref '+e['encounter_id'])
 if (e['tier']=='BOSS')!=(e['encounter_id'] in phase_by_enc):errors.append('boss phase mismatch '+e['encounter_id'])
for a in enemy:
 p={m['id'] for m in a['move_pool']}
 if not any(rule['condition']['op']=='ALWAYS' for rule in a['ai_rules']):errors.append('actor no default '+a['enemy_id'])
 for rule in a['ai_rules']:
  m=[rule['choose'].get('move_id')] if rule['choose']['type']=='FIXED' else [q['move_id'] for q in rule['choose']['candidates']]
  if any(t not in p for t in m):errors.append('bad move '+a['enemy_id'])
for b in boss:
 if len(b['phases'])!=b['phase_count']:errors.append('boss phase count '+b['encounter_id'])
 thresholds=[x['entry_condition'].get('threshold') for x in b['phases'][1:]]
 if thresholds != sorted(thresholds,reverse=True):errors.append('phase thresholds nonmonotonic '+b['encounter_id'])
 if any(x['entry_condition']['op'] not in ('AT_BATTLE_START','BOSS_GROUP_HP_FRACTION_LTE') for x in b['phases']):errors.append('phase entry op invalid '+b['encounter_id'])
 if any(y['op'] not in d['registries']['boss_phase_actions'] for x in b['phases'] for y in x['on_entry_effects']):errors.append('phase action invalid '+b['encounter_id'])
for r in relics:
 for rule in r['rules']:
  if rule['event'] not in reg['events'] or any(q['op'] not in reg['conditions'] for q in rule['conditions']) or any(q['op'] not in reg['actions'] for q in rule['actions']):errors.append('relic registry ref '+r['id'])
 if r['grade'] in ('고급','고유') and r['group_exclusive']!='TOP_TIER_RELIC':errors.append('high grade group missing '+r['id'])
 if r['grade'] in ('고급','고유') and not r['scope_limit']['once_per_run_grade_offer']:errors.append('grade cap missing '+r['id'])
# explicit scoped unit examples: one global high grade offer each + no same player both tiers.
def can_offer(grade, run_counts): return run_counts.get(grade,0)<(1 if grade in ('고급','고유') else 10**9)
if can_offer('고급',{'고급':1}) or not can_offer('고유',{'고급':1}):errors.append('grade offer counter logic')
if d['global_rules']['baseline']['type_immunity_relief']['replacement']!=0.5: errors.append('immunity A policy')
if not all(z==1 for z in d['global_rules']['baseline']['enemy_damage_per_target_multiplier_by_party_size'].values()):errors.append('multiplayer per-target damage scaling')
rules = d['global_rules']
snapshot = json.loads((ROOT / 'balance_global_rules.json').read_text(encoding='utf-8'))
canonical = json.loads((ROOT / 'combat_and_run_rules_v4_1.json').read_text(encoding='utf-8'))
if snapshot != rules or canonical != {k: rules.get(k) for k in v41_sections}:
 errors.append('v4.1 rule snapshot mismatch')
if reg != json.loads((ROOT / 'registry_events_conditions_actions.json').read_text(encoding='utf-8')):
 errors.append('event registry snapshot mismatch')
from validate_combat_rules import check_combat_rules
combat_checks = check_combat_rules(rules, relics)
errors.extend(name for name, passed in combat_checks.items() if not passed)
check={'schema_validation':schema_result,'static_reference_validation':'PASS' if not errors else 'FAIL','errors':errors,
       'v4_1_rule_examples':combat_checks,
       'counts':{'relics':len(relics),'relic_effect_rules':sum(len(z['rules']) for z in relics),'events':len(reg['events']),
                 'conditions':len(reg['conditions']),'actions':len(reg['actions']),'enemy_actors':len(enemy),
                 'ai_rules':sum(len(z['ai_rules']) for z in enemy),'encounters':len(enc),'boss_phases':sum(len(z['phases']) for z in boss)},
       'outstanding_verification':['Cobblemon move learnsets','Mega Showdown form IDs','showdown execution bridge','runtime handlers','simulation winrates','relic player per-run uniqueness tests'],
       'game_execution_tested':False}
(ROOT/'qa_validation_detailed.json').write_text(json.dumps(check,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(check,ensure_ascii=False,indent=2))
if errors:sys.exit(1)
