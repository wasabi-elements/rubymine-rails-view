package io.susshi.railsview.nodes

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

// Wraps a subdirectory and recursively applies class-name display to all .rb files inside.
// When kind == CONTROLLERS, file nodes are wrapped in ControllerWithViewsNode (Views link).
class RailsDirectoryNode(
    project: Project,
    directory: PsiDirectory,
    viewSettings: ViewSettings,
    private val showClassName: Boolean,
    private val kind: SectionKind = SectionKind.MODELS,
) : PsiDirectoryNode(project, directory, viewSettings) {

    override fun getChildrenImpl(): Collection<AbstractTreeNode<*>> {
        val raw = super.getChildrenImpl() ?: return emptyList()
        return raw.map { child ->
                when {
                    child is PsiFileNode && child.value is PsiFile -> wrapFile(child.value as PsiFile)
                    child is PsiDirectoryNode && child.value is PsiDirectory ->
                        RailsDirectoryNode(myProject, child.value as PsiDirectory, settings, showClassName, kind)
                    else -> child
                }
            }
    }

    private fun wrapFile(psiFile: PsiFile): AbstractTreeNode<*> {
        val fileNode = RailsFileNode(myProject, psiFile, settings, showClassName)
        return if (kind == SectionKind.CONTROLLERS)
            ControllerWithViewsNode(myProject, fileNode, settings)
        else
            fileNode
    }
}
