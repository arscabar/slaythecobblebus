package dev.cobblespire
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class EvolutionAccessTest {
 @Test fun `each badge or event instance grants at most one evolution credit`(){val a=EvolutionAccess();assertTrue(a.badge("boulder"));assertFalse(a.badge("boulder"));assertTrue(a.event("node-4"));assertFalse(a.event("node-4"));assertEquals(2,a.available().size)}
 @Test fun `without badge or event no evolution can be requested`(){val a=EvolutionAccess();assertThrows(IllegalArgumentException::class.java){a.evolve(1,"charmeleon",setOf("charmeleon")){} };assertTrue(a.available().isEmpty())}
 @Test fun `only native legal evolution targets are accepted and failure does not spend credit`(){val a=EvolutionAccess();a.event("fire-event",setOf("charmeleon"));assertThrows(IllegalArgumentException::class.java){a.evolve(1,"charizard",setOf("charmeleon")){} };assertEquals(1,a.available().size);assertThrows(IllegalStateException::class.java){a.evolve(1,"charmeleon",setOf("charmeleon")){error("fail")} };assertEquals(1,a.available().size);a.evolve(1,"charmeleon",setOf("charmeleon")){};assertTrue(a.available().isEmpty())}
}
