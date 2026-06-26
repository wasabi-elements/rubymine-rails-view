package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.SimpleTextAttributes
import io.susshi.railsview.settings.RailsViewSettings
import org.jetbrains.plugins.ruby.ruby.lang.psi.RFile
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RObjectClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RSingletonMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility

class ControllerWithViewsNode(
    project: Project,
    private val delegate: RailsFileNode,
    viewSettings: ViewSettings,
) : ProjectViewNode<VirtualFile>(project, delegate.value!!.virtualFile!!, viewSettings) {

    // Store VirtualFile (not PsiFile) so TreeAnchorizer does NOT wrap it in a
    // SmartPsiElementPointer. That keeps extractFileFromValue() returning null, which
    // makes AbstractTreeNode.mayContain() return true for every target file, allowing
    // the autoscroll visitor to call our contains() and decide whether to descend.
    override fun canRepresent(element: Any?): Boolean {
        val vFile = when (element) {
            is PsiFile -> element.virtualFile ?: return false
            is VirtualFile -> element
            else -> return false
        }
        return vFile == value
    }

    override fun contains(file: VirtualFile): Boolean {
        if (file == value) return true  // controller file → allow descent to method nodes
        findMatchingHelper()?.let { if (it == file) return true }
        findMatchingViewsDir()?.let { if (VfsUtilCore.isAncestor(it, file, false)) return true }
        return false
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val children = mutableListOf<AbstractTreeNode<*>>()
        val psiManager = PsiManager.getInstance(myProject)
        val appSettings = RailsViewSettings.getInstance()
        val controllerVf = value

        // 1. Helper file (expandable — shows its own methods)
        findMatchingHelper()?.let { helperFile ->
            val psiFile = psiManager.findFile(helperFile) ?: return@let
            children.add(RailsFileNode(myProject, psiFile, settings, showClassName = true, sortWeight = 0))
        }

        // 2. Concerns (include / extend / prepend) under the controller class
        val controllerRFile = controllerVf?.let { psiManager.findFile(it) } as? RFile
        val controllerRClass = controllerRFile?.let {
            PsiTreeUtil.findChildOfType(it, RClass::class.java)
        }
        controllerRClass?.let { rc ->
            val concernNodes = RailsConcernNode.extractConcerns(rc, myProject, settings)
            if (concernNodes.isNotEmpty()) {
                children.add(MethodGroupNode(myProject, "Concerns", AllIcons.Nodes.AbstractMethod,
                    concernNodes, settings, groupWeight = 3, containingFile = controllerVf))
            }
        }

        val viewsDir = findMatchingViewsDir()
        val allViewFiles = viewsDir?.children?.filter { !it.isDirectory } ?: emptyList()
        val claimedViewFiles = mutableSetOf<VirtualFile>()

        // 4. Partials group (before methods, after helper)
        val partials = allViewFiles.filter { it.name.startsWith("_") }
        claimedViewFiles.addAll(partials)
        if (partials.isNotEmpty()) {
            children.add(PartialsGroupNode(myProject, partials, settings))
        }

        // 5. Method nodes
        val rClass = controllerRClass
        val allMethods = rClass?.getMethods() ?: emptyList()

        if (appSettings.groupMethods) {
            val classMethods: List<RMethod> = buildList {
                rClass?.let { rc ->
                    PsiTreeUtil.findChildrenOfType(rc, RSingletonMethod::class.java)
                        .filter { PsiTreeUtil.getParentOfType(it, RClass::class.java) === rc }
                        .forEach { add(it) }
                    PsiTreeUtil.findChildrenOfType(rc, RObjectClass::class.java)
                        .filter { PsiTreeUtil.getParentOfType(it, RClass::class.java) === rc }
                        .forEach { addAll(it.getMethods()) }
                }
            }

            // Public + protected methods are controller actions (can have view files)
            val actionMethods = allMethods.filter { it !is RSingletonMethod && it.getVisibility() != Visibility.PRIVATE }
            val privateMethods = allMethods.filter { it !is RSingletonMethod && it.getVisibility() == Visibility.PRIVATE }

            // Build action nodes — each may carry matching view files
            val actionNodes = actionMethods.mapIndexed { idx, method ->
                val matchingViews = allViewFiles.filter { it.name.substringBefore('.') == method.name }
                claimedViewFiles.addAll(matchingViews)
                RailsMethodNode(myProject, method, settings, matchingViews, sortIndex = idx)
            }

            if (classMethods.isNotEmpty()) {
                val nodes = classMethods.mapIndexed { idx, m -> RailsMethodNode(myProject, m, settings, sortIndex = idx) }
                children.add(MethodGroupNode(myProject, "Class Methods", AllIcons.Nodes.Folder,
                    nodes, settings, groupWeight = 9, containingFile = controllerVf))
            }
            if (actionNodes.isNotEmpty()) {
                children.add(MethodGroupNode(myProject, "Actions", AllIcons.Nodes.Folder,
                    actionNodes, settings, groupWeight = 10, containingFile = controllerVf))
            }
            if (privateMethods.isNotEmpty()) {
                val nodes = privateMethods.mapIndexed { idx, m -> RailsMethodNode(myProject, m, settings, sortIndex = idx) }
                children.add(MethodGroupNode(myProject, "Private Methods", AllIcons.Nodes.Folder,
                    nodes, settings, groupWeight = 15, containingFile = controllerVf))
            }
        } else {
            // Flat: every method with its matching views (current behaviour)
            for ((idx, method) in allMethods.withIndex()) {
                val matchingViews = allViewFiles.filter { it.name.substringBefore('.') == method.name }
                claimedViewFiles.addAll(matchingViews)
                children.add(RailsMethodNode(myProject, method, settings, matchingViews, sortIndex = idx))
            }
        }

        // 4. Orphan view files — in the views dir but not matched by any action and not a partial.
        //    PsiFileNode has weight=30 (AbstractPsiBasedNode default) so it falls after
        //    method groups (≤ 15), and its getSortKey() gives alphabetical ordering.
        allViewFiles
            .filter { it !in claimedViewFiles }
            .forEach { vf ->
                psiManager.findFile(vf)?.let {
                    children.add(PsiFileNode(myProject, it, settings))
                }
            }

        return children
    }

    override fun update(presentation: PresentationData) {
        val psiFile = delegate.value
        if (psiFile != null) {
            presentation.setIcon(psiFile.getIcon(0))
            val className = RailsFileNode.toClassName(psiFile)
            val attrs = value?.let { vf ->
                FileStatusManager.getInstance(myProject).getStatus(vf).color
                    ?.let { SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, it) }
            } ?: SimpleTextAttributes.REGULAR_ATTRIBUTES
            presentation.addText(className, attrs)
        } else {
            presentation.presentableText = value?.name ?: ""
        }
    }

    override fun canNavigate(): Boolean = delegate.canNavigate()
    override fun canNavigateToSource(): Boolean = delegate.canNavigateToSource()
    override fun navigate(requestFocus: Boolean) = delegate.navigate(requestFocus)

    private fun findMatchingViewsDir(): VirtualFile? {
        val vFile = value ?: return null
        if (!vFile.nameWithoutExtension.endsWith("_controller")) return null

        for (root in ProjectRootManager.getInstance(myProject).contentRoots) {
            val controllersDir = root.findFileByRelativePath("app/controllers") ?: continue
            val relativePath = vFile.path.removePrefix(controllersDir.path).trimStart('/')
            val viewPath = relativePath.removeSuffix(".rb").removeSuffix("_controller")
            val match = root.findFileByRelativePath("app/views/$viewPath")
            if (match != null && match.isDirectory) return match
        }
        return null
    }

    private fun findMatchingHelper(): VirtualFile? {
        val vFile = value ?: return null
        if (!vFile.nameWithoutExtension.endsWith("_controller")) return null

        for (root in ProjectRootManager.getInstance(myProject).contentRoots) {
            val controllersDir = root.findFileByRelativePath("app/controllers") ?: continue
            val relativePath = vFile.path.removePrefix(controllersDir.path).trimStart('/')
            // "admin/users_controller.rb" → "admin/users_helper.rb"
            val helperPath = relativePath.removeSuffix(".rb").removeSuffix("_controller") + "_helper.rb"
            val match = root.findFileByRelativePath("app/helpers/$helperPath")
            if (match != null && !match.isDirectory) return match
        }
        return null
    }
}
