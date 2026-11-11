package com.example.lector.ext

import com.example.lector.ui.actualizaciones.proximaRevision
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProximaRevisionTest {
    private val zona = ZoneId.of("UTC")
    private fun dia(haceDias: Long) = LocalDate.now(zona).minusDays(haceDias).atStartOfDay(zona).toInstant().toEpochMilli() + 3_600_000
    private fun enDias(ms: Long) = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(zona), java.time.Instant.ofEpochMilli(ms).atZone(zona).toLocalDate())

    @Test
    fun `semanal con capitulo hace 3 dias espera 4 dias mas`() {
        val subidas = listOf(3L, 10, 17, 24, 31).map { dia(it) }
        assertEquals(4, enDias(proximaRevision(subidas, emptyList(), zona)))
    }

    @Test
    fun `sin fechas no hay proxima`() {
        assertEquals(0L, proximaRevision(listOf(0L, 0L), emptyList(), zona))
    }

    @Test
    fun `parado desde hace un ano se revisa cada vez menos pero nunca queda en el pasado`() {
        val subidas = listOf(365L, 372, 379, 386).map { dia(it) }
        val en = enDias(proximaRevision(subidas, emptyList(), zona))
        assert(en in 0..28) { "en $en días" }
    }
}
