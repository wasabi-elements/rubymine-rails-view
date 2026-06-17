package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall
import javax.swing.Icon

/**
 * Represents a Ruby class-level macro call (has_many, belongs_to, scope, attr_accessor, …)
 * shown as a tree child of a model file node.
 *
 * When childNodes is non-empty (e.g. typed_store with inline attribute definitions), the
 * node renders as a folder (bold text, folder icon) and expands to show the attributes.
 * Otherwise it shows a field or method icon depending on the macro command.
 *
 * Stores a display String as the node value (not RPossibleCall/PsiElement) so that
 * TreeAnchorizer does NOT wrap it → extractFileFromValue() = null → mayContain() = true.
 */
enum class MacroCategory { ASSOCIATION, SCOPE, ATTRIBUTE }

class RailsMacroNode(
    project: Project,
    call: RPossibleCall,
    viewSettings: ViewSettings,
    private val sortIndex: Int = 0,
    private val childNodes: List<AbstractTreeNode<*>> = emptyList(),
) : ProjectViewNode<String>(project, displayText(call), viewSettings) {

    private val callPointer = SmartPointerManager.getInstance(project)
        .createSmartPsiElementPointer(call)

    override fun getSortKey(): Comparable<*> = "%06d".format(sortIndex)

    override fun canRepresent(element: Any?): Boolean {
        val c = callPointer.element ?: return false
        return element === c || (element is PsiElement && c.isEquivalentTo(element))
    }

    override fun contains(file: VirtualFile): Boolean = false

    override fun getChildren(): Collection<AbstractTreeNode<*>> = childNodes

    override fun update(presentation: PresentationData) {
        if (childNodes.isNotEmpty()) {
            presentation.setIcon(AllIcons.Nodes.Folder)
            presentation.addText(value ?: "", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        } else {
            presentation.setIcon(iconFor(callPointer.element?.getCommand()))
            presentation.addText(value ?: "", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
    }

    override fun canNavigate(): Boolean {
        val c = callPointer.element ?: return false
        return c.containingFile?.virtualFile != null
    }

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun navigate(requestFocus: Boolean) {
        val c = callPointer.element ?: return
        val vFile = c.containingFile?.virtualFile ?: return
        OpenFileDescriptor(myProject, vFile, c.textOffset).navigate(requestFocus)
    }

    companion object {
        private val ASSOCIATION_COMMANDS = setOf(
            "has_many", "has_one", "belongs_to", "has_and_belongs_to_many"
        )
        private val ATTRIBUTE_COMMANDS = setOf(
            "attr_accessor", "attr_reader", "attr_writer",
            "store_accessor", "typed_store"
        )

        val MACRO_COMMANDS: Set<String> = ASSOCIATION_COMMANDS + ATTRIBUTE_COMMANDS + setOf("scope")

        fun categorize(command: String?): MacroCategory = when (command) {
            in ASSOCIATION_COMMANDS -> MacroCategory.ASSOCIATION
            "scope" -> MacroCategory.SCOPE
            else -> MacroCategory.ATTRIBUTE
        }

        private fun iconFor(command: String?): Icon = when (command) {
            in ASSOCIATION_COMMANDS -> AllIcons.Nodes.DataTables
            in ATTRIBUTE_COMMANDS   -> AllIcons.Nodes.Field
            "scope"                 -> AllIcons.Nodes.Method
            else                    -> AllIcons.Nodes.Method
        }

        fun displayText(call: RPossibleCall): String {
            val cmd = call.getCommand() ?: ""
            val args = call.getArguments()
            val firstArg = args.firstOrNull()?.text?.trim()
                ?.let { if (it.length > 40) it.take(40) + "…" else it }
            return if (firstArg != null) "$cmd $firstArg" else cmd
        }
    }
}
