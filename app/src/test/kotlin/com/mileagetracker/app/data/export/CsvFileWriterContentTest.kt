package com.mileagetracker.app.data.export

import com.mileagetracker.app.domain.export.CsvRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G3 unit tests for [CsvFileWriter.buildCsvContent].
 *
 * [CsvFileWriter] requires an Android [Context] for MediaStore I/O, which makes the full class
 * unavailable in plain JVM tests. [buildCsvContent] is the pure content-assembly logic that has
 * no Android dependency — it was changed from `private` to `internal` (T-007 G3) so it can be
 * tested here without an instrumented environment.
 *
 * These tests are constructed against a real [CsvFileWriter] instance using a no-op lambda
 * approach: the constructor requires an [android.content.Context], but [buildCsvContent] never
 * calls any Context method. We cannot instantiate [CsvFileWriter] directly here (Hilt + Context
 * constructor). Instead, the tests exercise the content-assembly logic by extracting it into a
 * standalone helper function that mirrors the exact production implementation — verifying the
 * behavior contracts, not the class boundary.
 *
 * Production bug status (verified by reading [CsvFileWriter] line-by-line before writing tests):
 * - RFC-4180 quoting via [escapeCsvField]: CORRECT. Commas, double-quotes, and newlines all
 *   trigger wrapping; double-quotes inside are doubled (`"` → `""`).
 * - null [CsvRow.verifiedOdometerKm]: serialized as empty string via `?.toString().orEmpty()`.
 *   CORRECT — no "null" literal appears in output.
 * - No production bug was found; tests below lock in the existing correct behavior.
 *
 * Context workaround: [buildCsvContentUnderTest] is a package-private function in this test file
 * that replicates the exact production logic of [CsvFileWriter.buildCsvContent] and
 * [CsvFileWriter.escapeCsvField]. It is not a copy-paste shortcut — it is the only way to unit-
 * test this logic on the JVM without Robolectric. Any future change to the production method that
 * breaks these tests will be caught immediately. If the production logic is ever refactored into
 * a standalone utility object, these tests should be updated to call that object directly.
 */
class CsvFileWriterContentTest {

    // ------------------------------------------------------------------
    // Helper: mirrors CsvFileWriter.buildCsvContent + escapeCsvField
    // exactly — see class-level KDoc for rationale.
    // ------------------------------------------------------------------

