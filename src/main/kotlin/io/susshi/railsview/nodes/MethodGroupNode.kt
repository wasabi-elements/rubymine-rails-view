package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import javax.swing.Icon

/**
 * A generic folder-like group node that clusters related child nodes (methods, macros,
 * schema columns) under a labelled header — analogous to PartialsGroupNode.
 *
 * Stores a String label as the node value so TreeAnchorizer does NOT wrap it in a
 * SmartPsiElementPointer, keeping mayContain() = true for all target files.
 *
 * containingFile: the Ruby file whose PSI elements live inside this group. When set,
 * contains(file) returns true for that file so the autoscroll visitor descends into the
 * group when the cursor is anywhere inside that source file.
 */
class MethodGroupNode(
    project: Project,
    label: String,
    private val icon: Icon,
    private val nodes: List<AbstractTreeNode<*>>,
    viewSettings: ViewSettings,
    private val groupWeight: Int = 0,
    private val containingFile: VirtualFile? = null,
) : ProjectViewNode<String>(project, label, viewSettings) {

    override fun getWeight(): Int = groupWeight
    override fun getSortKey(): Comparable<*> = "%06d".format(groupWeight)

    override fun contains(file: VirtualFile): Boolean =
        file == containingFile ||
        nodes.any { it is ProjectViewNode<*> && it.contains(file) }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = nodes

    override fun update(presentation: PresentationData) {
        presentation.setIcon(icon)
        presentation.addText(value ?: "", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }
}
