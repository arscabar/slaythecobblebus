# CobbleSpire v4.1 Predefined Data Bundle

This folder contains **predeclared design-time JSON** for the CobbleSpire Cobbleverse add-on. The data is **not an implemented game engine**.

- `all_definitions_v4.json`: complete combined definition snapshot.
- `relics_165_runtime.json`: all 165 relic-like Adventure Tools, 182 event/condition/action rules.
- `registry_events_conditions_actions.json`: controlled enum token registries.
- `enemy_actor_rules_25.json`: enemy Pokemon/3-5 move lists/priority AI profiles.
- `encounter_21.json`: villain-themed Act 1 Rocket / Act 2 Magma or Aqua / Act 3 Galactic encounters.
- `boss_phase_profiles_6.json`: 14 predeclared phases across 6 boss encounter variants.
- `characters_50_v3_preserved.json`: original 50-character metadata preserved, start decks still templates.
- `balance_global_rules.json`: progression, multiplayer, type matchup and run rules.
- `combat_and_run_rules_v4_1.json`: source for the v4.1 combat and memory-only run policies, mirrored in both global rule snapshots. The `pending` section for this ruleset is empty after settling the starter, switching, bench effects, Mega form, revival HP and rest rules. At rest, the resting player’s living Pokémon each recover 30% of their own maximum HP by default.
- `qa_validation.json`: static schema/referential check report.

Game intent: 3-act Slay-the-Spire-style **UI map** (no overworld route), up to 3 Pokemon per player and 1 active each, 1-3 enemies on screen, enemy attacks aim at the living active Pokemon of each player, one incoming move resolved independently for accuracy/damage/status per recipient, type immunity of player-to-enemy direct attacks eased to x0.5. No mandatory stellar Terastal visuals.

All original species moves, form identifiers and learnsets remain **unverified against the target modpack**. Do not treat these definitions as installed executable content.
