package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.SimpleTextAttributes
import io.susshi.railsview.settings.RailsViewSettings
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall
import org.jetbrains.plugins.ruby.ruby.lang.psi.RFile
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RObjectClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RSingletonMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility

// Does NOT extend PsiFileNode. AbstractPsiBasedNode (parent of PsiFileNode) wraps
// updateImpl() inside AstLoadingFilter.disallowTreeLoading() and conflicts with
// setPresentableText() set by PsiFileNode.updateImpl(). Extending AbstractTreeNode
// directly — the same pattern as ControllerWithViewsNode — is the only reliable path.
class RailsFileNode(
    project: Project,
    psiFile: PsiFile,
    private val viewSettings: ViewSettings,
    private val showClassName: Boolean = true,
    private val sortWeight: Int = 30,
) : AbstractTreeNode<PsiFile>(project, psiFile) {

    override fun getWeight(): Int = sortWeight

    // PsiFileNode kept only for navigation — never rendered.
    private val nav = PsiFileNode(project, psiFile, viewSettings)

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val file = value ?: return nav.children
        if (!file.name.endsWith(".rb")) return nav.children

        val rFile = file as? RFile ?: return nav.children
        val rClass = PsiTreeUtil.findChildOfType(rFile, RClass::class.java)
            ?: return nav.children

        val result = mutableListOf<AbstractTreeNode<*>>()
        val appSettings = RailsViewSettings.getInstance()
        val vPath = file.virtualFile?.path ?: ""
        val fileVf = file.virtualFile

        if (vPath.contains("/app/models/")) {
            // ── Concerns (include / extend / prepend) ──
            val concernNodes = RailsConcernNode.extractConcerns(rClass, myProject, viewSettings)
            if (concernNodes.isNotEmpty()) {
                if (appSettings.groupModelMacros) {
                    result.add(MethodGroupNode(myProject, "Concerns", AllIcons.Nodes.AbstractMethod,
                        concernNodes, viewSettings, groupWeight = 5, containingFile = fileVf))
                } else {
                    result.addAll(concernNodes)
                }
            }

            // ── Schema columns ──
            val columnNodes = mutableListOf<SchemaColumnNode>()
            SchemaColumnNode.columnsForModel(myProject, file.name)?.let { (schemaFile, cols) ->
                cols.forEachIndexed { idx, col ->
                    columnNodes.add(SchemaColumnNode(myProject, schemaFile, col.name, col.type, col.charOffset, viewSettings, sortIndex = idx))
                }
            }

            // ── Macros (associations, scopes, attr_*) ──
            val allMacroNodes = mutableListOf<RailsMacroNode>()
            val macrosByCategory = linkedMapOf<MacroCategory, MutableList<RailsMacroNode>>()
            var macroIdx = 0
            for (stmt in rClass.getStatements()) {
                val call = stmt as? RPossibleCall ?: continue
                val cmd = call.getCommand() ?: continue
                if (cmd !in RailsMacroNode.MACRO_COMMANDS) continue
                val children: List<AbstractTreeNode<*>> = if (cmd == "typed_store" && fileVf != null)
                    parseTypedStoreAttributes(call, fileVf)
                else
                    emptyList()
                val node = RailsMacroNode(myProject, call, viewSettings, sortIndex = macroIdx++, childNodes = children)
                val cat = RailsMacroNode.categorize(cmd)
                allMacroNodes.add(node)
                macrosByCategory.getOrPut(cat) { mutableListOf() }.add(node)
            }

            if (appSettings.groupModelMacros) {
                if (columnNodes.isNotEmpty()) {
                    result.add(MethodGroupNode(myProject, "Schema", AllIcons.Nodes.Folder,
                        columnNodes, viewSettings, groupWeight = 10, containingFile = fileVf))
                }
                macrosByCategory[MacroCategory.ASSOCIATION]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { result.add(MethodGroupNode(myProject, "Associations", AllIcons.Nodes.Folder,
                        it, viewSettings, groupWeight = 20, containingFile = fileVf)) }
                macrosByCategory[MacroCategory.SCOPE]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { result.add(MethodGroupNode(myProject, "Scopes", AllIcons.Nodes.Folder,
                        it, viewSettings, groupWeight = 30, containingFile = fileVf)) }
                macrosByCategory[MacroCategory.ATTRIBUTE]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { result.add(MethodGroupNode(myProject, "Attributes", AllIcons.Nodes.Folder,
                        it, viewSettings, groupWeight = 40, containingFile = fileVf)) }
            } else {
                result.addAll(columnNodes)
                result.addAll(allMacroNodes)
            }
        }

        // ── Methods (all Ruby files) ──
        val isModelFile = vPath.contains("/app/models/")
        val methodGroupBase = if (isModelFile) 50 else 10

        val allMethods = rClass.getMethods()
        // Collect class-level methods from two Ruby patterns:
        //   1. "def self.method_name" → RSingletonMethod direct in rClass
        //   2. "class << self ... end" → RObjectClass (extends RClass) that is a direct child
        val classMethods: List<RMethod> = buildList {
            PsiTreeUtil.findChildrenOfType(rClass, RSingletonMethod::class.java)
                .filter { PsiTreeUtil.getParentOfType(it, RClass::class.java) === rClass }
                .forEach { add(it) }
            PsiTreeUtil.findChildrenOfType(rClass, RObjectClass::class.java)
                .filter { PsiTreeUtil.getParentOfType(it, RClass::class.java) === rClass }
                .forEach { addAll(it.getMethods()) }
        }

        if (appSettings.groupMethods) {
            val instanceMethods = allMethods.filter { it !is RSingletonMethod && it.getVisibility() != Visibility.PRIVATE }
            val privateMethods = allMethods.filter { it !is RSingletonMethod && it.getVisibility() == Visibility.PRIVATE }

            if (classMethods.isNotEmpty()) {
                val nodes = classMethods.mapIndexed { idx, m -> RailsMethodNode(myProject, m, viewSettings, sortIndex = idx) }
                result.add(MethodGroupNode(myProject, "Class Methods", AllIcons.Nodes.Folder,
                    nodes, viewSettings, groupWeight = methodGroupBase, containingFile = fileVf))
            }
            if (instanceMethods.isNotEmpty()) {
                val nodes = instanceMethods.mapIndexed { idx, m -> RailsMethodNode(myProject, m, viewSettings, sortIndex = idx) }
                result.add(MethodGroupNode(myProject, "Instance Methods", AllIcons.Nodes.Folder,
                    nodes, viewSettings, groupWeight = methodGroupBase + 10, containingFile = fileVf))
            }
            if (privateMethods.isNotEmpty()) {
                val nodes = privateMethods.mapIndexed { idx, m -> RailsMethodNode(myProject, m, viewSettings, sortIndex = idx) }
                result.add(MethodGroupNode(myProject, "Private Methods", AllIcons.Nodes.Folder,
                    nodes, viewSettings, groupWeight = methodGroupBase + 20, containingFile = fileVf))
            }
        } else {
            allMethods.forEachIndexed { idx, m ->
                result.add(RailsMethodNode(myProject, m, viewSettings, sortIndex = idx))
            }
        }

        return result
    }

    override fun update(data: PresentationData) {
        val file = value ?: return
        data.setIcon(file.getIcon(0))
        val displayName = if (showClassName && file.name.endsWith(".rb"))
            toClassName(file)
        else
            file.name
        val attrs = file.virtualFile?.let { vf ->
            FileStatusManager.getInstance(myProject).getStatus(vf).color
                ?.let { SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, it) }
        } ?: SimpleTextAttributes.REGULAR_ATTRIBUTES
        data.addText(displayName, attrs)
    }

    override fun canNavigate() = nav.canNavigate()
    override fun canNavigateToSource() = nav.canNavigateToSource()
    override fun navigate(requestFocus: Boolean) = nav.navigate(requestFocus)

    private fun parseTypedStoreAttributes(
        call: RPossibleCall,
        modelVf: com.intellij.openapi.vfs.VirtualFile,
    ): List<SchemaColumnNode> {
        val callText = call.text ?: return emptyList()
        val callOffset = call.textOffset
        return TYPED_STORE_ATTR_REGEX.findAll(callText)
            .mapIndexed { idx, m ->
                SchemaColumnNode(
                    myProject,
                    modelVf,
                    columnName = m.groupValues[2],
                    columnType = m.groupValues[1],
                    charOffset = callOffset + m.range.first,
                    viewSettings = viewSettings,
                    sortIndex = idx,
                )
            }
            .toList()
    }

    companion object {
        private val TYPED_STORE_ATTR_REGEX = Regex(
            """\w+\.(string|integer|boolean|float|decimal|date|datetime|time|text|json|jsonb|any|uuid|array)\s*\(?\s*:(\w+)"""
        )

        fun toClassName(psiFile: PsiFile): String {
            if (psiFile.name.endsWith(".rb")) {
                val fqn = (psiFile as? RFile)
                    ?.getPrimaryDeclaration()
                    ?.getFQN()
                    ?.getFullPath()
                if (!fqn.isNullOrBlank()) return fqn.trimStart(':')
            }
            return mechanicalToClassName(psiFile.name)
        }

        fun mechanicalToClassName(filename: String): String {
            if (!filename.endsWith(".rb")) return filename
            return filename.removeSuffix(".rb")
                .split("_")
                .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        }
    }
}
