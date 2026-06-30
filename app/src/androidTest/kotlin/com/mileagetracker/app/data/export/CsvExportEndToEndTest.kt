package com.mileagetracker.app.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mileagetracker.app.data.local.MileageTrackerDatabase
import com.mileagetracker.app.data.local.TripEntity
import com.mileagetracker.app.data.repository.TripRepositoryImpl
import com.mileagetracker.app.data.signing.TripSigner
import com.mileagetracker.app.domain.export.CsvExportRules
import com.mileagetracker.app.domain.export.IntegritySidecarGenerator
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end instrumented test for the CSV export pipeline.
 *
 * Pipeline (all real production code, no re-implementation):
 *   Real in-memory Room DB (full schema + TypeConverters + Migration 1->2)
 *     -> real TripDao.getCompletedTripsForExport()
 *     -> real TripRepositoryImpl.getCompletedTripsForExport() (entity-to-domain mapping)
 *     -> real CsvExportRules.buildExportRows() (status filter + business-reason gate)
 *     -> real CsvFileWriter.buildCsvContent() (CSV byte assembly)
 *     -> write those bytes to a real file on disk (context.filesDir)
 *     -> read the file back from disk
 *     -> assert the disk-read content
 *
 * The test also calls the real CsvFileWriter.writeToDownloads() and asserts
 * CsvWriteResult.Success, which proves the MediaStore export path does not crash.
 * Content verification uses the disk round-trip, not a MediaStore read-back.
 *
 * Fixtures cover the full export contract:
 *   - COMPLETED PRIVATE: must appear.
 *   - COMPLETED WORK with non-blank reason: must appear.
 *   - COMPLETED WORK with blank reason: excluded by CsvExportRules defensive gate.
 *   - ACTIVE: excluded by TripDao WHERE status = 'completed'.
 *   - COMPLETED WORK with comma in business reason: must appear, RFC-4180 quoted.
 */
@RunWith(AndroidJUnit4::class)
class CsvExportEndToEndTest {

    private lateinit var inMemoryDatabase: MileageTrackerDatabase
    private lateinit var tripRepositoryImpl: TripRepositoryImpl
    private lateinit var csvFileWriter: CsvFileWriter
    private lateinit var instrumentationContext: Context

    // Deterministic timestamps so column-value assertions are stable across runs.
    private val PRIVATE_TRIP_START_TIMESTAMP = 1_700_000_000_000L
    private val PRIVATE_TRIP_END_TIMESTAMP = 1_700_003_600_000L
    private val WORK_TRIP_START_TIMESTAMP = 1_700_010_000_000L
    private val WORK_TRIP_END_TIMESTAMP = 1_700_013_600_000L
    private val WORK_WITH_COMMA_REASON_START_TIMESTAMP = 1_700_020_000_000L
    private val WORK_WITH_COMMA_REASON_END_TIMESTAMP = 1_700_023_600_000L

