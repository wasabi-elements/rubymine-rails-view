package io.susshi.railsview.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persisted settings for the Rails View plugin.
 * Stored in `~/.config/JetBrains/<product>/options/railsView.xml`.
 */
@State(
    name = "RailsViewSettings",
    storages = [Storage("railsView.xml")]
)
class RailsViewSettings : PersistentStateComponent<RailsViewSettings> {

    /** Whether spec/ and test/ sections are shown in the tree */
    var showTests: Boolean = true

    /** Whether the top-level "Project Files" node is shown */
    var showProjectFiles: Boolean = true

    /** Whether to show the "Views › …" shortcut under each controller node */
    var groupViewsUnderControllers: Boolean = true

    /** Whether to group class/instance/private methods into folder nodes */
    var groupMethods: Boolean = true

    /** Whether to group model macros (schema, associations, scopes, attributes) into folder nodes */
    var groupModelMacros: Boolean = true

    override fun getState(): RailsViewSettings = this

    override fun loadState(state: RailsViewSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): RailsViewSettings =
            ApplicationManager.getApplication().getService(RailsViewSettings::class.java)
    }
}
