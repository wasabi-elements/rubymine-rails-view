package io.susshi.railsview.pane

import io.susshi.railsview.nodes.RailsRootNode
import com.intellij.ide.SelectInTarget
import com.intellij.ide.impl.ProjectViewSelectInPaneTarget
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.ProjectTreeStructure
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.ui.DoubleClickListener
import icons.RailsViewIcons
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode

class RailsProjectViewPane(project: Project) : ProjectViewPane(project) {

    companion object {
        const val ID = "RailsProjectPane"
    }

    override fun getTitle(): String = "Rails View"
    override fun getId(): String = ID
    override fun getIcon(): Icon = RailsViewIcons.RAILS_ICON
    override fun getWeight(): Int = 11

    override fun createStructure() = object : ProjectTreeStructure(myProject, ID) {
        override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> =
            RailsRootNode(project, settings)

        // Override the parent-element chain so AbstractProjectViewPane.select() can find our
        // nodes when auto-scrolling from the editor. The standard PSI chain goes:
        //   PsiFile → PsiDirectory(app/models/) → PsiDirectory(app/) → PsiDirectory(root/) → Project
        // but our tree has no node for app/ or the content root, so we map section directories
        // directly to the Project, matching RailsProjectNode.getValue() == Project.
        override fun getParentElement(element: Any): Any? {
            if (element is PsiFile) return element.parent
            if (element is Project) return null
            if (element is PsiDirectory) {
                val vFile = element.virtualFile
                val contentRoots = ProjectRootManager.getInstance(myProject).contentRoots
                // Content root itself → project node
                if (contentRoots.any { it == vFile }) return myProject
                val parent = element.parent ?: return myProject
                val parentVFile = parent.virtualFile
                // Direct child of app/ or of a content root → section directory
                // Map straight to project, skipping app/ and root/ (neither has a tree node)
                return if (parentVFile.name == "app" || contentRoots.any { it == parentVFile })
                    myProject
                else
                    parent
            }
            return super.getParentElement(element)
        }
    }

    // AbstractProjectViewPane only navigates on double-click for leaf nodes (no children).
    // File nodes (RailsFileNode, ControllerWithViewsNode) have children, so the default
    // behavior just expands them. This listener navigates any double-clicked navigatable
    // node regardless of leaf status, while returning false to also allow expansion.
    override fun createComponent(): JComponent {
        val component = super.createComponent()
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val node = (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)
                    ?.userObject as? AbstractTreeNode<*>
                    ?: return false
                WriteIntentReadAction.run(Runnable { if (node.canNavigate()) node.navigate(true) })
                return false
            }
        }.installOn(tree)
        return component
    }

    override fun createSelectInTarget(): SelectInTarget =
        ProjectViewSelectInPaneTarget(myProject, this, true)
}