    @Before
    fun setUp() {
        instrumentationContext = ApplicationProvider.getApplicationContext()

        // inMemoryDatabaseBuilder uses the real TypeConverters (Converters.kt) and
        // applies Migration 1->2 (adds trip_sequence_number), matching production exactly.
        // allowMainThreadQueries() is fine in instrumented tests (not the UI main thread).
        inMemoryDatabase = Room.inMemoryDatabaseBuilder(
            instrumentationContext,
            MileageTrackerDatabase::class.java,
        )
            .addMigrations(MileageTrackerDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        tripRepositoryImpl = TripRepositoryImpl(inMemoryDatabase.tripDao())
        val tripSigner = TripSigner()
        csvFileWriter = CsvFileWriter(
            appContext = instrumentationContext,
            tripSigner = tripSigner,
            integritySidecarGenerator = IntegritySidecarGenerator(tripSigner),
        )
    }

    @After
    fun tearDown() {
        inMemoryDatabase.close()
        // Clean up any CSV files written to filesDir during the test run.
        instrumentationContext.filesDir
            .listFiles { diskFile -> diskFile.name.endsWith(".csv") }
            ?.forEach { csvFile -> csvFile.delete() }
    }

    // -------------------------------------------------------------------------
    // E2E: full pipeline through to a real disk file read back for assertions
    // -------------------------------------------------------------------------

    @Test
    fun fullExportPipeline_producesCorrectCsvRowsForCompletedTripsOnly() = runTest {
        // --- Arrange: insert five TripEntity rows via the real DAO ---

        // Row 1: completed PRIVATE trip — must appear in export.
        val completedPrivateTripEntity = buildTripEntity(
            tripId = "trip-private-001",
            classification = TripClassification.PRIVATE,
            status = TripStatus.COMPLETED,
            businessReason = null,
            startTimestamp = PRIVATE_TRIP_START_TIMESTAMP,
            endTimestamp = PRIVATE_TRIP_END_TIMESTAMP,
            startOdometerKm = 100.0,
            endOdometerKm = 110.0,
            verifiedOdometerKm = 110.5,
            distanceKm = 10.0,
        )

        // Row 2: completed WORK trip with a valid business reason — must appear in export.
        val completedWorkTripWithReasonEntity = buildTripEntity(
            tripId = "trip-work-002",
            classification = TripClassification.WORK,
            status = TripStatus.COMPLETED,
            businessReason = "Client site visit",
            startTimestamp = WORK_TRIP_START_TIMESTAMP,
            endTimestamp = WORK_TRIP_END_TIMESTAMP,
            startOdometerKm = 200.0,
            endOdometerKm = 225.0,
            verifiedOdometerKm = null,
            distanceKm = 25.0,
        )

        // Row 3: completed WORK trip with a blank business reason — must be EXCLUDED.
        // CsvExportRules.buildExportRows applies a defensive gate: blank reason on a
        // WORK trip must not appear in the compliance logbook even if status is COMPLETED.
        val completedWorkTripWithBlankReasonEntity = buildTripEntity(
            tripId = "trip-work-blank-003",
            classification = TripClassification.WORK,
            status = TripStatus.COMPLETED,
            businessReason = "   ",
            startTimestamp = 1_700_030_000_000L,
            endTimestamp = 1_700_033_600_000L,
            startOdometerKm = 300.0,
            endOdometerKm = 320.0,
            verifiedOdometerKm = null,
            distanceKm = 20.0,
        )

        // Row 4: ACTIVE trip — must be EXCLUDED by TripDao WHERE status = 'completed'.
        val activeTripEntity = buildTripEntity(
            tripId = "trip-active-004",
            classification = TripClassification.PRIVATE,
            status = TripStatus.ACTIVE,
            businessReason = null,
            startTimestamp = 1_700_040_000_000L,
            endTimestamp = 1_700_040_000_000L, // sentinel: equals startTimestamp while ACTIVE
            startOdometerKm = 400.0,
            endOdometerKm = 400.0,
            verifiedOdometerKm = null,
            distanceKm = 0.0,
        )

        // Row 5: completed WORK trip with a comma in the business reason — must appear,
        // with the businessReason field RFC-4180 double-quoted.
        val completedWorkTripWithCommaReasonEntity = buildTripEntity(
            tripId = "trip-work-comma-005",
            classification = TripClassification.WORK,
            status = TripStatus.COMPLETED,
            businessReason = "Visit client, sign contract",
            startTimestamp = WORK_WITH_COMMA_REASON_START_TIMESTAMP,
            endTimestamp = WORK_WITH_COMMA_REASON_END_TIMESTAMP,
            startOdometerKm = 500.0,
            endOdometerKm = 545.0,
            verifiedOdometerKm = null,
            distanceKm = 45.0,
        )

        // Insert all five via the real DAO.
        val tripDao = inMemoryDatabase.tripDao()
        tripDao.insertTrip(completedPrivateTripEntity)
        tripDao.insertTrip(completedWorkTripWithReasonEntity)
        tripDao.insertTrip(completedWorkTripWithBlankReasonEntity)
        tripDao.insertTrip(activeTripEntity)
        tripDao.insertTrip(completedWorkTripWithCommaReasonEntity)

        // --- Act: run the real production export path ---

        // Step 1: real repository query — maps TripEntity -> Trip domain model;
        // TripDao WHERE status='completed' excludes the ACTIVE trip here.
        val completedDomainTrips = tripRepositoryImpl.getCompletedTripsForExport()

        // Step 2: real CsvExportRules — applies the status filter (belt-and-suspenders)
        // and the defensive blank-business-reason gate. Blank-reason WORK excluded here.
        val exportRows = CsvExportRules.buildExportRows(completedDomainTrips)

        // Step 3: real CsvFileWriter.buildCsvContent() assembles the CSV string.
        // buildCsvContent() is `internal` — callable from the same Gradle module (app).
        // This is the production method, not a re-implementation.
        val csvContentFromProduction = csvFileWriter.buildCsvContent(exportRows)

        // Step 4: write the CSV bytes to a real file on disk, then read them back.
        // Using filesDir (app-private internal storage) — no permissions needed, always
        // writable from an instrumented test, deterministic path, easy cleanup in @After.
        val outputCsvFile = File(instrumentationContext.filesDir, "export_e2e_test.csv")
        outputCsvFile.writeBytes(csvContentFromProduction.toByteArray(Charsets.UTF_8))

        val diskReadContent = outputCsvFile.readText(Charsets.UTF_8)

        // Step 5: real CsvFileWriter.writeExport() — asserts the MediaStore path
        // does not crash and returns the correct filename pattern. The emulator/device under
        // test has no signing key provisioned, so the integrity sidecar is expected to fall
        // back with a warning here — the CSV write itself must still succeed.
        val writeExportResult = csvFileWriter.writeExport(completedDomainTrips, exportRows)

        // --- Assert: byte-level content from the disk-read file ---

        val diskReadLines = diskReadContent.lines().filter { csvLine -> csvLine.isNotEmpty() }

        // Assertion 1: disk-read content is byte-for-byte identical to the produced string
        // (UTF-8 encode -> write to disk -> read from disk -> UTF-8 decode = identity).
        assertEquals(
            "Disk-read content must be identical to the CSV string that was written",
            csvContentFromProduction,
            diskReadContent,
        )

        // Assertion 2: header line is exactly the T-007.6 amended column string (12 columns).
        assertEquals(
            "First line of disk-read CSV must be the exact T-007.6 header with SAST columns",
            "tripId,classification,startTimestamp,endTimestamp,startOdometerKm," +
                "endOdometerKm,verifiedOdometerKm,distanceKm,businessReason,status," +
                "startDateTime,endDateTime",
            diskReadLines[0],
        )

        // Assertion 3: exactly 3 data rows (private + work-with-reason + work-with-comma-reason);
        // blank-reason WORK and ACTIVE must be excluded.
        assertEquals(
            "Disk-read CSV must have exactly 3 data rows (1 private + 2 work). " +
                "Blank-reason WORK and ACTIVE must be excluded. " +
                "Got ${diskReadLines.size - 1} data rows.",
            4, // 1 header + 3 data rows
            diskReadLines.size,
        )

        // Assertion 4: excluded trip IDs must not appear anywhere in the disk-read file.
        assertFalse(
            "Blank-reason WORK trip (trip-work-blank-003) must not appear in disk-read CSV",
            diskReadContent.contains("trip-work-blank-003"),
        )
        assertFalse(
            "ACTIVE trip (trip-active-004) must not appear in disk-read CSV",
            diskReadContent.contains("trip-active-004"),
        )

        // Assertion 5: identify each expected data line by its tripId prefix.
        val dataLines = diskReadLines.drop(1)
        val privateTripLine = dataLines.first { csvLine -> csvLine.startsWith("trip-private-001,") }
        val workTripLine = dataLines.first { csvLine -> csvLine.startsWith("trip-work-002,") }
        val commaReasonTripLine = dataLines.first { csvLine -> csvLine.startsWith("trip-work-comma-005,") }

        // Assertion 6: private trip — correct fixed-column values.
        // Comma split is safe here because the private trip's businessReason is null (empty field).
        val privateTripColumns = privateTripLine.split(",")
        assertEquals("Private trip column[0] tripId", "trip-private-001", privateTripColumns[0])
        assertEquals("Private trip column[1] classification", "private", privateTripColumns[1])
        assertEquals(
            "Private trip column[2] startTimestamp",
            PRIVATE_TRIP_START_TIMESTAMP.toString(),
            privateTripColumns[2],
        )
        assertEquals(
            "Private trip column[3] endTimestamp",
            PRIVATE_TRIP_END_TIMESTAMP.toString(),
            privateTripColumns[3],
        )
        assertEquals("Private trip column[4] startOdometerKm", "100.0", privateTripColumns[4])
        assertEquals("Private trip column[5] endOdometerKm", "110.0", privateTripColumns[5])
        assertEquals("Private trip column[6] verifiedOdometerKm", "110.5", privateTripColumns[6])
        assertEquals("Private trip column[7] distanceKm", "10.0", privateTripColumns[7])
        assertEquals("Private trip column[8] businessReason (empty)", "", privateTripColumns[8])
        assertEquals("Private trip column[9] status", "completed", privateTripColumns[9])
        // T-007.6: SAST datetime columns — indices 10 and 11.
        // PRIVATE_TRIP_START_TIMESTAMP = 1_700_000_000_000L -> 2023-11-15 00:13:20 SAST (UTC+2)
        // PRIVATE_TRIP_END_TIMESTAMP   = 1_700_003_600_000L -> 2023-11-15 01:13:20 SAST (UTC+2)
        assertEquals(
            "Private trip column[10] startDateTime must be 2023-11-15 00:13:20 SAST",
            "2023-11-15 00:13:20",
            privateTripColumns[10],
        )
        assertEquals(
            "Private trip column[11] endDateTime must be 2023-11-15 01:13:20 SAST",
            "2023-11-15 01:13:20",
            privateTripColumns[11],
        )

        // Assertion 7: null verifiedOdometerKm must serialize to an EMPTY field, not "null".
        val workTripColumns = workTripLine.split(",")
        assertEquals(
            "Work trip column[6] verifiedOdometerKm must be empty string when null",
            "",
            workTripColumns[6],
        )
        assertEquals(
            "Work trip column[8] businessReason",
            "Client site visit",
            workTripColumns[8],
        )
        assertEquals("Work trip column[9] status", "completed", workTripColumns[9])
        // T-007.6: SAST columns for the work trip.
        // WORK_TRIP_START_TIMESTAMP = 1_700_010_000_000L -> 2023-11-15 03:00:00 SAST
        // WORK_TRIP_END_TIMESTAMP   = 1_700_013_600_000L -> 2023-11-15 04:00:00 SAST
        assertEquals(
            "Work trip column[10] startDateTime must be 2023-11-15 03:00:00 SAST",
            "2023-11-15 03:00:00",
            workTripColumns[10],
        )
        assertEquals(
            "Work trip column[11] endDateTime must be 2023-11-15 04:00:00 SAST",
            "2023-11-15 04:00:00",
            workTripColumns[11],
        )

        // Assertion 8: businessReason containing a comma must be RFC-4180 double-quoted.
        assertTrue(
            "businessReason with a comma must be wrapped in double-quotes per RFC-4180. " +
                "Full line was: $commaReasonTripLine",
            commaReasonTripLine.contains("\"Visit client, sign contract\""),
        )

        // Assertion 9: writeExport() (MediaStore path) returns Success with the
        // correct filename pattern — proves it does not crash on this emulator/device.
        assertTrue(
            "writeExport() must return ExportWriteResult.Success; got: $writeExportResult",
            writeExportResult is ExportWriteResult.Success,
        )
        val successWriteResult = writeExportResult as ExportWriteResult.Success
        assertTrue(
            "writeExport() csvFilename must match mileage_trips_YYYYMMDD_HHMMSS.csv",
            successWriteResult.csvFilename.matches(Regex("mileage_trips_\\d{8}_\\d{6}\\.csv")),
        )
    }

    // -------------------------------------------------------------------------
    // Edge-case: no qualifying trips -> header-only CSV on disk
    // -------------------------------------------------------------------------

    @Test
    fun exportWithNoQualifyingTrips_producesHeaderOnlyCsvOnDisk() = runTest {
        val tripDao = inMemoryDatabase.tripDao()
        tripDao.insertTrip(
            buildTripEntity(
                tripId = "trip-active-only",
                classification = TripClassification.PRIVATE,
                status = TripStatus.ACTIVE,
                businessReason = null,
                startTimestamp = 1_700_050_000_000L,
                endTimestamp = 1_700_050_000_000L,
                startOdometerKm = 0.0,
                endOdometerKm = 0.0,
                verifiedOdometerKm = null,
                distanceKm = 0.0,
            ),
        )
        tripDao.insertTrip(
            buildTripEntity(
                tripId = "trip-work-blank-only",
                classification = TripClassification.WORK,
                status = TripStatus.COMPLETED,
                businessReason = "",
                startTimestamp = 1_700_060_000_000L,
                endTimestamp = 1_700_063_600_000L,
                startOdometerKm = 0.0,
                endOdometerKm = 10.0,
                verifiedOdometerKm = null,
                distanceKm = 10.0,
            ),
        )

        val completedDomainTrips = tripRepositoryImpl.getCompletedTripsForExport()
        val exportRows = CsvExportRules.buildExportRows(completedDomainTrips)
        val csvContentFromProduction = csvFileWriter.buildCsvContent(exportRows)

        val outputCsvFile = File(instrumentationContext.filesDir, "export_empty_e2e_test.csv")
        outputCsvFile.writeBytes(csvContentFromProduction.toByteArray(Charsets.UTF_8))
        val diskReadContent = outputCsvFile.readText(Charsets.UTF_8)

        val nonEmptyDiskLines = diskReadContent.lines().filter { csvLine -> csvLine.isNotEmpty() }
        assertEquals(
            "Disk-read CSV must contain exactly 1 line (header only) when no trips qualify",
            1,
            nonEmptyDiskLines.size,
        )
        assertEquals(
            "Disk-read header-only CSV must have the correct T-007.6 header with SAST columns",
            "tripId,classification,startTimestamp,endTimestamp,startOdometerKm," +
                "endOdometerKm,verifiedOdometerKm,distanceKm,businessReason,status," +
                "startDateTime,endDateTime",
            nonEmptyDiskLines[0],
        )
        assertTrue(
            "exportRows must be empty when no trips qualify; got ${exportRows.size} rows",
            exportRows.isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // Edge-case: PENDING_BUSINESS_REASON trip is excluded at the DAO level
    // -------------------------------------------------------------------------

    @Test
    fun pendingBusinessReasonTrip_isExcludedFromExport() = runTest {
        val tripDao = inMemoryDatabase.tripDao()
        tripDao.insertTrip(
            buildTripEntity(
                tripId = "trip-pending-br-001",
                classification = TripClassification.WORK,
                status = TripStatus.PENDING_BUSINESS_REASON,
                businessReason = null,
                startTimestamp = 1_700_070_000_000L,
                endTimestamp = 1_700_073_600_000L,
                startOdometerKm = 600.0,
                endOdometerKm = 620.0,
                verifiedOdometerKm = null,
                distanceKm = 20.0,
            ),
        )

        // TripDao.getCompletedTripsForExport() queries WHERE status='completed';
        // PENDING_BUSINESS_REASON is excluded at the DAO level before rules even run.
        val completedDomainTrips = tripRepositoryImpl.getCompletedTripsForExport()
        assertTrue(
            "PENDING_BUSINESS_REASON trip must be excluded by getCompletedTripsForExport DAO query",
            completedDomainTrips.isEmpty(),
        )

        val exportRows = CsvExportRules.buildExportRows(completedDomainTrips)
        assertTrue("exportRows must be empty when input trips list is empty", exportRows.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Shared entity builder
    // -------------------------------------------------------------------------

    private fun buildTripEntity(
        tripId: String,
        classification: TripClassification,
        status: TripStatus,
        businessReason: String?,
        startTimestamp: Long,
        endTimestamp: Long,
        startOdometerKm: Double,
        endOdometerKm: Double,
        verifiedOdometerKm: Double?,
        distanceKm: Double,
        startLatitude: Double? = null,
        startLongitude: Double? = null,
        endLatitude: Double? = null,
        endLongitude: Double? = null,
    ): TripEntity = TripEntity(
        id = tripId,
        classification = classification,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        startOdometerKm = startOdometerKm,
        endOdometerKm = endOdometerKm,
        verifiedOdometerKm = verifiedOdometerKm,
        distanceKm = distanceKm,
        businessReason = businessReason,
        startLatitude = startLatitude,
        startLongitude = startLongitude,
        endLatitude = endLatitude,
        endLongitude = endLongitude,
        status = status,
        photoRetention = PhotoRetentionMode.SAVED,
        createdAt = startTimestamp,
        updatedAt = startTimestamp,
        signatureBase64 = null,
        signingKeyId = null,
        tripSequenceNumber = 0,
        isManualStart = false,
    )
}
