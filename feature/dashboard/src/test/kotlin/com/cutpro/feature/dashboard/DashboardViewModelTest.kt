package com.cutpro.feature.dashboard

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportResolution
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DashboardViewModel(projectDao = null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasTemplatesAndEmptyProjects() {
        val state = viewModel.uiState.value
        assertThat(state.projects).isEmpty()
        assertThat(state.templates.size).isAtLeast(15)
        assertThat(state.isDarkTheme).isTrue()
        assertThat(state.defaultResolution).isEqualTo(ExportResolution.RES_1080P)
    }

    @Test
    fun createNewProject_addsToUiState() = runTest {
        var createdId: String? = null
        viewModel.createNewProject(title = "My Vacation", aspectRatio = CanvasAspectRatio.RATIO_16_9) { id ->
            createdId = id
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(createdId).isNotNull()
        val projects = viewModel.uiState.value.projects
        assertThat(projects).hasSize(1)
        assertThat(projects.first().title).isEqualTo("My Vacation")
        assertThat(projects.first().aspectRatioLabel).isEqualTo("16:9")
        assertThat(projects.first().durationFormatted).isEqualTo("00:00")
    }

    @Test
    fun confirmAndDeleteProject_removesFromState() = runTest {
        viewModel.createNewProject(title = "To Delete") {}
        testDispatcher.scheduler.advanceUntilIdle()

        val project = viewModel.uiState.value.projects.first()
        viewModel.confirmDeleteProject(project)
        assertThat(viewModel.uiState.value.projectToDelete).isEqualTo(project)

        viewModel.executeDeleteProject()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.projects).isEmpty()
        assertThat(viewModel.uiState.value.projectToDelete).isNull()
    }

    @Test
    fun dismissDeleteDialog_clearsSelection() = runTest {
        viewModel.createNewProject(title = "Keep Project") {}
        testDispatcher.scheduler.advanceUntilIdle()

        val project = viewModel.uiState.value.projects.first()
        viewModel.confirmDeleteProject(project)
        assertThat(viewModel.uiState.value.projectToDelete).isNotNull()

        viewModel.dismissDeleteDialog()
        assertThat(viewModel.uiState.value.projectToDelete).isNull()
    }

    @Test
    fun settings_toggleThemeAndResolution() {
        viewModel.setDarkTheme(false)
        assertThat(viewModel.uiState.value.isDarkTheme).isFalse()

        viewModel.setDefaultResolution(ExportResolution.RES_720P)
        assertThat(viewModel.uiState.value.defaultResolution).isEqualTo(ExportResolution.RES_720P)

        viewModel.openSettings()
        assertThat(viewModel.uiState.value.isSettingsOpen).isTrue()

        viewModel.closeSettings()
        assertThat(viewModel.uiState.value.isSettingsOpen).isFalse()
    }

    @Test
    fun builtInTemplates_zeroEmojisInAllTitlesAndDescriptions() {
        fun hasEmoji(text: String): Boolean {
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (cp in 0x1F600..0x1F64F ||
                    cp in 0x1F300..0x1F5FF ||
                    cp in 0x1F680..0x1F6FF ||
                    cp in 0x1F1E0..0x1F1FF ||
                    cp in 0x2702..0x27B0 ||
                    cp in 0x1F900..0x1F9FF ||
                    cp in 0x1FA70..0x1FAFF
                ) {
                    return true
                }
                i += Character.charCount(cp)
            }
            return false
        }

        val templates = viewModel.uiState.value.templates
        for (tpl in templates) {
            assertThat(hasEmoji(tpl.title)).isFalse()
            assertThat(hasEmoji(tpl.category)).isFalse()
            assertThat(hasEmoji(tpl.description)).isFalse()
        }
    }
}

