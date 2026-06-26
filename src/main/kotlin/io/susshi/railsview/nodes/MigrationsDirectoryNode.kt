package io.susshi.railsview.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleTextAttributes

// Wraps db/migrate/ — sorts children newest-first and formats each migration filename.
class MigrationsDirectoryNode(
    project: Project,
    private val directory: PsiDirectory,
    viewSettings: ViewSettings,
) : PsiDirectoryNode(project, directory, viewSettings) {

    override fun getChildrenImpl(): Collection<AbstractTreeNode<*>> =
        directory.files
            .filter { it.name.endsWith(".rb") }
            .sortedByDescending { it.name }
            .mapIndexed { index, psiFile -> MigrationFileNode(myProject, psiFile, settings, index) }
}

// Same AbstractTreeNode pattern as RailsFileNode — avoids AbstractPsiBasedNode's update chain.
// ordinal drives getWeight() so the tree comparator preserves newest-first order even when
// "Sort alphabetically" is on in the project view settings.
// "20240317113427_create_nats.rb" → "2024-03-17-113427 CreateNats"
class MigrationFileNode(
    project: Project,
    psiFile: PsiFile,
    viewSettings: ViewSettings,
    private val ordinal: Int = 0,
) : AbstractTreeNode<PsiFile>(project, psiFile) {

    private val nav = PsiFileNode(project, psiFile, viewSettings)

    // Lower ordinal = newer migration = appears first; beats alphabetical re-sort by the tree.
    override fun getWeight(): Int = ordinal

    override fun getChildren(): Collection<AbstractTreeNode<*>> = nav.children

    override fun update(data: PresentationData) {
        val file = value ?: return
        data.setIcon(file.getIcon(0))
        data.addText(formatMigrationName(file.name), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        data.tooltip = file.name
    }

    override fun canNavigate() = nav.canNavigate()
    override fun canNavigateToSource() = nav.canNavigateToSource()
    override fun navigate(requestFocus: Boolean) = nav.navigate(requestFocus)

    companion object {
        fun formatMigrationName(filename: String): String {
            val base = filename.removeSuffix(".rb")
            val underscore = base.indexOf('_')
            if (underscore < 0) return filename
            val ts = base.substring(0, underscore)
            val rest = base.substring(underscore + 1)
            if (ts.length != 14 || !ts.all { it.isDigit() }) return filename
            val date = "${ts.substring(0, 4)}-${ts.substring(4, 6)}-${ts.substring(6, 8)}"
            val time = ts.substring(8, 14)
            val name = rest.split("_").joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
            return "$date-$time $name"
        }
    }
}
