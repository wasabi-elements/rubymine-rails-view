package io.susshi.railsview.actions

import io.susshi.railsview.pane.RailsProjectViewPane
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager

/**
 * "Select in Rails View" — appears in the editor right-click context menu.
 * Switches to the Rails View pane and selects the currently open file.
 */
class SelectInRailsViewAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null && file != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            ?: return

        val projectView = ProjectView.getInstance(project)

        // Switch to the Rails pane
        projectView.changeView(RailsProjectViewPane.ID)

        // Ask the pane to select the file
        projectView.selectCB(null, file, true)
    }
}
