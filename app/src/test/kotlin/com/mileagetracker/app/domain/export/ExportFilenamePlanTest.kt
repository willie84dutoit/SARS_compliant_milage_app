package com.mileagetracker.app.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * T-032 Half A / Pass 2 unit tests for [ExportFilenamePlan]. Pure JVM, no Android dependency —
 * verifies the shared-stem pairing contract: CSV, integrity sidecar, and verify document
 * filenames are all derived from one [ExportFilenamePlan.fromTimestampStem] call so a caller
 * cannot accidentally compute the stem more than once per export (the bug this class exists to
 * prevent — a clock tick between three separate `SimpleDateFormat.format(Date())` calls would
 * previously have produced mismatched stems across the three files).
 */
class ExportFilenamePlanTest {

    @Test
    fun `fromTimestampStem builds csvFilename with the mileage_trips prefix and csv extension`() {
        val plan = ExportFilenamePlan.fromTimestampStem("20260630_142205")
        assertEquals("mileage_trips_20260630_142205.csv", plan.csvFilename)
    }

    @Test
    fun `fromTimestampStem builds sidecarJsonFilename with the integrity json suffix`() {
        val plan = ExportFilenamePlan.fromTimestampStem("20260630_142205")
        assertEquals("mileage_trips_20260630_142205.integrity.json", plan.sidecarJsonFilename)
    }

    @Test
    fun `fromTimestampStem builds a static VERIFY md filename regardless of stem`() {
        val planA = ExportFilenamePlan.fromTimestampStem("20260630_142205")
        val planB = ExportFilenamePlan.fromTimestampStem("20990101_000000")
        assertEquals("VERIFY.md", planA.verifyMarkdownFilename)
        assertEquals("VERIFY.md", planB.verifyMarkdownFilename)
    }

    @Test
    fun `csvFilename and sidecarJsonFilename share the identical stem prefix`() {
        val plan = ExportFilenamePlan.fromTimestampStem("20260630_142205")
        val csvStem = plan.csvFilename.removeSuffix(".csv")
        val sidecarStem = plan.sidecarJsonFilename.removeSuffix(".integrity.json")
        assertEquals(
            "CSV and sidecar filenames must share the exact same stem",
            csvStem,
            sidecarStem,
        )
    }

    @Test
    fun `different timestamp stems produce different csv and sidecar filenames`() {
        val planA = ExportFilenamePlan.fromTimestampStem("20260630_142205")
        val planB = ExportFilenamePlan.fromTimestampStem("20260630_142206")
        assertNotEquals(planA.csvFilename, planB.csvFilename)
        assertNotEquals(planA.sidecarJsonFilename, planB.sidecarJsonFilename)
    }
}