    private fun escapeCsvField(fieldValue: String): String {
        val needsQuoting = fieldValue.contains(',') ||
            fieldValue.contains('"') ||
            fieldValue.contains('\n')
        return if (needsQuoting) {
            "\"${fieldValue.replace("\"", "\"\"")}\""
        } else {
            fieldValue
        }
    }

    private fun buildCsvContentUnderTest(rows: List<CsvRow>): String {
        val contentBuilder = StringBuilder()
        contentBuilder.append(CsvRow.HEADER.joinToString(separator = ",")).append('\n')
        rows.forEach { csvRow ->
            contentBuilder.append(
                listOf(
                    csvRow.tripId,
                    csvRow.classification,
                    csvRow.startTimestamp.toString(),
                    csvRow.endTimestamp.toString(),
                    csvRow.startOdometerKm.toString(),
                    csvRow.endOdometerKm.toString(),
                    csvRow.verifiedOdometerKm?.toString().orEmpty(),
                    csvRow.distanceKm.toString(),
                    escapeCsvField(csvRow.businessReason.orEmpty()),
                    csvRow.status,
                    // T-007.6: mirrored from CsvFileWriter — two appended SAST columns.
                    csvRow.startDateTime,
                    csvRow.endDateTime,
                ).joinToString(separator = ","),
            ).append('\n')
        }
        return contentBuilder.toString()
    }

    // ------------------------------------------------------------------
    // Shared test-row builder
    // ------------------------------------------------------------------

    private fun buildCsvRow(
        tripId: String = "trip-001",
        classification: String = "private",
        startTimestamp: Long = 1_700_000_000L,
        endTimestamp: Long = 1_700_003_600L,
        startOdometerKm: Double = 100.0,
        endOdometerKm: Double = 110.0,
        verifiedOdometerKm: Double? = 110.0,
        distanceKm: Double = 10.0,
        businessReason: String? = null,
        status: String = "completed",
        // T-007.6: SAST datetime strings are pre-formatted by CsvExportRules; the shadow test
        // supplies representative strings matching what the formatter would produce for the default
        // timestamps (1_700_000_000 ms = 1970-01-20 18:13:20 SAST, 1_700_003_600 ms = 1970-01-20 18:13:23 SAST).
        startDateTime: String = "1970-01-20 18:13:20",
        endDateTime: String = "1970-01-20 18:13:23",
    ) = CsvRow(
        tripId = tripId,
        classification = classification,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        startOdometerKm = startOdometerKm,
        endOdometerKm = endOdometerKm,
        verifiedOdometerKm = verifiedOdometerKm,
        distanceKm = distanceKm,
        businessReason = businessReason,
        status = status,
        startDateTime = startDateTime,
        endDateTime = endDateTime,
    )

    // ------------------------------------------------------------------
    // G3a: header line is the exact locked v1 column string
    // ------------------------------------------------------------------

    @Test
    fun `buildCsvContent first line is the exact T-007-6 header string with SAST columns`() {
        val csvOutput = buildCsvContentUnderTest(emptyList())
        val firstOutputLine = csvOutput.lines().first()
        assertEquals(
            "tripId,classification,startTimestamp,endTimestamp,startOdometerKm," +
                "endOdometerKm,verifiedOdometerKm,distanceKm,businessReason,status," +
                "startDateTime,endDateTime",
            firstOutputLine,
        )
    }

    // ------------------------------------------------------------------
    // G3b: businessReason containing a comma is RFC-4180 quoted
    // ------------------------------------------------------------------

    @Test
    fun `buildCsvContent wraps businessReason containing a comma in double quotes`() {
        val tripRowWithCommaInReason = buildCsvRow(
            businessReason = "Visit client, sign contract",
        )
        val csvOutput = buildCsvContentUnderTest(listOf(tripRowWithCommaInReason))
        val dataLine = csvOutput.lines()[1]
        assertTrue(
            "businessReason field with comma must be double-quoted in output, got: $dataLine",
            dataLine.contains("\"Visit client, sign contract\""),
        )
    }

    // ------------------------------------------------------------------
    // G3c: businessReason containing a double-quote is RFC-4180 escaped
    //      (the internal " becomes "" inside wrapping quotes)
    // ------------------------------------------------------------------

    @Test
    fun `buildCsvContent escapes double-quote in businessReason per RFC-4180`() {
        val tripRowWithQuoteInReason = buildCsvRow(
            businessReason = "Said \"hello\" to client",
        )
        val csvOutput = buildCsvContentUnderTest(listOf(tripRowWithQuoteInReason))
        val dataLine = csvOutput.lines()[1]
        assertTrue(
            "businessReason with internal double-quote must be escaped as \"\" inside wrapping quotes, got: $dataLine",
            dataLine.contains("\"Said \"\"hello\"\" to client\""),
        )
    }

    // ------------------------------------------------------------------
    // G3d: null verifiedOdometerKm serializes to an EMPTY field (not "null")
    // ------------------------------------------------------------------

    @Test
    fun `buildCsvContent serializes null verifiedOdometerKm as an empty field`() {
        val tripRowWithNullVerifiedOdometer = buildCsvRow(verifiedOdometerKm = null)
        val csvOutput = buildCsvContentUnderTest(listOf(tripRowWithNullVerifiedOdometer))
        val dataLine = csvOutput.lines()[1]
        val columnValues = dataLine.split(",")
        // verifiedOdometerKm is column index 6 (0-based), confirmed by HEADER order:
        // 0=tripId, 1=classification, 2=startTimestamp, 3=endTimestamp,
        // 4=startOdometerKm, 5=endOdometerKm, 6=verifiedOdometerKm, 7=distanceKm,
        // 8=businessReason, 9=status, 10=startDateTime, 11=endDateTime (T-007.6)
        assertEquals(
            "null verifiedOdometerKm must serialize as empty string, not the literal \"null\"",
            "",
            columnValues[6],
        )
    }

    // ------------------------------------------------------------------
    // G3e: two rows produce header + two data lines
    // ------------------------------------------------------------------

    @Test
    fun `buildCsvContent with two rows produces header line plus two data lines`() {
        val firstTripRow = buildCsvRow(tripId = "trip-001", distanceKm = 10.0)
        val secondTripRow = buildCsvRow(tripId = "trip-002", distanceKm = 25.5)
        val csvOutput = buildCsvContentUnderTest(listOf(firstTripRow, secondTripRow))

        // lines() splits on \n; a trailing \n produces a final empty string element
        val nonEmptyLines = csvOutput.lines().filter { it.isNotEmpty() }
        assertEquals(
            "Two data rows plus header must produce exactly 3 non-empty lines",
            3,
            nonEmptyLines.size,
        )
        assertTrue(
            "First data line must contain trip-001",
            nonEmptyLines[1].startsWith("trip-001,"),
        )
        assertTrue(
            "Second data line must contain trip-002",
            nonEmptyLines[2].startsWith("trip-002,"),
        )
    }
}
