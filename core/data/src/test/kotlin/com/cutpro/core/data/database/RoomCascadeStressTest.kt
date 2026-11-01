package com.cutpro.core.data.database

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomCascadeStressTest {

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
    fun testMassiveCascadeDelete_100Tracks_1000Clips_5000Keyframes_zeroOrphans() = runBlocking {
        val projectId = "proj-stress-massive"
        val numTracks = 100
        val clipsPerTrack = 10       // 100 * 10 = 1,000 clips
        val keyframesPerClip = 5     // 1000 * 5 = 5,000 keyframes
        val cuesPerTrack = 1         // 100 * 1 = 100 caption cues

        // 1. Insert Project
        db.projectDao().insertProject(
            ProjectEntity(
                id = projectId,
                title = "Massive Stress Test Project",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                aspectRatio = CanvasAspectRatio.RATIO_16_9.name,
                defaultResolution = ExportResolution.RES_1080P.name,
                defaultFrameRate = ExportFrameRate.FPS_30.name,
                videoCodec = VideoCodec.H264.name,
                durationUs = 100_000_000L
            )
        )

        // 2. Insert Tracks, Clips, Keyframes, Cues
        val tracks = ArrayList<TrackEntity>(numTracks)
        val clips = ArrayList<ClipEntity>(numTracks * clipsPerTrack)
        val keyframes = ArrayList<KeyframeEntity>(numTracks * clipsPerTrack * keyframesPerClip)
        val cues = ArrayList<CaptionCueEntity>(numTracks * cuesPerTrack)

        for (t in 0 until numTracks) {
            val trackId = "track-$t"
            tracks.add(
                TrackEntity(
                    id = trackId,
                    projectId = projectId,
                    name = "Track $t",
                    trackType = if (t % 2 == 0) TrackType.VIDEO.name else TrackType.AUDIO.name,
                    trackIndex = t
                )
            )

            cues.add(
                CaptionCueEntity(
                    id = "cue-$t",
                    trackId = trackId,
                    startTimeUs = 0L,
                    endTimeUs = 1_000_000L,
                    text = "Cue $t",
                    wordsJson = "[]",
                    styleJson = "{}",
                    layoutJson = "{}"
                )
            )

            for (c in 0 until clipsPerTrack) {
                val clipId = "clip-$t-$c"
                clips.add(
                    ClipEntity(
                        id = clipId,
                        trackId = trackId,
                        sourceUri = "content://media/clip_${t}_${c}",
                        fileName = "video_${t}_${c}.mp4",
                        mimeType = "video/mp4",
                        timelineStartUs = c * 1_000_000L,
                        timelineDurationUs = 1_000_000L,
                        sourceInUs = 0L,
                        sourceOutUs = 1_000_000L
                    )
                )

                for (k in 0 until keyframesPerClip) {
                    val keyframeId = "kf-$t-$c-$k"
                    keyframes.add(
                        KeyframeEntity(
                            id = keyframeId,
                            clipId = clipId,
                            propertyType = "SCALE",
                            timestampUs = k * 200_000L,
                            valueJson = "{\"scale\": ${1.0 + k * 0.1}}",
                            easingType = "LINEAR"
                        )
                    )
                }
            }
        }

        // Batch insert entities into database
        for (track in tracks) {
            db.trackDao().insertTrack(track)
        }
        for (clip in clips) {
            db.clipDao().insertClip(clip)
        }
        for (keyframe in keyframes) {
            db.keyframeDao().insertKeyframe(keyframe)
        }
        for (cue in cues) {
            db.captionCueDao().insertCue(cue)
        }

        // Verify counts before deletion
        assertThat(countRows("projects")).isEqualTo(1)
        assertThat(countRows("tracks")).isEqualTo(numTracks)
        assertThat(countRows("clips")).isEqualTo(numTracks * clipsPerTrack)
        assertThat(countRows("keyframes")).isEqualTo(numTracks * clipsPerTrack * keyframesPerClip)
        assertThat(countRows("caption_cues")).isEqualTo(numTracks * cuesPerTrack)

        // 3. Perform Cascading Deletion
        val deleteDurationMs = measureTimeMillis {
            db.projectDao().deleteProjectById(projectId)
        }

        // 4. Assert ZERO orphaned records across all tables
        assertThat(countRows("projects")).isEqualTo(0)
        assertThat(countRows("tracks")).isEqualTo(0)
        assertThat(countRows("clips")).isEqualTo(0)
        assertThat(countRows("keyframes")).isEqualTo(0)
        assertThat(countRows("caption_cues")).isEqualTo(0)

        // Verify DAOs also return empty
        assertThat(db.projectDao().getProjectById(projectId)).isNull()
        assertThat(db.trackDao().getTracksForProject(projectId)).isEmpty()

        println("Cascading deletion of 1 Project, 100 Tracks, 1,000 Clips, 5,000 Keyframes took: ${deleteDurationMs}ms")
    }

    private fun countRows(tableName: String): Long {
        val cursor = db.openHelper.readableDatabase.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM $tableName"))
        cursor.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return 0L
    }
}
