package com.openlauncher.app.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class LogCsvTest {
    @Test
    fun escapesTextAndNeutralizesSpreadsheetFormulas() {
        val output = StringWriter()
        LogCsv(output).vehicle(listOf(
            VehicleLogEntry(VehicleLogType.SERVICE, "Oil, \"filter\"", "10.50", 0, 30),
            VehicleLogEntry(VehicleLogType.FUEL, " =1+1", "4", 0)
        ))
        assertTrue(output.toString().contains(
            "\"1970-01-01T00:00:00Z\",\"SERVICE\",\"Oil, \"\"filter\"\"\",\"10.50\",\"30\"\r\n"
        ))
        assertTrue(output.toString().contains("\"' =1+1\""))
    }

    @Test
    fun writesTripsWithStableUnitsAndUtcTimestamps() {
        val output = StringWriter()
        LogCsv(output).trips(listOf(TripEntry(1500.5, 80, 20, 12.25, 0)))
        assertTrue(output.toString().endsWith("\"1970-01-01T00:00:00Z\",\"1500.5\",\"80\",\"20\",\"12.25\"\r\n"))
    }
}
