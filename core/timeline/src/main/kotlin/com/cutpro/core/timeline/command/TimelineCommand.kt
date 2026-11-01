package com.cutpro.core.timeline.command

import com.cutpro.core.model.ProjectManifest

/**
 * Interface representing an atomic mutation command on a ProjectManifest with undo support.
 */
interface TimelineCommand {
    val description: String
    fun execute(current: ProjectManifest): ProjectManifest
    fun undo(current: ProjectManifest): ProjectManifest
}
