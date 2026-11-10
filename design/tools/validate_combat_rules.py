"""Validate v4.1 declarations and representative rule outcomes."""

def check_combat_rules(rules, relics):
    party, switch, status = (rules[key] for key in ('pokemon_party', 'switch', 'status'))
    victory, run = rules['victory'], rules['run_state']
    relic = {item['id']: item['rules'] for item in relics}
    normal, forced = switch['normal'], switch['forced']

    def can_switch(energy, used):
        return energy >= normal['energy_cost'] and used < normal['uses_per_player_per_round']

    def tick(remaining, active, application_round, event):
        if event != status['tick_event']:
            return remaining
        if (status['active_only'] and not active) or (status['skip_application_round'] and application_round):
            return remaining
        return remaining - status['max_ticks_per_round']

    def settle_hp(hp, won):
        if not won:
            return hp
        return hp if hp and victory['preserve_survivor_hp'] else 100 * victory['fainted_restore_fraction']

    aid = relic[victory['party_heal_relic']][0]
    owner = relic[victory['owner_heal_relic']][0]
    rescue = relic[victory['in_battle_revive_relic']][0]
    aid_hp = [settle_hp(hp, True) + (100 * aid['actions'][0]['value'] if hp else 0) for hp in (0, 50)]
    sequence = victory['sequence']
    deck = party['battle_deck_state']
    return {
        'reentry_preserves_each_pokemon_battle_deck': deck['scope'] == 'CURRENT_BATTLE'
        and deck['owner'] == party['deck_owner'] == 'POKEMON' and deck['on_reentry'] == 'RESUME_OWN_STATE'
        and deck['preserved_fields'] == ['MOVE_SLOTS', 'DRAW_ORDER', 'DISCARD_PILE', 'EXHAUST_PILE'],
        **check_battle_result(rules['battle_result'], rescue),
        **check_party_edges(rules),
        'starter_party_and_independent_decks': party['max_per_player'] == 3 and party['active_per_player'] == 1
        and party['starter'] == {'chosen_leader': 1, 'automatic_partners': 2, 'partner_type_rule': 'PAIRWISE_DISJOINT_ALL_TYPES'}
        and party['deck_owner'] == 'POKEMON',
        'switch_cost_and_limit': can_switch(1, 0) and not can_switch(0, 0) and not can_switch(3, 1)
        and normal['reset_at'] == 'ROUND_START' and normal['phase'] == 'PLAYER_PHASE',
        'forced_switch_is_free': forced['energy_cost'] == 0 and not forced['consumes_normal_use'],
        'forced_switch_timing': forced['finish_current_enemy_action']
        and not forced['redirect_current_enemy_action_to_replacement']
        and forced['resolve_before'] == 'NEXT_PLAYER_ACTION',
        'reroll_is_separate': switch['separate_from_move_slot_reroll']
        and relic['S02'][0]['actions'][0]['op'] == 'GRANT_MANUAL_REROLL'
        and [r['actions'][0]['op'] for r in relic['B02']] == ['INCREASE_BASE_ENERGY', 'DENY_MANUAL_REROLL'],
        'sleep_and_paralysis_timing': status['timed_statuses'] == ['SLEEP', 'PARALYSIS']
        and tick(3, True, True, 'ROUND_END') == 3 and tick(3, True, False, 'ROUND_END') == 2
        and tick(2, False, False, 'ROUND_END') == 2 and tick(2, True, False, 'ROUND_END') == 1
        and tick(2, True, False, 'BATTLE_WON') == 2 and not status['tick_on_battle_result'],
        'status_cleared_at_battle_end': status['clear_on'] == ['VICTORY', 'DEFEAT']
        and status['clear_scope'] == 'ALL_PARTY_POKEMON' and status['clear_all_ailments'],
        'victory_and_defeat_hp': [settle_hp(hp, True) for hp in (0, 46, 0, 72)] == [20, 46, 20, 72]
        and settle_hp(0, False) == 0 and victory['max_resolutions_per_battle'] == 1,
        'victory_relic_snapshot': aid_hp == [20, 53] and victory['alive_only_relics_use_pre_revive_snapshot']
        and owner['conditions'] == [{'op': 'OWNER_ALIVE'}] and aid['conditions'] == [{'op': 'COOP_ONLY'}]
        and sequence.index('SNAPSHOT_SURVIVAL_AND_HP') < sequence.index('REVIVE_FAINTED') < sequence.index('DISPATCH_VICTORY_RELICS'),
        'rescue_remains_in_battle': rescue['event'] == 'ENEMY_PHASE_END'
        and rescue['activation_limit'] == {'scope': 'PARTY_ACT', 'max_activations': 1}
        and {c['op'] for c in rescue['conditions']} == {'OWNER_ALIVE', 'KO_ALLY_EXISTS', 'COOP_ONLY'}
        and settle_hp(28, True) == 28,
        'run_is_memory_only': run['storage'] == 'MEMORY_ONLY' and not run['save_enabled'] and not run['load_enabled']
        and run['on_end'] == run['on_server_restart'] == 'DISCARD_RUN'
        and run['on_restart'] == 'DISCARD_AND_CREATE_NEW_RUN' and 'run_persistence' not in rules,
    }


