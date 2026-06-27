package io.susshi.railsview.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import io.susshi.railsview.nodes.DEFAULT_SECTIONS
import io.susshi.railsview.nodes.loadBundledSectionOrder
import io.susshi.railsview.pane.RailsProjectViewPane
import javax.swing.DefaultListModel
import javax.swing.ListSelectionModel

class RailsViewConfigurable : BoundConfigurable("Rails View") {

    private val settings = RailsViewSettings.getInstance()

    // Maps for converting between section keys and display labels
    private val labelByKey: Map<String, String> = DEFAULT_SECTIONS.associate { it.key to it.label }
    private val keyByLabel: Map<String, String> = DEFAULT_SECTIONS.associate { it.label to it.key }

    private val listModel = DefaultListModel<String>()
    private val sectionList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 12
    }

    // Snapshot taken on reset() to detect list modifications in isModified()
    private var savedOrder: List<String> = emptyList()

    // -------------------------------------------------------------------------
    // BoundConfigurable overrides

    override fun apply() {
        super.apply()
        settings.sectionOrder = ArrayList(currentKeys())
        refreshTrees()
    }

    override fun reset() {
        super.reset()
        populateList()
    }

    override fun isModified(): Boolean = super.isModified() || currentKeys() != savedOrder

    // -------------------------------------------------------------------------
    // Panel

    override fun createPanel(): DialogPanel {
        populateList()

        val decorator = ToolbarDecorator.createDecorator(sectionList)
            .disableAddAction()
            .disableRemoveAction()
            .addExtraAction(object : AnAction("Reset to Defaults", null, AllIcons.Actions.Rollback) {
                override fun actionPerformed(e: AnActionEvent) {
                    listModel.clear()
                    loadBundledSectionOrder().forEach { key -> labelByKey[key]?.let { listModel.addElement(it) } }
                }
            })
            .createPanel()

        return panel {
            group("Display Section Options") {
                row {
                    checkBox("Show Routes section")
                        .bindSelected(settings::showRoutes)
                }
                row {
                    checkBox("Show Test sections (spec/, test/)")
                        .bindSelected(settings::showTests)
                }
                row {
                    checkBox("Show Project Files node")
                        .bindSelected(settings::showProjectFiles)
                }
                row {
                    checkBox("Show External Libraries node (gems, SDK)")
                        .bindSelected(settings::showExternalFiles)
                }
                row {
                    checkBox("Show Scratches and Consoles node")
                        .bindSelected(settings::showScratches)
                }
            }
            group("Section Behaviour Options") {
                row {
                    checkBox("Group Views under each controller")
                        .bindSelected(settings::groupViewsUnderControllers)
                }
                row {
                    checkBox("Group Methods by scope")
                        .bindSelected(settings::groupMethods)
                }
                row {
                    checkBox("Group Model Declarations by type")
                        .bindSelected(settings::groupModelMacros)
                }
                row {
                    checkBox("Nest Routes by path hierarchy (api / v1 / … each become a folder)")
                        .bindSelected(settings::routesNestedPaths)
                }
            }
            group("Section Order") {
                row {
                    comment(
                        "Select a section and use the arrows to reorder. " +
                        "This order is used when no <b>.railsview</b> file is present in the project root.<br>" +
                        "A <b>.railsview</b> file (one section key per line) always takes precedence."
                    )
                }
                row {
                    cell(decorator).align(Align.FILL).resizableColumn()
                }.resizableRow()
                row {
                    comment(
                        "<b>Alternative: .railsview file</b><br>" +
                        "Instead of using the order above, you can place a <b>.railsview</b> file in your " +
                        "project root. When present, it takes full precedence over these settings — " +
                        "the list above is ignored entirely. This is useful for committing a shared " +
                        "section order to version control so all team members get the same layout.<br><br>" +
                        "One section key per line; lines starting with <b>#</b> are comments.<br><br>" +
                        "<b>Available keys:</b><br>" +
                        "models, controllers, views, helpers, mailers, jobs, services, channels,<br>" +
                        "uploaders, policies, serializers, decorators, assets, javascript, graphql,<br>" +
                        "routes, config, database, lib, spec, test,<br>" +
                        "project_files, external_files, scratches<br><br>" +
                        "Prefix a key with <b>!</b> to hide that section for the whole team:<br>" +
                        "<code>!routes</code> — hides Routes even if enabled in personal Settings.<br><br>" +
                        "<b>Example .railsview:</b><br>" +
                        "<pre>" +
                        "# My preferred order\n" +
                        "models\n" +
                        "controllers\n" +
                        "services\n" +
                        "views\n" +
                        "database\n" +
                        "!spec\n" +
                        "!scratches\n" +
                        "</pre>" +
                        "Sections listed in the file appear first in that order. Any sections not listed " +
                        "are appended after in the default order. Sections whose directory does not exist " +
                        "in the project are silently skipped."
                    )
                }
            }
            group("About") {
                row {
                    comment(
                        "Rails View by <a href=\"https://susshi.io\">Wasabi Elements GmbH</a> — " +
                        "restores the logical project tree removed in RubyMine 2025.3.<br>" +
                        "Refresh the project view (right-click → Reload) after changing settings."
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers

    private fun populateList() {
        listModel.clear()
        effectiveOrder().forEach { key -> labelByKey[key]?.let { listModel.addElement(it) } }
        savedOrder = currentKeys()
    }

    /** Returns the order to display: saved settings → bundled defaults. */
    private fun effectiveOrder(): List<String> {
        val saved = settings.sectionOrder
        if (saved.isEmpty()) return loadBundledSectionOrder()
        val allKeys = DEFAULT_SECTIONS.map { it.key }.toSet()
        val existing = saved.filter { it in allKeys }
        val missing = DEFAULT_SECTIONS.map { it.key }.filter { it !in existing.toSet() }
        return existing + missing
    }

    private fun currentKeys(): List<String> =
        listModel.elements().asSequence().mapNotNull { keyByLabel[it] }.toList()

    private fun refreshTrees() {
        for (project in ProjectManager.getInstance().openProjects) {
            ProjectView.getInstance(project)
                .getProjectViewPaneById(RailsProjectViewPane.ID)
                ?.updateFromRoot(true)
        }
    }
}
