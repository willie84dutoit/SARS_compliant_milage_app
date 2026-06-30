package com.mileagetracker.app.data.repository

import com.mileagetracker.app.data.local.TripPhotoDao
import com.mileagetracker.app.data.local.TripPhotoEntity
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for [TripPhotoRepositoryImpl] covering the T-039 item 8 fix (security finding M-1):
 * - [PhotoRetentionMode.SAVED] keeps the photo file on disk and writes a [TripPhotoEntity] row.
 * - [PhotoRetentionMode.TEMPORARY] deletes the photo file and writes no row — this is the
 *   "setting OFF + OCR success + user confirmed" path, since [savePhotoIfRetentionEnabled] is
 *   only ever invoked from [com.mileagetracker.app.ui.classification.TripClassificationViewModel]
 *   .onSaveClassification, i.e. after the user has tapped Save on the unified
 *   classification/odometer screen.
 * - A delete failure (simulated via an unwritable/locked file) must never throw — the save must
 *   complete (degrade-never-block). This replaces the old `check(deleted)` crash.
 *
 * Uses a hand-written fake [TripPhotoDao] (no Room/Robolectric available in plain JVM unit
 * tests) and real [java.io.File]s under a JVM temp directory so delete-failure can be triggered
 * deterministically by deleting the file out from under the repository before it attempts to
 * delete it itself (delete-of-already-deleted-file reliably returns false on all platforms).
 */
class TripPhotoRepositoryImplTest {

    private lateinit var temporaryDirectory: File
    private lateinit var fakeTripPhotoDao: FakeTripPhotoDao
    private lateinit var tripPhotoRepository: TripPhotoRepositoryImpl

    @Before
    fun setUp() {
        temporaryDirectory = File.createTempFile("trip-photo-repo-test", "").let { placeholder ->
            placeholder.delete()
            placeholder.mkdirs()
            placeholder
        }
        fakeTripPhotoDao = FakeTripPhotoDao()
        tripPhotoRepository = TripPhotoRepositoryImpl(
            tripPhotoDao = fakeTripPhotoDao,
        )
    }

    @After
    fun tearDown() {
        temporaryDirectory.deleteRecursively()
    }

    private fun createTestImageFile(fileName: String = "odometer.jpg"): File {
        val testImageFile = File(temporaryDirectory, fileName)
        testImageFile.writeText("fake-jpeg-bytes")
        return testImageFile
    }

    @Test
    fun `SAVED retention mode keeps the photo file and writes a row`() = runTest {
        val testImageFile = createTestImageFile()

        tripPhotoRepository.savePhotoIfRetentionEnabled(
            tripId = "trip-saved-mode",
            imageUri = testImageFile.absolutePath,
            retentionMode = PhotoRetentionMode.SAVED,
        )

        assertTrue("photo file must still exist when retention is SAVED", testImageFile.exists())
        assertEquals(1, fakeTripPhotoDao.insertedPhotos.size)
        assertEquals("trip-saved-mode", fakeTripPhotoDao.insertedPhotos.single().tripId)
        assertEquals(testImageFile.absolutePath, fakeTripPhotoDao.insertedPhotos.single().imageUri)
    }

    @Test
    fun `TEMPORARY retention mode after confirmed save deletes the photo file and writes no row`() = runTest {
        val testImageFile = createTestImageFile()

        // Mirrors the only real call site (TripClassificationViewModel.onSaveClassification):
        // this is invoked after OCR has completed and the user has tapped Save.
        tripPhotoRepository.savePhotoIfRetentionEnabled(
            tripId = "trip-temporary-mode",
            imageUri = testImageFile.absolutePath,
            retentionMode = PhotoRetentionMode.TEMPORARY,
        )

        assertFalse("photo file must be deleted when retention is TEMPORARY", testImageFile.exists())
        assertTrue("no TripPhotoEntity row should be written under TEMPORARY retention", fakeTripPhotoDao.insertedPhotos.isEmpty())
    }

    @Test
    fun `delete failure under TEMPORARY retention does not throw and save still completes`() = runTest {
        val testImageFile = createTestImageFile()
        // Simulate an unrecoverable delete failure: remove the file out from under the
        // repository first, so the repository's own File.exists() check still sees it as
        // present (via a wrapper) — instead, simplest deterministic approach is a File whose
        // parent directory has been deleted, making delete() return false reliably.
        val parentDirectoryOfImage = testImageFile.parentFile
        check(parentDirectoryOfImage != null) { "test setup error: image file must have a parent directory" }

        // Make the file's path point at a location that exists() reports true for via a
        // pre-created directory standing in place of the file, which File.delete() cannot
        // remove because it is non-empty (java.io.File#delete refuses non-empty directories).
        testImageFile.delete()
        testImageFile.mkdirs()
        File(testImageFile, "blocking-child.txt").writeText("keeps parent directory non-empty")

        // Should not throw despite delete() returning false on both the initial attempt and
        // the single retry.
        tripPhotoRepository.savePhotoIfRetentionEnabled(
            tripId = "trip-delete-failure",
            imageUri = testImageFile.absolutePath,
            retentionMode = PhotoRetentionMode.TEMPORARY,
        )

        assertTrue(
            "delete failure must leave the file/directory behind rather than throwing",
            testImageFile.exists(),
        )
        assertTrue(
            "no TripPhotoEntity row should be written under TEMPORARY retention even on delete failure",
            fakeTripPhotoDao.insertedPhotos.isEmpty(),
        )
    }

    @Test
    fun `deletePhotosForTrip deletes all photo files and clears rows even when one file is missing`() = runTest {
        val firstImageFile = createTestImageFile("first.jpg")
        val secondImageFileAlreadyMissing = File(temporaryDirectory, "second-already-deleted.jpg")
        // Intentionally never created on disk — exercises the "file already gone" branch in
        // deleteFileQuietlyIfExists without involving delete() at all.

        fakeTripPhotoDao.insertedPhotos.add(
            TripPhotoEntity(
                id = "photo-1",
                tripId = "trip-cleanup",
                imageUri = firstImageFile.absolutePath,
                capturedAt = 1L,
            ),
        )
        fakeTripPhotoDao.insertedPhotos.add(
            TripPhotoEntity(
                id = "photo-2",
                tripId = "trip-cleanup",
                imageUri = secondImageFileAlreadyMissing.absolutePath,
                capturedAt = 2L,
            ),
        )

        tripPhotoRepository.deletePhotosForTrip("trip-cleanup")

        assertFalse(firstImageFile.exists())
        assertTrue(fakeTripPhotoDao.deletePhotosForTripCallsRecorded.contains("trip-cleanup"))
    }
}

/**
 * Hand-written fake [TripPhotoDao] — this project uses hand-written fakes only, no mocking
 * framework. [insertedPhotos] doubles as both the insert record and the backing store consulted
 * by [getPhotosForTrip], matching how other fakes in this codebase combine recording with state.
 */
private class FakeTripPhotoDao : TripPhotoDao {

    val insertedPhotos = mutableListOf<TripPhotoEntity>()
    val deletePhotosForTripCallsRecorded = mutableListOf<String>()

    override suspend fun insertTripPhoto(photo: TripPhotoEntity) {
        insertedPhotos.add(photo)
    }

    override suspend fun getPhotosForTrip(tripId: String): List<TripPhotoEntity> {
        return insertedPhotos.filter { photo -> photo.tripId == tripId }
    }

    override suspend fun deletePhotosForTrip(tripId: String) {
        deletePhotosForTripCallsRecorded.add(tripId)
        insertedPhotos.removeAll { photo -> photo.tripId == tripId }
    }
}
