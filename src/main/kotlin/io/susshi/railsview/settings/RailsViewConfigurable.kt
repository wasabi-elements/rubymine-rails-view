package io.susshi.railsview.settings

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import io.susshi.railsview.pane.RailsProjectViewPane

class RailsViewConfigurable : BoundConfigurable("Rails View") {

    private val settings = RailsViewSettings.getInstance()

    override fun apply() {
        super.apply()
        // Force a full tree rebuild on our pane so settings take effect immediately.
        // refresh() only repaints; updateFromRoot(true) re-calls getChildren() on every node.
        for (project in ProjectManager.getInstance().openProjects) {
            ProjectView.getInstance(project)
                .getProjectViewPaneById(RailsProjectViewPane.ID)
                ?.updateFromRoot(true)
        }
    }

    override fun createPanel(): DialogPanel = panel {
        group("Display Options") {
            row {
                checkBox("Show test sections (spec/, test/)")
                    .bindSelected(settings::showTests)
            }
            row {
                checkBox("Show 'Project Files' node (Gemfile, Rakefile, unclaimed directories…)")
                    .bindSelected(settings::showProjectFiles)
            }
            row {
                checkBox("Group matching Views directory under each Controller")
                    .bindSelected(settings::groupViewsUnderControllers)
            }
            row {
                checkBox("Group methods into folders (Class Methods / Instance Methods / Private Methods)")
                    .bindSelected(settings::groupMethods)
            }
            row {
                checkBox("Group model macros into folders (Schema / Associations / Scopes / Attributes)")
                    .bindSelected(settings::groupModelMacros)
            }
        }
        group("Section Order") {
            row {
                comment(
                    "Create a <b>.railsview</b> file in your project root to control which sections " +
                    "appear and in what order. One section key per line; lines starting with <b>#</b> are comments.<br><br>" +
                    "<b>Available keys:</b><br>" +
                    "models, controllers, views, helpers, mailers, jobs, services, channels,<br>" +
                    "uploaders, policies, serializers, decorators, concerns, graphql, assets,<br>" +
                    "javascript, config, database, lib, spec, test<br><br>" +
                    "<b>Example .railsview:</b><br>" +
                    "<pre>" +
                    "# My preferred order\n" +
                    "models\n" +
                    "controllers\n" +
                    "services\n" +
                    "views\n" +
                    "database\n" +
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
