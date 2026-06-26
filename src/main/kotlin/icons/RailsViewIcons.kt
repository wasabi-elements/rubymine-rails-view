package icons

import io.susshi.railsview.nodes.SectionKind
import com.intellij.icons.AllIcons
import javax.swing.Icon

object RailsViewIcons {

    val RAILS_ICON: Icon          = AllIcons.Nodes.Module
    val MODELS_ICON: Icon         = AllIcons.Nodes.DataSchema
    val CONTROLLERS_ICON: Icon    = AllIcons.Nodes.Controller
    val VIEWS_ICON: Icon          = AllIcons.FileTypes.Html
    val HELPERS_ICON: Icon        = AllIcons.Nodes.Method
    val MAILERS_ICON: Icon        = AllIcons.Nodes.Class
    val JOBS_ICON: Icon           = AllIcons.Nodes.RunnableMark
    val SERVICES_ICON: Icon       = AllIcons.Nodes.Services
    val CHANNELS_ICON: Icon       = AllIcons.Nodes.Pluginobsolete
    val UPLOADERS_ICON: Icon      = AllIcons.Nodes.UpFolder
    val POLICIES_ICON: Icon       = AllIcons.Nodes.SecurityRole
    val SERIALIZERS_ICON: Icon    = AllIcons.Nodes.Enum
    val DECORATORS_ICON: Icon     = AllIcons.Nodes.ClassInitializer
    val CONCERNS_ICON: Icon       = AllIcons.Nodes.AbstractMethod
    val ASSETS_ICON: Icon         = AllIcons.Nodes.ResourceBundle
    val JAVASCRIPT_ICON: Icon     = AllIcons.FileTypes.JavaScript
    val CONFIG_ICON: Icon         = AllIcons.Nodes.ConfigFolder
    val DATABASE_ICON: Icon       = AllIcons.Nodes.DataTables
    val LIB_ICON: Icon            = AllIcons.Nodes.PpLib
    val SPEC_ICON: Icon           = AllIcons.Nodes.JunitTestMark
    val TEST_ICON: Icon           = AllIcons.Nodes.JunitTestMark
    val GRAPHQL_ICON: Icon         = AllIcons.FileTypes.JsonSchema
    val ROUTES_ICON: Icon          = AllIcons.Nodes.PpWeb
    val PROJECT_FILES_ICON: Icon  = AllIcons.General.ProjectTab

    fun forSection(kind: SectionKind): Icon = when (kind) {
        SectionKind.MODELS        -> MODELS_ICON
        SectionKind.CONTROLLERS   -> CONTROLLERS_ICON
        SectionKind.VIEWS         -> VIEWS_ICON
        SectionKind.HELPERS       -> HELPERS_ICON
        SectionKind.MAILERS       -> MAILERS_ICON
        SectionKind.JOBS          -> JOBS_ICON
        SectionKind.SERVICES      -> SERVICES_ICON
        SectionKind.CHANNELS      -> CHANNELS_ICON
        SectionKind.UPLOADERS     -> UPLOADERS_ICON
        SectionKind.POLICIES      -> POLICIES_ICON
        SectionKind.SERIALIZERS   -> SERIALIZERS_ICON
        SectionKind.DECORATORS    -> DECORATORS_ICON
        SectionKind.CONCERNS      -> CONCERNS_ICON
        SectionKind.ASSETS        -> ASSETS_ICON
        SectionKind.JAVASCRIPT    -> JAVASCRIPT_ICON
        SectionKind.CONFIG        -> CONFIG_ICON
        SectionKind.DATABASE      -> DATABASE_ICON
        SectionKind.LIB           -> LIB_ICON
        SectionKind.SPEC          -> SPEC_ICON
        SectionKind.TEST          -> TEST_ICON
        SectionKind.GRAPHQL       -> GRAPHQL_ICON
        SectionKind.ROUTES        -> ROUTES_ICON
        SectionKind.PROJECT_FILES -> PROJECT_FILES_ICON
    }
}
