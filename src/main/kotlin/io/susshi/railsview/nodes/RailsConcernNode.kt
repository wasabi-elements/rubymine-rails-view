package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass

/**
 * Represents a single `include`, `extend`, or `prepend` call at class level.
 *
 * Stores the module name (String) so TreeAnchorizer does NOT wrap the value.
 * Navigation goes to the resolved concern file when found, otherwise to the
 * include/extend call in the source file.
 */
class RailsConcernNode(
    project: Project,
    call: RPossibleCall,
    private val moduleName: String,
    private val verb: String,
    private val concernFile: VirtualFile?,
    viewSettings: ViewSettings,
    private val sortIndex: Int = 0,
) : ProjectViewNode<String>(project, moduleName, viewSettings) {

    private val callPointer = SmartPointerManager.getInstance(project)
        .createSmartPsiElementPointer(call)

    override fun getSortKey(): Comparable<*> = "%06d".format(sortIndex)
    override fun contains(file: VirtualFile): Boolean = file == concernFile
    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun update(presentation: PresentationData) {
        presentation.setIcon(
            if (verb == "extend") AllIcons.Nodes.AbstractClass else AllIcons.Nodes.Interface
        )
        presentation.addText("$verb ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        presentation.addText(moduleName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun canNavigate(): Boolean = concernFile != null || callPointer.element != null
    override fun canNavigateToSource(): Boolean = canNavigate()
    override fun navigate(requestFocus: Boolean) {
        if (concernFile != null) {
            OpenFileDescriptor(myProject, concernFile, 0).navigate(requestFocus)
            return
        }
        val call = callPointer.element ?: return
        val vf = call.containingFile?.virtualFile ?: return
        OpenFileDescriptor(myProject, vf, call.textOffset).navigate(requestFocus)
    }

    companion object {
        private val CONCERN_COMMANDS = setOf("include", "extend", "prepend")

        fun extractConcerns(
            rClass: RClass,
            project: Project,
            viewSettings: ViewSettings,
        ): List<RailsConcernNode> {
            val nodes = mutableListOf<RailsConcernNode>()
            var idx = 0
            for (stmt in rClass.getStatements()) {
                val call = stmt as? RPossibleCall ?: continue
                val cmd = call.getCommand() ?: continue
                if (cmd !in CONCERN_COMMANDS) continue
                val firstArg = call.getArguments().firstOrNull()?.text?.trim() ?: continue
                // Skip symbol literals (:Foo) and string literals — only bare constants
                if (firstArg.startsWith(":") || firstArg.startsWith("\"") || firstArg.startsWith("'")) continue
                val concernFile = resolveModule(project, firstArg)
                nodes.add(RailsConcernNode(project, call, firstArg, cmd, concernFile, viewSettings, idx++))
            }
            return nodes
        }

        private fun resolveModule(project: Project, moduleName: String): VirtualFile? {
            val relativePath = moduleName.split("::").joinToString("/") { toSnakeCase(it) } + ".rb"
            val searchDirs = listOf(
                "app/concerns",
                "app/models/concerns",
                "app/controllers/concerns",
                "lib",
            )
            for (root in ProjectRootManager.getInstance(project).contentRoots) {
                for (dir in searchDirs) {
                    root.findFileByRelativePath("$dir/$relativePath")
                        ?.takeIf { !it.isDirectory }
                        ?.let { return it }
                }
            }
            return null
        }

        private fun toSnakeCase(name: String): String =
            name.replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
                .replace(Regex("([a-z\\d])([A-Z])"), "$1_$2")
                .lowercase()
    }
}
