package com.mileagetracker.app.ui.export

import app.cash.turbine.test
import com.mileagetracker.app.data.export.CsvWriter
import com.mileagetracker.app.data.export.ExportWriteResult
import com.mileagetracker.app.domain.export.CsvRow
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.repository.FakeTripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T-007 G4 unit tests for [ExportViewModel].
 *
 * [ExportViewModel] depends on [CsvWriter] (abstraction) rather than the concrete [CsvFileWriter],
 * enabling tests to inject a [FakeCsvWriter] that records calls and returns preconfigured results
 * without touching Android platform I/O or the MediaStore.
 *
 * Dispatcher setup mirrors [com.mileagetracker.app.ui.home.HomeStatusViewModelTest] since the
 * ViewModel uses `viewModelScope`, which resolves `Dispatchers.Main` internally.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------
    // Shared trip builder (mirrors CsvExportRulesTest.buildTrip exactly)
    // ------------------------------------------------------------------

    private fun buildTrip(
        id: String = "trip-1",
        classification: TripClassification = TripClassification.PRIVATE,
        status: TripStatus = TripStatus.COMPLETED,
        businessReason: String? = null,
    ) = Trip(
        id = id,
        classification = classification,
        startTimestamp = 1_000L,
        endTimestamp = 2_000L,
        startOdometerKm = 100.0,
        endOdometerKm = 110.0,
        verifiedOdometerKm = 110.0,
        distanceKm = 10.0,
        businessReason = businessReason,
        startLatitude = null,
        startLongitude = null,
        endLatitude = null,
        endLongitude = null,
        status = status,
        photoRetention = PhotoRetentionMode.SAVED,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        signatureBase64 = null,
        signingKeyId = null,
        tripSequenceNumber = 0,
        isManualStart = false,
    )

    // ------------------------------------------------------------------
    // Fake CsvWriter implementation
    // ------------------------------------------------------------------

    private class FakeCsvWriter(
        private val resultToReturn: ExportWriteResult,
    ) : CsvWriter {
        var lastRowCountReceived: Int = -1
            private set
        var lastTripCountReceived: Int = -1
            private set

        override fun writeExport(trips: List<Trip>, rows: List<CsvRow>): ExportWriteResult {
            lastTripCountReceived = trips.size
            lastRowCountReceived = rows.size
            return resultToReturn
        }
    }

    // ------------------------------------------------------------------
    // Default success result helper (T-032 Half A / Pass 2 fields default to "sidecar written")
    // ------------------------------------------------------------------

    private fun buildDefaultSuccessResult(
        csvFilename: String = "mileage_trips_20240101_120000.csv",
    ) = ExportWriteResult.Success(
        csvFilename = csvFilename,
        sidecarWritten = true,
        sidecarJsonFilename = "mileage_trips_20240101_120000.integrity.json",
        verifyMarkdownFilename = "VERIFY.md",
        integrityWarning = null,
    )

    // ------------------------------------------------------------------
    // ViewModel builder helper
    // ------------------------------------------------------------------

    private fun buildViewModel(
        fakeTripRepository: FakeTripRepository = FakeTripRepository(),
        fakeCsvWriter: FakeCsvWriter = FakeCsvWriter(buildDefaultSuccessResult()),
    ): ExportViewModel = ExportViewModel(
        tripRepository = fakeTripRepository,
        csvWriter = fakeCsvWriter,
    )

    // ------------------------------------------------------------------
    // Test 1: success path — two COMPLETED private trips exported successfully
    // ------------------------------------------------------------------

    @Test
    fun `onExportRequested with two completed trips returns success with correct rowCount and filename`() =
        runTest {
            val fakeTripRepository = FakeTripRepository()
            fakeTripRepository.setTripHistory(
                listOf(
                    buildTrip(id = "trip-1"),
                    buildTrip(id = "trip-2"),
                ),
            )
            val fakeCsvWriter = FakeCsvWriter(buildDefaultSuccessResult())
            val viewModel = buildViewModel(fakeTripRepository, fakeCsvWriter)

            viewModel.uiState.test {
                // M-3: uiState is now a combine of exportProgressAndResult + observeTripHistory(),
                // so there may be a second emission after the initialValue that reflects the real
                // trip count. Drain any pending setup emissions before triggering the export.
                advanceUntilIdle()
                val settledState = expectMostRecentItem()
                assertEquals(
                    "completedTripCount should reflect the 2 trips set in the repository",
                    2,
                    settledState.completedTripCount,
                )

                viewModel.onExportRequested()

                val progressState = awaitItem()
                assertTrue("isExportInProgress should be true immediately", progressState.isExportInProgress)

                advanceUntilIdle()

                val resultState = awaitItem()
                assertFalse("isExportInProgress should be false after export completes", resultState.isExportInProgress)
                assertTrue(
                    "lastExportResult should be Success",
                    resultState.lastExportResult is ExportResult.Success,
                )
                val successResult = resultState.lastExportResult as ExportResult.Success
                assertEquals("rowCount should be 2", 2, successResult.rowCount)
                assertEquals(
                    "filename should match",
                    "mileage_trips_20240101_120000.csv",
                    successResult.filename,
                )
            }
        }

    // ------------------------------------------------------------------
    // Test 2: failure path — export returns a failure message
    // ------------------------------------------------------------------

    @Test
    fun `onExportRequested with write failure returns Failure with error message`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setTripHistory(listOf(buildTrip(id = "trip-1")))
        val fakeCsvWriter = FakeCsvWriter(ExportWriteResult.Failure("Storage full"))
        val viewModel = buildViewModel(fakeTripRepository, fakeCsvWriter)

        viewModel.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem() // settled state with completedTripCount = 1

            viewModel.onExportRequested()

            awaitItem() // isExportInProgress = true
            advanceUntilIdle()

            val resultState = awaitItem()
            assertFalse("isExportInProgress should be false after export completes", resultState.isExportInProgress)
            assertTrue(
                "lastExportResult should be Failure",
                resultState.lastExportResult is ExportResult.Failure,
            )
            val failureResult = resultState.lastExportResult as ExportResult.Failure
            assertEquals("failure message should match", "Storage full", failureResult.message)
        }
    }

    // ------------------------------------------------------------------
    // Test 3: lifecycle — isExportInProgress transitions true then false
    // ------------------------------------------------------------------

    @Test
    fun `onExportRequested sets isExportInProgress true then false around the async work`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setTripHistory(listOf(buildTrip(id = "trip-1")))
        val fakeCsvWriter = FakeCsvWriter(buildDefaultSuccessResult())
        val viewModel = buildViewModel(fakeTripRepository, fakeCsvWriter)

        viewModel.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem() // settled state with isExportInProgress = false

            viewModel.onExportRequested()

            val progressState = awaitItem()
            assertTrue("isExportInProgress should be true immediately after onExportRequested", progressState.isExportInProgress)

            advanceUntilIdle()

            val completedState = awaitItem()
            assertFalse("isExportInProgress should be false after advanceUntilIdle", completedState.isExportInProgress)
        }
    }

    // ------------------------------------------------------------------
    // Test 4: M-3 empty state — completedTripCount is zero when no trips
    // ------------------------------------------------------------------

    @Test
    fun `completedTripCount is zero when repository has no completed trips`() = runTest {
        val emptyFakeTripRepository = FakeTripRepository()
        val viewModel = buildViewModel(emptyFakeTripRepository)

        viewModel.uiState.test {
            advanceUntilIdle()
            val settledState = expectMostRecentItem()
            assertEquals(
                "completedTripCount should be 0 when no trips in repository",
                0,
                settledState.completedTripCount,
            )
        }
    }

    // ------------------------------------------------------------------
    // Test 5 (T-032 Half A / Pass 2): ViewModel passes the full Trip list through to the
    // writer (not just the CSV rows) — the sidecar generator needs the Trip objects for the
    // signing fields, which CsvRow does not carry.
    // ------------------------------------------------------------------

    @Test
    fun `onExportRequested passes the full completed trip list through to csvWriter alongside the rows`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setTripHistory(
            listOf(buildTrip(id = "trip-1"), buildTrip(id = "trip-2"), buildTrip(id = "trip-3")),
        )
        val fakeCsvWriter = FakeCsvWriter(buildDefaultSuccessResult())
        val viewModel = buildViewModel(fakeTripRepository, fakeCsvWriter)

        viewModel.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onExportRequested()
            awaitItem() // isExportInProgress = true
            advanceUntilIdle()
            awaitItem() // settled result

            assertEquals(
                "csvWriter.writeExport must receive all 3 completed trips, not just rows",
                3,
                fakeCsvWriter.lastTripCountReceived,
            )
            assertEquals(
                "csvWriter.writeExport must receive the matching row count",
                3,
                fakeCsvWriter.lastRowCountReceived,
            )
        }
    }

    // ------------------------------------------------------------------
    // Test 6 (T-032 Half A / Pass 2): success + sidecar filenames surface on ExportResult.Success
    // ------------------------------------------------------------------

    @Test
    fun `onExportRequested surfaces sidecar and verify filenames on success when sidecar was written`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setTripHistory(listOf(buildTrip(id = "trip-1")))
        val fakeCsvWriter = FakeCsvWriter(
            ExportWriteResult.Success(
                csvFilename = "mileage_trips_20260630_142205.csv",
                sidecarWritten = true,
                sidecarJsonFilename = "mileage_trips_20260630_142205.integrity.json",
                verifyMarkdownFilename = "VERIFY.md",
                integrityWarning = null,
            ),
        )
        val viewModel = buildViewModel(fakeTripRepository, fakeCsvWriter)

        viewModel.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onExportRequested()
            awaitItem()
            advanceUntilIdle()

            val resultState = awaitItem()
            val successResult = resultState.lastExportResult as ExportResult.Success
            assertEquals(
                "sidecarJsonFilename should be surfaced",
                "mileage_trips_20260630_142205.integrity.json",
                successResult.sidecarJsonFilename,
            )
            assertEquals(
                "verifyMarkdownFilename should be surfaced",
                "VERIFY.md",
                successResult.verifyMarkdownFilename,
            )
            assertEquals(
                "integrityWarning should be null when the sidecar was written successfully",
                null,
                successResult.integrityWarning,
            )
        }
    }

    // ------------------------------------------------------------------
    // Test 7 (T-032 Half A / Pass 2): fallback-with-warning — CSV still exported, sidecar wasn't,
    // and the loud warning message is surfaced on the success result rather than blocking export.
    // ------------------------------------------------------------------

    @Test
    fun `onExportRequested surfaces integrityWarning and null sidecar filenames on key-read fallback`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setTripHistory(listOf(buildTrip(id = "trip-1")))
        val fakeCsvWriter = FakeCsvWriter(
            ExportWriteResult.Success(
                csvFilename = "mileage_trips_20260630_142205.csv",
                sidecarWritten = false,
                sidecarJsonFilename = null,
                verifyMarkdownFilename = null,
                integrityWarning = "Integrity file could not be generated — these records are " +
                    "not cryptographically verifiable.",
            ),
        )
        val viewModel = buildViewModel(fakeTripRepository, fakeCsvWriter)

        viewModel.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onExportRequested()
            awaitItem()
            advanceUntilIdle()

            val resultState = awaitItem()
            assertTrue(
                "export must still report Success even when the sidecar fell back",
                resultState.lastExportResult is ExportResult.Success,
            )
            val successResult = resultState.lastExportResult as ExportResult.Success
            assertEquals(
                "csv filename must still be reported on the fallback path",
                "mileage_trips_20260630_142205.csv",
                successResult.filename,
            )
            assertEquals(null, successResult.sidecarJsonFilename)
            assertEquals(null, successResult.verifyMarkdownFilename)
            assertEquals(
                "integrityWarning must be the loud user-facing fallback message",
                "Integrity file could not be generated — these records are not cryptographically verifiable.",
                successResult.integrityWarning,
            )
        }
    }
}
