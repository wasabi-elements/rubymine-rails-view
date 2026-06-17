package io.susshi.railsview.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.ui.SimpleTextAttributes
import io.susshi.railsview.settings.RailsViewSettings

class RailsSectionNode(
    project: Project,
    private val directory: PsiDirectory,
    viewSettings: ViewSettings,
    private val label: String,
    val kind: SectionKind,
    private val ordinal: Int = 0,
) : ProjectViewNode<VirtualFile>(project, directory.virtualFile, viewSettings) {

    // getWeight covers AlphaComparator (last resort); getSortKey covers the BY_NAME
    // path in GroupByTypeComparator — both must agree so ordinal wins in all cases.
    override fun getWeight(): Int = ordinal
    override fun getSortKey(): Comparable<*> = "%04d".format(ordinal)

    // By NOT extending PsiDirectoryNode (which goes through AbstractPsiBasedNode),
    // extractFileFromValue() in AbstractTreeNode returns null for our PsiDirectory value
    // (it only handles SmartPsiElementPointer). This makes mayContain() return true for
    // every file, so the autoscroll visitor descends here even for view/helper files.
    // The real filtering is done in contains() below.
    //
    // Multi-selection is enabled by default (myMultiSelectionEnabled=true), so if the
    // visitor finds multiple matching paths, getAdjustedPaths() picks the "canonical"
    // one — which is the shorter helpers/views path, not the deeper controllers path.
    // To force a single match, helpers/views sections return false for files that have
    // a matching controller (redirecting them exclusively to the controllers section).
    override fun contains(file: VirtualFile): Boolean {
        if (VfsUtilCore.isAncestor(directory.virtualFile, file, false)) {
            when (kind) {
                SectionKind.HELPERS -> if (hasMatchingControllerForHelper(file)) return false
                SectionKind.VIEWS   -> if (hasMatchingControllerForView(file))   return false
                else -> {}
            }
            return true
        }
        if (kind == SectionKind.CONTROLLERS) {
            for (root in ProjectRootManager.getInstance(myProject).contentRoots) {
                root.findFileByRelativePath("app/helpers")?.let {
                    if (VfsUtilCore.isAncestor(it, file, false)) return true
                }
                root.findFileByRelativePath("app/views")?.let {
                    if (VfsUtilCore.isAncestor(it, file, false)) return true
                }
            }
        }
        return false
    }

    private fun hasMatchingControllerForHelper(file: VirtualFile): Boolean {
        if (!file.name.endsWith("_helper.rb")) return false
        for (root in ProjectRootManager.getInstance(myProject).contentRoots) {
            val helpersDir = root.findFileByRelativePath("app/helpers") ?: continue
            if (!VfsUtilCore.isAncestor(helpersDir, file, false)) continue
            val rel = file.path.removePrefix(helpersDir.path).trimStart('/')
            val controllerRel = rel.removeSuffix("_helper.rb") + "_controller.rb"
            if (root.findFileByRelativePath("app/controllers/$controllerRel") != null) return true
        }
        return false
    }

    private fun hasMatchingControllerForView(file: VirtualFile): Boolean {
        for (root in ProjectRootManager.getInstance(myProject).contentRoots) {
            val viewsDir = root.findFileByRelativePath("app/views") ?: continue
            if (!VfsUtilCore.isAncestor(viewsDir, file, false)) continue
            // Walk up to the direct child of viewsDir — that is the view-group dir
            var current: VirtualFile = if (file.isDirectory) file else (file.parent ?: continue)
            while (current != viewsDir) {
                val parent = current.parent ?: break
                if (parent == viewsDir) {
                    val controllerName = current.name + "_controller.rb"
                    if (root.findFileByRelativePath("app/controllers/$controllerName") != null) return true
                    break
                }
                current = parent
            }
        }
        return false
    }

    override fun update(presentation: PresentationData) {
        presentation.clearText()
        presentation.addText(label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        presentation.setIcon(kind.icon)
        presentation.locationString = directory.virtualFile.path
            .removePrefix(myProject.basePath ?: "")
            .trimStart('/')
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = when (kind) {
        SectionKind.CONTROLLERS -> buildControllerChildren()
        SectionKind.VIEWS       -> buildViewChildren()
        SectionKind.DATABASE    -> buildDatabaseChildren()
        else                    -> buildDefaultChildren()
    }

    private val showClassName: Boolean get() = when (kind) {
        SectionKind.VIEWS,
        SectionKind.ASSETS,
        SectionKind.JAVASCRIPT,
        SectionKind.CONFIG,
        SectionKind.DATABASE,
        SectionKind.PROJECT_FILES -> false
        else -> true
    }

    private fun buildDefaultChildren(): Collection<AbstractTreeNode<*>> {
        val dirs = directory.subdirectories
            .map { psiDir -> RailsDirectoryNode(myProject, psiDir, settings, showClassName) }
        val files = directory.files.map { psiFile ->
            RailsFileNode(myProject, psiFile, settings, showClassName)
        }
        return dirs + files
    }

    private fun buildControllerChildren(): Collection<AbstractTreeNode<*>> {
        val appSettings = RailsViewSettings.getInstance()
        val dirs = directory.subdirectories
            .map { psiDir -> RailsDirectoryNode(myProject, psiDir, settings, true, SectionKind.CONTROLLERS) }
        val files = directory.files.map { psiFile ->
            val fileNode = RailsFileNode(myProject, psiFile, settings, true)
            if (appSettings.groupViewsUnderControllers)
                ControllerWithViewsNode(myProject, fileNode, settings)
            else
                fileNode
        }
        return dirs + files
    }

    private fun buildDatabaseChildren(): Collection<AbstractTreeNode<*>> {
        val dirs = directory.subdirectories.map { psiDir ->
            if (psiDir.name == "migrate")
                MigrationsDirectoryNode(myProject, psiDir, settings)
            else
                RailsDirectoryNode(myProject, psiDir, settings, false)
        }
        val files = directory.files.map { psiFile ->
            RailsFileNode(myProject, psiFile, settings, false)
        }
        return dirs + files
    }

    private fun buildViewChildren(): Collection<AbstractTreeNode<*>> {
        val dirs = directory.subdirectories.map { psiDir ->
            ViewGroupNode(myProject, psiDir, settings)
        }
        val files = directory.files.map { psiFile ->
            RailsFileNode(myProject, psiFile, settings, false)
        }
        return dirs + files
    }
}
