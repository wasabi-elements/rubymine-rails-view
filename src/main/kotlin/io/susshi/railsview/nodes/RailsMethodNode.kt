package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility

/**
 * Represents a Ruby method definition in the tree.
 *
 * Stores the method name (String) as the node value rather than the RMethod (PsiElement),
 * so TreeAnchorizer does NOT wrap it in a SmartPsiElementPointer.
 * This keeps extractFileFromValue() returning null, which makes mayContain() return true
 * for every file — allowing the autoscroll visitor to enter this node and call contains().
 *
 * For controller actions the view files are passed in and shown as children.
 */
class RailsMethodNode(
    project: Project,
    method: RMethod,
    viewSettings: ViewSettings,
    private val viewFiles: List<VirtualFile> = emptyList(),
    private val sortIndex: Int = 0,
) : ProjectViewNode<String>(project, method.name ?: "", viewSettings) {

    private val methodPointer = SmartPointerManager.getInstance(project)
        .createSmartPsiElementPointer(method)

    // Weight 10 puts methods after helper (0) and partials (5), before orphan views (30).
    // getSortKey encodes the definition-order index so methods appear in source order.
    override fun getWeight(): Int = 10
    override fun getSortKey(): Comparable<*> = "%06d".format(sortIndex)

    override fun canRepresent(element: Any?): Boolean {
        val m = methodPointer.element ?: return false
        return element === m || (element is PsiElement && m.isEquivalentTo(element))
    }

    override fun contains(file: VirtualFile): Boolean = viewFiles.any { it == file }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        if (viewFiles.isEmpty()) return emptyList()
        val psiManager = PsiManager.getInstance(myProject)
        return viewFiles.mapNotNull { vf ->
            psiManager.findFile(vf)?.let { PsiFileNode(myProject, it, settings) }
        }
    }

    override fun update(presentation: PresentationData) {
        val method = methodPointer.element
        val name = method?.name ?: (value ?: "?")
        val visibility = method?.getVisibility() ?: Visibility.PUBLIC
        presentation.setIcon(AllIcons.Nodes.Method)
        val textAttr = if (visibility == Visibility.PRIVATE)
            SimpleTextAttributes.GRAYED_ATTRIBUTES
        else
            SimpleTextAttributes.REGULAR_ATTRIBUTES
        presentation.addText(name, textAttr)
    }

    override fun canNavigate(): Boolean = methodPointer.element?.canNavigate() == true
    override fun canNavigateToSource(): Boolean = methodPointer.element?.canNavigateToSource() == true
    override fun navigate(requestFocus: Boolean) { methodPointer.element?.navigate(requestFocus) }
}
