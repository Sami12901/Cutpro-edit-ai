package com.cutpro.core.data.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.cutpro.core.data.database.entity.CaptionCueEntity
import com.cutpro.core.data.database.entity.ClipEntity
import com.cutpro.core.data.database.entity.KeyframeEntity
import com.cutpro.core.data.database.entity.ProjectEntity
import com.cutpro.core.data.database.entity.TrackEntity
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.TrackType
import com.cutpro.core.model.VideoCodec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomRelationalDatabaseTest {

    private lateinit var db: CutproDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = CutproDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testCascadeDeleteCascadesThroughAllTables() = runBlocking {
        val projectId = "proj-1"
        val trackId = "track-1"
        val clipId = "clip-1"
        val keyframeId = "kf-1"
        val cueId = "cue-1"

        // 1. Insert Project
        db.projectDao().insertProject(
            ProjectEntity(
                id = projectId,
                title = "Test Project",
                createdAt = 1000L,
                updatedAt = 2000L,
                aspectRatio = CanvasAspectRatio.RATIO_16_9.name,
                defaultResolution = ExportResolution.RES_1080P.name,
                defaultFrameRate = ExportFrameRate.FPS_30.name,
                videoCodec = VideoCodec.H264.name,
                durationUs = 10_000_000L
            )
        )

        // 2. Insert Track
        db.trackDao().insertTrack(
            TrackEntity(
                id = trackId,
                projectId = projectId,
                name = "Video Track",
                trackType = TrackType.VIDEO.name,
                trackIndex = 0
            )
        )

        // 3. Insert Clip
        db.clipDao().insertClip(
            ClipEntity(
                id = clipId,
                trackId = trackId,
                sourceUri = "content://media/1",
                fileName = "test.mp4",
                mimeType = "video/mp4",
                timelineStartUs = 0L,
                timelineDurationUs = 5_000_000L,
                sourceInUs = 0L,
                sourceOutUs = 5_000_000L
            )
        )

        // 4. Insert Keyframe
        db.keyframeDao().insertKeyframe(
            KeyframeEntity(
                id = keyframeId,
                clipId = clipId,
                propertyType = "SCALE",
                timestampUs = 1_000_000L,
                valueJson = "{\"scale\":1.5}",
                easingType = "LINEAR"
            )
        )

        // 5. Insert CaptionCue
        db.captionCueDao().insertCue(
            CaptionCueEntity(
                id = cueId,
                trackId = trackId,
                startTimeUs = 500_000L,
                endTimeUs = 2_000_000L,
                text = "Hello world",
                wordsJson = "[]",
                styleJson = "{}",
                layoutJson = "{}"
            )
        )

        // Verify all entities inserted
        assertThat(db.projectDao().getProjectById(projectId)).isNotNull()
        assertThat(db.trackDao().getTrackById(trackId)).isNotNull()
        assertThat(db.clipDao().getClipById(clipId)).isNotNull()
        assertThat(db.keyframeDao().getKeyframesForClip(clipId)).hasSize(1)
        assertThat(db.captionCueDao().getCuesForTrack(trackId)).hasSize(1)

        // 6. Delete Project -> Cascade should purge everything
        db.projectDao().deleteProjectById(projectId)

        assertThat(db.projectDao().getProjectById(projectId)).isNull()
        assertThat(db.trackDao().getTrackById(trackId)).isNull()
        assertThat(db.clipDao().getClipById(clipId)).isNull()
        assertThat(db.keyframeDao().getKeyframesForClip(clipId)).isEmpty()
        assertThat(db.captionCueDao().getCuesForTrack(trackId)).isEmpty()
    }

    @Test
    fun testForeignKeyViolationThrowsConstraintException(): Unit = runBlocking {
        // Attempting to insert a clip with non-existent trackId
        val clip = ClipEntity(
            id = "orphan-clip",
            trackId = "non-existent-track",
            sourceUri = "content://media/1",
            fileName = "test.mp4",
            mimeType = "video/mp4",
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L
        )

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                db.clipDao().insertClip(clip)
            }
        }
    }

    @Test
    fun testReactiveFlowObservesTableChanges() = runBlocking {
        val projectsFlow = db.projectDao().observeAllProjects()
        assertThat(projectsFlow.first()).isEmpty()

        db.projectDao().insertProject(
            ProjectEntity(
                id = "p-100",
                title = "Flow Project",
                createdAt = 100L,
                updatedAt = 200L,
                aspectRatio = CanvasAspectRatio.RATIO_9_16.name,
                defaultResolution = ExportResolution.RES_720P.name,
                defaultFrameRate = ExportFrameRate.FPS_60.name,
                videoCodec = VideoCodec.H264.name,
                durationUs = 1_000_000L
            )
        )

        val updatedList = projectsFlow.first()
        assertThat(updatedList).hasSize(1)
        assertThat(updatedList[0].id).isEqualTo("p-100")
        assertThat(updatedList[0].title).isEqualTo("Flow Project")
    }
}
