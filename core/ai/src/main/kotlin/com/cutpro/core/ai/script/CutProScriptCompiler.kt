package com.cutpro.core.ai.script

import com.cutpro.core.ai.model.AddCaptionAction
import com.cutpro.core.ai.model.AddOverlayAction
import com.cutpro.core.ai.model.ApplyColorGradeAction
import com.cutpro.core.ai.model.ApplyFilterAction
import com.cutpro.core.ai.model.ApplySpeedCurveAction
import com.cutpro.core.ai.model.CutSegmentAction
import com.cutpro.core.ai.model.DuckAudioAction
import com.cutpro.core.ai.model.EditAction
import com.cutpro.core.ai.model.EditPlan
import com.cutpro.core.ai.model.PushInZoomAction
import com.cutpro.core.model.BlendMode
import com.cutpro.core.model.ColorAdjustments
import com.cutpro.core.model.LutFilterType
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.SpeedCurve
import com.cutpro.core.model.SpeedCurvePreset
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import java.util.UUID

object CutProScriptCompiler {

    fun compile(ast: ScriptAst, project: ProjectManifest): EditPlan {
        val actions = mutableListOf<EditAction>()
        val primaryVideoTrack = project.tracks.firstOrNull { it.type == TrackType.VIDEO }
            ?: project.tracks.firstOrNull()
            ?: TimelineTrack(id = "track_main_video", name = "Main Video", type = TrackType.VIDEO)

        for (call in ast.statements) {
            val commandUpper = call.commandName.uppercase()
            when (commandUpper) {
                "SPLIT" -> {
                    val targetTrack = resolveTrack(call.arguments["track"], project, primaryVideoTrack)
                    val timeUs = getTimecodeArg(call.arguments, "time", call.line)
                    val clip = findClipAtTime(targetTrack, timeUs)
                    if (clip != null && timeUs > clip.timelineStartUs && timeUs < clip.timelineEndUs) {
                        actions.add(
                            CutSegmentAction(
                                description = "Split clip at ${timeUs / 1000}ms",
                                trackId = targetTrack.id,
                                clipId = clip.id,
                                startTimeUs = timeUs,
                                endTimeUs = timeUs,
                                ripple = false
                            )
                        )
                    }
                }

                "DELETE_RANGE", "CUT" -> {
                    val targetTrack = resolveTrack(call.arguments["track"], project, primaryVideoTrack)
                    val startUs = getTimecodeArg(call.arguments, "start", call.line)
                    val endUs = getTimecodeArg(call.arguments, "end", call.line)
                    val ripple = getBooleanArg(call.arguments, "ripple", default = true)
                    if (endUs > startUs) {
                        actions.add(
                            CutSegmentAction(
                                description = "Delete range [${startUs / 1000}ms -> ${endUs / 1000}ms]",
                                trackId = targetTrack.id,
                                startTimeUs = startUs,
                                endTimeUs = endUs,
                                ripple = ripple
                            )
                        )
                    }
                }

                "APPLY_ZOOM_PUNCH" -> {
                    val targetTrack = resolveTrack(call.arguments["track"], project, primaryVideoTrack)
                    val timeUs = getTimecodeArg(call.arguments, "time", call.line)
                    val factor = getFloatArg(call.arguments, "factor", default = 1.15f)
                    val clip = findClipAtTime(targetTrack, timeUs)
                    if (clip != null) {
                        actions.add(
                            PushInZoomAction(
                                description = "Punch-in zoom ${(factor * 100).toInt()}% at ${timeUs / 1000}ms",
                                trackId = targetTrack.id,
                                clipId = clip.id,
                                startTimeUs = clip.timelineStartUs,
                                endTimeUs = clip.timelineEndUs,
                                splitPointUs = timeUs.coerceIn(clip.timelineStartUs, clip.timelineEndUs),
                                zoomScale = factor.coerceIn(1.05f, 3.0f)
                            )
                        )
                    }
                }

                "APPLY_SPEED_RAMP" -> {
                    val targetTrack = resolveTrack(call.arguments["track"], project, primaryVideoTrack)
                    val curveName = getStringArg(call.arguments, "curve", default = "HERO").uppercase()
                    val preset = try {
                        SpeedCurvePreset.valueOf(curveName)
                    } catch (e: Exception) {
                        SpeedCurvePreset.HERO
                    }
                    val clip = resolveClip(call.arguments["clip"], targetTrack)
                    if (clip != null) {
                        actions.add(
                            ApplySpeedCurveAction(
                                description = "Speed ramp curve '${preset.name}' on clip '${clip.id}'",
                                trackId = targetTrack.id,
                                clipId = clip.id,
                                startTimeUs = clip.timelineStartUs,
                                endTimeUs = clip.timelineEndUs,
                                curve = SpeedCurve.createPreset(preset)
                            )
                        )
                    }
                }

                "ADD_CAPTION" -> {
                    val captionTrack = project.tracks.firstOrNull { it.type == TrackType.CAPTION }
                        ?: TimelineTrack(id = "track_captions_auto", name = "Captions", type = TrackType.CAPTION)
                    val text = getStringArg(call.arguments, "text", default = "")
                    val startUs = getTimecodeArg(call.arguments, "start", call.line)
                    val endUs = getTimecodeArg(call.arguments, "end", call.line)
                    val style = getStringArg(call.arguments, "style", default = "HIGHLIGHT_PILL")
                    val lang = getStringArg(call.arguments, "lang", default = "en")

                    if (text.isNotBlank() && endUs > startUs) {
                        actions.add(
                            AddCaptionAction(
                                description = "Caption: \"$text\"",
                                trackId = captionTrack.id,
                                startTimeUs = startUs,
                                endTimeUs = endUs,
                                text = text,
                                style = style,
                                language = lang
                            )
                        )
                    }
                }

                "ADD_OVERLAY" -> {
                    val overlayTrack = project.tracks.firstOrNull { it.type == TrackType.OVERLAY }
                        ?: TimelineTrack(id = "track_overlay_auto", name = "Overlay", type = TrackType.OVERLAY)
                    val src = getStringArg(call.arguments, "src", default = "asset://default")
                    val startUs = getTimecodeArg(call.arguments, "start", call.line)
                    val durationUs = getTimecodeArg(call.arguments, "duration", call.line, defaultUs = 3_000_000L)
                    val scale = getFloatArg(call.arguments, "scale", default = 1.0f)
                    val blendStr = getStringArg(call.arguments, "blend", default = "NORMAL").uppercase()
                    val blendMode = try {
                        BlendMode.valueOf(blendStr)
                    } catch (e: Exception) {
                        BlendMode.NORMAL
                    }

                    actions.add(
                        AddOverlayAction(
                            description = "Add overlay from '$src'",
                            trackId = overlayTrack.id,
                            startTimeUs = startUs,
                            endTimeUs = startUs + durationUs,
                            assetUriString = src,
                            scale = scale,
                            blendMode = blendMode
                        )
                    )
                }

                "DUCK_AUDIO" -> {
                    val audioTrack = project.tracks.firstOrNull { it.type == TrackType.AUDIO }
                        ?: primaryVideoTrack
                    val startUs = getTimecodeArg(call.arguments, "start", call.line)
                    val endUs = getTimecodeArg(call.arguments, "end", call.line)
                    val db = getFloatArg(call.arguments, "db", default = -18.0f)

                    if (endUs > startUs) {
                        actions.add(
                            DuckAudioAction(
                                description = "Duck audio to ${db}dB [${startUs / 1000}ms -> ${endUs / 1000}ms]",
                                trackId = audioTrack.id,
                                startTimeUs = startUs,
                                endTimeUs = endUs,
                                targetVolumeDb = db
                            )
                        )
                    }
                }

                "APPLY_COLOR_GRADE" -> {
                    val targetTrack = resolveTrack(call.arguments["track"], project, primaryVideoTrack)
                    val clip = resolveClip(call.arguments["clip"], targetTrack)
                    if (clip != null) {
                        val adjustments = ColorAdjustments(
                            exposure = getFloatArg(call.arguments, "exposure", default = 0.0f).coerceIn(-2.0f, 2.0f),
                            contrast = getFloatArg(call.arguments, "contrast", default = 1.0f).coerceIn(0.0f, 2.0f),
                            saturation = getFloatArg(call.arguments, "saturation", default = 1.0f).coerceIn(0.0f, 2.0f),
                            temperature = getFloatArg(call.arguments, "temperature", default = 0.0f).coerceIn(-1.0f, 1.0f),
                            vignette = getFloatArg(call.arguments, "vignette", default = 0.0f).coerceIn(0.0f, 1.0f)
                        )
                        actions.add(
                            ApplyColorGradeAction(
                                description = "Color grade adjustments on clip '${clip.id}'",
                                trackId = targetTrack.id,
                                clipId = clip.id,
                                startTimeUs = clip.timelineStartUs,
                                endTimeUs = clip.timelineEndUs,
                                adjustments = adjustments
                            )
                        )
                    }
                }

                "APPLY_FILTER" -> {
                    val targetTrack = resolveTrack(call.arguments["track"], project, primaryVideoTrack)
                    val filterName = getStringArg(call.arguments, "filter", default = "NONE").uppercase()
                    val filterType = try {
                        LutFilterType.valueOf(filterName)
                    } catch (e: Exception) {
                        LutFilterType.NONE
                    }
                    val clip = resolveClip(call.arguments["clip"], targetTrack)
                    if (clip != null) {
                        actions.add(
                            ApplyFilterAction(
                                description = "Filter '${filterType.displayName}' on clip '${clip.id}'",
                                trackId = targetTrack.id,
                                clipId = clip.id,
                                startTimeUs = clip.timelineStartUs,
                                endTimeUs = clip.timelineEndUs,
                                filterType = filterType
                            )
                        )
                    }
                }
            }
        }

        return EditPlan(
            id = UUID.randomUUID().toString(),
            title = "CutPro AI Script Edit Plan",
            description = "Compiled from ${ast.statements.size} script actions",
            actions = actions,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun resolveTrack(
        argVal: ArgumentValue?,
        project: ProjectManifest,
        fallback: TimelineTrack
    ): TimelineTrack {
        if (argVal == null) return fallback
        val nameOrId = when (argVal) {
            is ArgumentValue.StringVal -> argVal.value
            else -> return fallback
        }
        val lower = nameOrId.lowercase()
        return project.tracks.firstOrNull { it.id == nameOrId }
            ?: project.tracks.firstOrNull { it.name.equals(nameOrId, ignoreCase = true) }
            ?: when {
                lower in listOf("main", "video", "main_video") -> {
                    project.tracks.firstOrNull { it.type == TrackType.VIDEO } ?: fallback
                }
                lower in listOf("overlay", "broll", "pip") -> {
                    project.tracks.firstOrNull { it.type == TrackType.OVERLAY } ?: fallback
                }
                lower in listOf("caption", "subtitles", "text") -> {
                    project.tracks.firstOrNull { it.type == TrackType.CAPTION } ?: fallback
                }
                lower in listOf("audio", "music", "bg_music") -> {
                    project.tracks.firstOrNull { it.type == TrackType.AUDIO } ?: fallback
                }
                else -> fallback
            }
    }

    private fun resolveClip(argVal: ArgumentValue?, track: TimelineTrack): TimelineClip? {
        if (argVal == null) return track.clips.firstOrNull()
        return when (argVal) {
            is ArgumentValue.StringVal -> {
                val str = argVal.value
                val intIdx = str.toIntOrNull()
                if (intIdx != null && intIdx in track.clips.indices) {
                    track.clips[intIdx]
                } else {
                    track.clips.firstOrNull { it.id == str } ?: track.clips.firstOrNull()
                }
            }
            is ArgumentValue.NumberVal -> {
                val idx = argVal.value.toInt()
                if (idx in track.clips.indices) track.clips[idx] else track.clips.firstOrNull()
            }
            else -> track.clips.firstOrNull()
        }
    }

    private fun findClipAtTime(track: TimelineTrack, timeUs: Long): TimelineClip? {
        return track.clips.firstOrNull { it.containsTimelineTimestamp(timeUs) }
    }

    private fun getTimecodeArg(
        args: Map<String, ArgumentValue>,
        key: String,
        line: Int,
        defaultUs: Long = 0L
    ): Long {
        val arg = args[key] ?: return defaultUs
        return when (arg) {
            is ArgumentValue.TimecodeVal -> arg.timeUs
            is ArgumentValue.NumberVal -> (arg.value * 1_000_000.0).toLong()
            is ArgumentValue.StringVal -> CutProScriptParser.parseTimecodeUs(arg.value)
            else -> defaultUs
        }
    }

    private fun getStringArg(args: Map<String, ArgumentValue>, key: String, default: String): String {
        val arg = args[key] ?: return default
        return when (arg) {
            is ArgumentValue.StringVal -> arg.value
            is ArgumentValue.NumberVal -> arg.value.toString()
            is ArgumentValue.BooleanVal -> arg.value.toString()
            is ArgumentValue.TimecodeVal -> arg.rawText
        }
    }

    private fun getFloatArg(args: Map<String, ArgumentValue>, key: String, default: Float): Float {
        val arg = args[key] ?: return default
        return when (arg) {
            is ArgumentValue.NumberVal -> arg.value
            is ArgumentValue.StringVal -> arg.value.toFloatOrNull() ?: default
            else -> default
        }
    }

    private fun getBooleanArg(args: Map<String, ArgumentValue>, key: String, default: Boolean): Boolean {
        val arg = args[key] ?: return default
        return when (arg) {
            is ArgumentValue.BooleanVal -> arg.value
            is ArgumentValue.StringVal -> arg.value.toBooleanStrictOrNull() ?: default
            else -> default
        }
    }
}
