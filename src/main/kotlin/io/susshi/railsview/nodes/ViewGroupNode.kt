package io.susshi.railsview.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.ui.SimpleTextAttributes
import icons.RailsViewIcons

/**
 * A views sub-directory node (e.g. `app/views/posts/`).
 *
 * When shown as a child of a controller, the label is prefixed with "Views › "
 * to make the relationship obvious without needing the folder to be open.
 */
class ViewGroupNode(
    project: Project,
    directory: PsiDirectory,
    viewSettings: ViewSettings,
    private val prefixLabel: Boolean = true,
) : PsiDirectoryNode(project, directory, viewSettings) {

    override fun updateImpl(data: PresentationData) {
        data.clearText()
        val dirName = value?.name ?: return
        if (prefixLabel) {
            data.addText("Views › ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            data.addText(dirName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        } else {
            data.addText(dirName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        }
        data.setIcon(RailsViewIcons.VIEWS_ICON)
    }
}
