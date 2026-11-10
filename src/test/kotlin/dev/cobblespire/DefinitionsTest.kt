package dev.cobblespire
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class DefinitionsTest {
    @Test fun `all original definitions and references are retained`(){Definitions.validate();assertEquals(50,Definitions.characters.size);assertEquals(165,Definitions.relics.size);assertEquals(25,Definitions.enemies.size)}
    @Test fun `latest rules explicitly separate switch and deck reroll`(){val rules=Definitions.data.getAsJsonObject("global_rules");assertEquals(3,rules.getAsJsonObject("pokemon_party").get("max_per_player").asInt);assertEquals(1,rules.getAsJsonObject("switch").getAsJsonObject("normal").get("energy_cost").asInt);assertFalse(rules.getAsJsonObject("run_state").get("save_enabled").asBoolean)}
}
