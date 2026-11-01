package com.cutpro

import com.cutpro.navigation.CutProDestinations
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CutProDestinationsTest {

    @Test
    fun destinations_haveExpectedRoutes() {
        assertThat(CutProDestinations.DASHBOARD).isEqualTo("dashboard")
        assertThat(CutProDestinations.EDITOR_ROUTE).isEqualTo("editor/{projectId}")
        assertThat(CutProDestinations.editorRoute("proj_123")).isEqualTo("editor/proj_123")
    }
}
