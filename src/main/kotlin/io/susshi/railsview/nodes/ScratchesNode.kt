package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

/**
 * Top-level "Scratches and Consoles" node.
 *
 * Only shows root types whose directory exists and contains at least one entry,
 * matching the behaviour of the standard Project View (which also hides empty roots).
 */
class ScratchesNode(
    project: Project,
    settings: ViewSettings,
    private val ordinal: Int,
) : ProjectViewNode<String>(project, "scratches_and_consoles", settings) {

    override fun getWeight(): Int = ordinal

    override fun contains(file: VirtualFile): Boolean =
        RootType.getAllRootTypes().any { it.containsFile(file) }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val service = ScratchFileService.getInstance()
        return RootType.getAllRootTypes()
            .filter { !it.isHidden }
            .filter { service.getVirtualFile(it)?.children?.isNotEmpty() == true }
            .map { ScratchRootTypeNode(myProject, it, settings) }
    }

    override fun update(presentation: PresentationData) {
        presentation.setIcon(AllIcons.Actions.Scratch)
        presentation.presentableText = "Scratches and Consoles"
    }
}

/**
 * One folder per non-empty RootType (e.g. "Scratches", "Extensions").
 *
 * PsiManager.findDirectory() returns null for directories outside the project
 * PSI scope (scratch roots, console roots), so we use ScratchDirNode for all
 * subdirectories and PsiFileNode only for leaf files.
 */
private class ScratchRootTypeNode(
    project: Project,
    private val rootType: RootType,
    viewSettings: ViewSettings,
) : ProjectViewNode<RootType>(project, rootType, viewSettings) {

    override fun contains(file: VirtualFile): Boolean = rootType.containsFile(file)

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val dir = ScratchFileService.getInstance().getVirtualFile(rootType)
            ?: return emptyList()
        return scratchChildren(myProject, dir, settings)
    }

    override fun update(presentation: PresentationData) {
        presentation.setIcon(iconForRootType())
        presentation.presentableText = rootType.displayName
    }

    private fun iconForRootType(): javax.swing.Icon = when {
        rootType.id.contains("scratch")  -> AllIcons.Actions.Scratch
        rootType.id.contains("console")  -> AllIcons.Nodes.Console
        else                              -> AllIcons.Nodes.Folder
    }
}

/**
 * Generic directory node for folders inside a scratch root.
 * Uses VirtualFile-based child enumeration because PsiManager.findDirectory()
 * is unreliable for out-of-project directories.
 */
private class ScratchDirNode(
    project: Project,
    private val dir: VirtualFile,
    viewSettings: ViewSettings,
) : ProjectViewNode<VirtualFile>(project, dir, viewSettings) {

    override fun contains(file: VirtualFile): Boolean = false

    override fun getChildren(): Collection<AbstractTreeNode<*>> =
        scratchChildren(myProject, dir, settings)

    override fun update(presentation: PresentationData) {
        // The owning RootType (e.g. Extensions) can substitute a human-readable name
        // for its subdirectories via substituteName() — that is how "com.intellij.database"
        // becomes "Database Tools and SQL" in the standard Project View.
        val displayName = RootType.forFile(dir)
            ?.substituteName(myProject, dir)
            ?: dir.name
        presentation.setIcon(AllIcons.Nodes.Folder)
        presentation.presentableText = displayName
    }
}

/** Shared child builder: directories → ScratchDirNode, files → PsiFileNode (if resolvable). */
private fun scratchChildren(
    project: Project,
    dir: VirtualFile,
    settings: ViewSettings,
): List<AbstractTreeNode<*>> {
    val psiManager = PsiManager.getInstance(project)
    return dir.children
        .sortedWith(compareBy({ !it.isDirectory }, { it.name }))
        .mapNotNull { child ->
            if (child.isDirectory) {
                ScratchDirNode(project, child, settings)
            } else {
                psiManager.findFile(child)?.let { PsiFileNode(project, it, settings) }
            }
        }
}