def check_battle_result(policy, rescue):
    sequence = policy['sequence']
    phase_end = policy['enemy_phase_end']

    def result(active, bench, enemies):
        living_allies = active + bench if policy['allied_survival_scope'] == 'ALL_ACTIVE_AND_BENCHED_POKEMON' else active
        facts = {'NO_LIVING_ALLIES': living_allies == 0, 'NO_LIVING_ENEMIES': enemies == 0}
        return next((rule['result'] for rule in policy['result_priority'] if facts[rule['when']]), policy['otherwise'])

    def c04_can_fire(reached_phase_end, coop, owner_alive, fainted_ally, uses):
        return (reached_phase_end or phase_end['synthetic_on_early_end']) and coop and owner_alive and fainted_ally and (
            uses < rescue['activation_limit']['max_activations']
        )

    return {
        'battle_result_after_action_and_phase_effects': sequence == [
            'RESOLVE_CURRENT_ACTION_AND_FAINTS', 'DISPATCH_ENEMY_PHASE_END_IF_REACHED', 'DECIDE_RESULT'
        ],
        'mutual_knockout_is_defeat': result(0, 0, 0) == 'DEFEAT',
        'surviving_bench_can_win_or_continue': result(0, 1, 0) == 'VICTORY' and result(0, 1, 1) == 'CONTINUE',
        'living_allies_and_enemies_continue': result(1, 0, 1) == 'CONTINUE' and result(0, 0, 1) == 'DEFEAT',
        'c04_only_at_real_enemy_phase_end': phase_end['event'] == rescue['event'] == 'ENEMY_PHASE_END'
        and phase_end['rescue_relic'] == 'C04' and not c04_can_fire(False, True, True, True, 0),
        'c04_revival_conditions_unchanged': c04_can_fire(True, True, True, True, 0)
        and not any(c04_can_fire(True, *case) for case in [(False, True, True, 0), (True, False, True, 0),
                                                        (True, True, False, 0), (True, True, True, 1)])
        and rescue['actions'][0]['op'] == 'REVIVE_ONE_ALLY_HP_FRACTION' and rescue['actions'][0]['value'] == 0.2,
    }


def check_party_edges(rules):
    from itertools import combinations
    from math import floor

    party, switch, status, victory, rest = (rules[key] for key in (
        'pokemon_party', 'switch', 'status', 'victory', 'rest_heal'
    ))
    bench = status['bench_effects']

    def allowed_types(*members):
        return all(not (set(a) & set(b)) for a, b in combinations(members, 2))

    def revive(max_hp):
        hp = victory['fainted_restore_fraction'] * max_hp
        return max(victory['fainted_restore_min_hp'], floor(hp))

    def rest_hp(current, maximum):
        if current == 0 and not rest['revives_fainted']:
            return 0
        healed = current + maximum * rest['base_max_hp_fraction']
        return min(healed, maximum) if rest['cap_at_max_hp'] else healed

    return {
        'starter_types_pairwise_disjoint': party['starter']['partner_type_rule'] == 'PAIRWISE_DISJOINT_ALL_TYPES'
        and allowed_types(('FIRE', 'FLYING'), ('WATER',), ('GRASS', 'POISON'))
        and not allowed_types(('FIRE', 'FLYING'), ('WATER', 'FLYING'), ('GRASS',))
        and not allowed_types(('FIRE',), ('WATER', 'GROUND'), ('GRASS', 'GROUND')),
        'victory_revive_rounds_down_with_minimum_one': victory['fainted_restore_rounding'] == 'FLOOR'
        and victory['fainted_restore_min_hp'] == 1 and [revive(hp) for hp in (1, 4, 19, 100)] == [1, 1, 3, 20],
        'status_does_not_block_switch': switch['normal']['allowed_while_afflicted'] == ['SLEEP', 'PARALYSIS']
        and switch['normal']['post_switch_move'] == 'ALLOW_WITH_REMAINING_ENERGY_AND_NORMAL_CHECKS',
        'bench_effect_scopes': bench['scope'] == 'IN_BATTLE'
        and bench['preserve_without_periodic_damage'] == ['POISON', 'BURN']
        and bench['clear_from_switching_out_target'] == ['LEECH_SEED', 'TRAPPING']
        and bench['preserve_battle_wide_effects']
        and bench['pokemon_targeted_relics_and_held_items'] == 'EXPLICIT_BENCH_TARGET_ONLY',
        'mega_form_reverts_only_at_battle_end': party['mega_form'] == {
            'preserve_on': ['SWITCH_OUT', 'REENTRY'], 'revert_on': 'BATTLE_END'
        },
        'rest_recovers_all_living_and_caps_hp': rest['recipient_scope'] == 'ALL_LIVING_POKEMON_OF_RESTING_PLAYER'
        and rest['respect_existing_relic_modifiers_and_restrictions']
        and [rest_hp(hp, max_hp) for hp, max_hp in [(40, 100), (180, 200), (0, 100)]] == [70, 200, 0],
        'six_open_combat_rules_resolved': not rules['pending'],
    }
