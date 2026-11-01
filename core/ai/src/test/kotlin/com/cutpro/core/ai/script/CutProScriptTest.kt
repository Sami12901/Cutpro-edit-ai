package com.cutpro.core.ai.script

import com.cutpro.core.ai.model.AddCaptionAction
import com.cutpro.core.ai.model.AddOverlayAction
import com.cutpro.core.ai.model.ApplyColorGradeAction
import com.cutpro.core.ai.model.ApplyFilterAction
import com.cutpro.core.ai.model.ApplySpeedCurveAction
import com.cutpro.core.ai.model.CutSegmentAction
import com.cutpro.core.ai.model.DuckAudioAction
import com.cutpro.core.ai.model.PushInZoomAction
import com.cutpro.core.model.BlendMode
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.LutFilterType
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.SpeedCurvePreset
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutProScriptTest {

    private val testProject: ProjectManifest by lazy {
        val mediaSource = MediaSourceInfo(
            uriString = "content://media/1",
            fileName = "main.mp4",
            mimeType = "video/mp4",
            durationUs = 30_000_000L
        )
        val clip = TimelineClip(
            id = "clip_001",
            trackId = "track_main",
            mediaSource = mediaSource,
            timelineStartUs = 0L,
            timelineDurationUs = 30_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 30_000_000L
        )
        val track = TimelineTrack(
            id = "track_main",
            name = "Main Video",
            type = TrackType.VIDEO,
            clips = listOf(clip)
        )
        ProjectManifest(
            title = "Test AI Project",
            aspectRatio = CanvasAspectRatio.RATIO_9_16,
            durationUs = 30_000_000L,
            tracks = listOf(track)
        )
    }

    @Test
    fun lexer_tokenizesBasicScript() {
        val script = """
            // Comment line
            SPLIT(track="main", time="00:03.500")
            DELETE_RANGE(track="main", start="00:03.500", end="00:05.100", ripple=true)
        """.trimIndent()

        val tokens = CutProScriptLexer.tokenize(script)
        assertTrue(tokens.isNotEmpty())
        assertEquals(TokenKind.Identifier("SPLIT"), tokens[0].kind)
        assertEquals(TokenKind.LParen, tokens[1].kind)
    }

    @Test
    fun parser_parsesCpdslScript() {
        val script = """
            SPLIT(track="main", time="00:03.500")
            DELETE_RANGE(track="main", start="00:03.500", end="00:05.100", ripple=true)
            APPLY_ZOOM_PUNCH(time="00:08.200", factor=1.20)
            ADD_CAPTION(text="স্বাগতম সবাইকে!", start="00:00.000", end="00:02.500", style="HIGHLIGHT_PILL", lang="bn")
            ADD_OVERLAY(src="asset://b_roll.png", start="00:05.100", duration="00:03.000", scale=1.15, blend="SCREEN")
            APPLY_SPEED_RAMP(clip="clip_001", curve="HERO")
            DUCK_AUDIO(start="00:00.000", end="00:10.000", db=-18)
            APPLY_COLOR_GRADE(clip="clip_001", contrast=1.2, saturation=1.3, temperature=0.1)
            APPLY_FILTER(clip="clip_001", filter="CINEMATIC_TEAL_ORANGE")
        """.trimIndent()

        val ast = CutProScriptParser.parse(script)
        assertEquals(9, ast.statements.size)
        assertEquals("SPLIT", ast.statements[0].commandName)
        assertEquals("DELETE_RANGE", ast.statements[1].commandName)
        assertEquals("APPLY_ZOOM_PUNCH", ast.statements[2].commandName)
        assertEquals("ADD_CAPTION", ast.statements[3].commandName)
    }

    @Test
    fun parser_parsesJsonScript() {
        val json = """
            {
              "actions": [
                { "type": "SPLIT", "track": "main", "time": "00:04.000" },
                { "type": "ADD_CAPTION", "text": "Hello world", "start": "00:01.000", "end": "00:03.000" }
              ]
            }
        """.trimIndent()

        val ast = CutProScriptParser.parse(json)
        assertEquals(2, ast.statements.size)
        assertEquals("SPLIT", ast.statements[0].commandName)
        assertEquals("ADD_CAPTION", ast.statements[1].commandName)
    }

    @Test
    fun compiler_producesValidEditPlan() {
        val script = """
            SPLIT(track="main", time="00:05.000")
            DELETE_RANGE(track="main", start="00:05.000", end="00:08.000", ripple=true)
            APPLY_ZOOM_PUNCH(time="00:10.000", factor=1.25)
            ADD_CAPTION(text="বাংলা সাবটাইটেল", start="00:00.000", end="00:03.000", style="HIGHLIGHT_PILL", lang="bn")
            ADD_OVERLAY(src="content://image/1.png", start="00:08.000", duration="00:02.000", scale=1.0, blend="SCREEN")
            APPLY_SPEED_RAMP(clip="clip_001", curve="HERO")
            DUCK_AUDIO(start="00:00.000", end="00:05.000", db=-16)
            APPLY_COLOR_GRADE(clip="clip_001", contrast=1.2, saturation=1.3)
            APPLY_FILTER(clip="clip_001", filter="CINEMATIC_TEAL_ORANGE")
        """.trimIndent()

        val ast = CutProScriptParser.parse(script)
        val plan = CutProScriptCompiler.compile(ast, testProject)

        assertNotNull(plan)
        assertEquals(9, plan.actions.size)

        assertTrue(plan.actions.any { it is CutSegmentAction })
        assertTrue(plan.actions.any { it is PushInZoomAction })
        assertTrue(plan.actions.any { it is AddCaptionAction })
        assertTrue(plan.actions.any { it is AddOverlayAction })
        assertTrue(plan.actions.any { it is ApplySpeedCurveAction })
        assertTrue(plan.actions.any { it is DuckAudioAction })
        assertTrue(plan.actions.any { it is ApplyColorGradeAction })
        assertTrue(plan.actions.any { it is ApplyFilterAction })

        val overlayAction = plan.actions.first { it is AddOverlayAction } as AddOverlayAction
        assertEquals(BlendMode.SCREEN, overlayAction.blendMode)

        val filterAction = plan.actions.first { it is ApplyFilterAction } as ApplyFilterAction
        assertEquals(LutFilterType.CINEMATIC_TEAL_ORANGE, filterAction.filterType)

        val speedAction = plan.actions.first { it is ApplySpeedCurveAction } as ApplySpeedCurveAction
        assertEquals(SpeedCurvePreset.HERO, speedAction.curve.preset)
    }
}

