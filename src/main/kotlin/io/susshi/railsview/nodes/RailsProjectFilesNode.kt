package io.susshi.railsview.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.ui.SimpleTextAttributes
import icons.RailsViewIcons

/**
 * Shows project-root files (Gemfile, Rakefile, …) plus any root directories
 * that are not already covered by a dedicated section node.
 * Also shows any subdirectories of app/ that are not covered by a dedicated section.
 */
class RailsProjectFilesNode(
    project: Project,
    private val projectRoot: PsiDirectory,
    settings: ViewSettings,
    private val claimedRootDirs: Set<String> = emptySet(),
    private val ordinal: Int = Int.MAX_VALUE,
    private val appDir: PsiDirectory? = null,
    private val claimedAppDirs: Set<String> = emptySet(),
) : ProjectViewNode<PsiDirectory>(project, projectRoot, settings) {

    // Must be a ProjectViewNode so the visitor descends into this node during autoscroll.
    override fun contains(file: VirtualFile): Boolean =
        VfsUtilCore.isAncestor(projectRoot.virtualFile, file, false)

    companion object {
        // Files and directories that are never useful to expose in the project view.
        private val ALWAYS_HIDDEN = setOf(
            ".git", ".idea", ".DS_Store", "node_modules", ".bundle",
            "Thumbs.db", ".gitkeep", ".keep",
        )
    }

    // High weight keeps this node after all section nodes in AlphaComparator.
    override fun getWeight(): Int = ordinal

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val psiManager = PsiManager.getInstance(myProject)
        val children = mutableListOf<AbstractTreeNode<*>>()

        // Unclaimed subdirs of app/ shown as app/<name>
        if (appDir != null) {
            val hasUnclaimed = appDir.virtualFile.children.any {
                it.isDirectory && it.name !in ALWAYS_HIDDEN && it.name !in claimedAppDirs
            }
            if (hasUnclaimed) {
                children.add(AppRemainingNode(myProject, appDir, settings, claimedAppDirs))
            }
        }

        for (entry in projectRoot.virtualFile.children.sortedWith(compareBy({ !it.isDirectory }, { it.name }))) {
            if (entry.name in ALWAYS_HIDDEN) continue

            if (entry.isDirectory) {
                if (entry.name in claimedRootDirs) continue
                val psiDir = psiManager.findDirectory(entry) ?: continue
                children.add(PsiDirectoryNode(myProject, psiDir, settings))
            } else {
                val psiFile = psiManager.findFile(entry) ?: continue
                children.add(PsiFileNode(myProject, psiFile, settings))
            }
        }

        return children
    }

    override fun update(presentation: PresentationData) {
        presentation.clearText()
        presentation.addText("Project Files", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        presentation.setIcon(RailsViewIcons.PROJECT_FILES_ICON)
    }
}
