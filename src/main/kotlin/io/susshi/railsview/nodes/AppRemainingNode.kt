package io.susshi.railsview.nodes

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

/**
 * Represents the app/ directory inside Project Files, but only shows subdirectories
 * not already covered by a dedicated section node (e.g. app/data, app/lib).
 */
class AppRemainingNode(
    project: Project,
    directory: PsiDirectory,
    viewSettings: ViewSettings,
    private val claimedDirs: Set<String>,
) : PsiDirectoryNode(project, directory, viewSettings) {

    override fun getChildrenImpl(): Collection<AbstractTreeNode<*>> =
        super.getChildrenImpl()
            ?.filter { child ->
                child !is PsiDirectoryNode || (child.value as? PsiDirectory)?.name !in claimedDirs
            }
            ?: emptyList()
}
