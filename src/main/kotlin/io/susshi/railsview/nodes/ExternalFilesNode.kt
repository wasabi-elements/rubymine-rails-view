package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Wraps the platform's ExternalLibrariesNode so it participates in weight-based ordering.
 *
 * ExternalLibrariesNode returns NodeSortOrder.LAST from getSortOrder(), which forces it
 * to the bottom of any tree regardless of its position in the children list. This wrapper
 * delegates all content logic to the real node but omits the getSortOrder() override,
 * letting getWeight() control position just like every other section node does.
 */
class ExternalFilesNode(
    project: Project,
    settings: ViewSettings,
    private val ordinal: Int,
) : ProjectViewNode<String>(project, "external_files", settings) {

    private val delegate = ExternalLibrariesNode(project, settings)

    override fun getWeight(): Int = ordinal

    override fun contains(file: VirtualFile): Boolean = delegate.contains(file)

    override fun getChildren(): Collection<AbstractTreeNode<*>> = delegate.getChildren()

    override fun update(presentation: PresentationData) {
        presentation.setIcon(AllIcons.Nodes.PpLibFolder)
        presentation.presentableText = "External Libraries"
    }
}
