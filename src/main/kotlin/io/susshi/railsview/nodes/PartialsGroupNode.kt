package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.SimpleTextAttributes

/**
 * Groups all partial templates (_*.erb, _*.html.erb, etc.) under a single "Partials" node
 * inside a ControllerWithViewsNode.
 *
 * Stores "Partials" (String) as the node value — not a PsiElement — so TreeAnchorizer
 * does NOT wrap it, keeping mayContain() = true for all files.
 */
class PartialsGroupNode(
    project: Project,
    private val partials: List<VirtualFile>,
    viewSettings: ViewSettings,
) : ProjectViewNode<String>(project, "Partials", viewSettings) {

    // Weight 5 puts this after the helper (weight 0) but before method nodes (weight 10).
    // getSortKey is only used for tie-breaking within the same weight, so any constant works.
    override fun getWeight(): Int = 5
    override fun getSortKey(): Comparable<*> = "0"

    override fun contains(file: VirtualFile): Boolean = partials.any { it == file }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val psiManager = PsiManager.getInstance(myProject)
        return partials
            .sortedBy { it.name }
            .mapNotNull { vf -> psiManager.findFile(vf)?.let { PsiFileNode(myProject, it, settings) } }
    }

    override fun update(presentation: PresentationData) {
        presentation.setIcon(AllIcons.Nodes.Folder)
        presentation.addText("Partials", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }
}
