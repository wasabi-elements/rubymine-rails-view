package io.susshi.railsview.nodes

import io.susshi.railsview.settings.RailsViewSettings
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.SimpleTextAttributes
import icons.RailsViewIcons

internal data class SectionDef(
    val key: String,
    val dirName: String,
    val label: String,
    val kind: SectionKind,
    val appLevel: Boolean,
    /** Non-null for file-backed sections; path is relative to the project root. */
    val filePath: String? = null,
)

internal val DEFAULT_SECTIONS = listOf(
    SectionDef("models",      "models",      "Models",      SectionKind.MODELS,      true),
    SectionDef("controllers", "controllers", "Controllers", SectionKind.CONTROLLERS, true),
    SectionDef("views",       "views",       "Views",       SectionKind.VIEWS,       true),
    SectionDef("helpers",     "helpers",     "Helpers",     SectionKind.HELPERS,     true),
    SectionDef("mailers",     "mailers",     "Mailers",     SectionKind.MAILERS,     true),
    SectionDef("jobs",        "jobs",        "Jobs",        SectionKind.JOBS,        true),
    SectionDef("services",    "services",    "Services",    SectionKind.SERVICES,    true),
    SectionDef("channels",    "channels",    "Channels",    SectionKind.CHANNELS,    true),
    SectionDef("uploaders",   "uploaders",   "Uploaders",   SectionKind.UPLOADERS,   true),
    SectionDef("policies",    "policies",    "Policies",    SectionKind.POLICIES,    true),
    SectionDef("serializers", "serializers", "Serializers", SectionKind.SERIALIZERS, true),
    SectionDef("decorators",  "decorators",  "Decorators",  SectionKind.DECORATORS,  true),
    SectionDef("assets",      "assets",      "Assets",      SectionKind.ASSETS,      true),
    SectionDef("javascript",  "javascript",  "JavaScript",  SectionKind.JAVASCRIPT,  true),
    SectionDef("graphql",     "graphql",     "GraphQL",     SectionKind.GRAPHQL,     true),
    SectionDef("routes",      "",            "Routes",      SectionKind.ROUTES,      false, "config/routes.rb"),
    SectionDef("config",      "config",      "Config",      SectionKind.CONFIG,      false),
    SectionDef("database",    "db",          "Database",    SectionKind.DATABASE,    false),
    SectionDef("lib",         "lib",         "Lib",         SectionKind.LIB,         false),
    SectionDef("spec",        "spec",        "Spec",        SectionKind.SPEC,        false),
    SectionDef("test",        "test",        "Test",        SectionKind.TEST,        false),
)

/** Reads `railsview-defaults.txt` from the plugin bundle; falls back to DEFAULT_SECTIONS order. */
internal fun loadBundledSectionOrder(): List<String> {
    val clazz = RailsRootNode::class.java
    fun tryLoad(stream: java.io.InputStream?): List<String>? = try {
        stream?.bufferedReader()?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith('#') }
            ?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }
    return tryLoad(clazz.getResourceAsStream("/railsview-defaults.txt"))
        ?: tryLoad(clazz.classLoader?.getResourceAsStream("railsview-defaults.txt"))
        ?: DEFAULT_SECTIONS.map { it.key }
}

/** Invisible tree root — returns a single RailsProjectNode as its only child. */
class RailsRootNode(
    project: Project,
    viewSettings: ViewSettings,
) : ProjectViewNode<Project>(project, project, viewSettings) {

    // ProjectViewNodeVisitor.contains() only descends into ProjectViewNode instances;
    // returning true here lets the visitor pass through this invisible wrapper.
    override fun contains(file: VirtualFile): Boolean = true

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val project = value ?: return emptyList()
        return listOf(RailsProjectNode(project, settings))
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value?.name ?: "Rails"
    }
}

/**
 * Visible project-level node (shows the project name and the Rails icon).
 * Its children are the section nodes (Models, Controllers, Views, …).
 *
 * Section order is driven by `.railsview` in the project root (one section key per
 * line, `#` for comments). If absent, `railsview-defaults.txt` bundled in the plugin
 * is used, falling back to the hardcoded DEFAULT_SECTIONS order.
 */
class RailsProjectNode(
    project: Project,
    viewSettings: ViewSettings,
) : ProjectViewNode<Project>(project, project, viewSettings) {

    // Same as RailsRootNode — must be a ProjectViewNode so the visitor descends into it.
    override fun contains(file: VirtualFile): Boolean = true

    override fun update(presentation: PresentationData) {
        presentation.setIcon(RailsViewIcons.RAILS_ICON)
        presentation.addText(value?.name ?: "Project", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val project = value ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        val appSettings = RailsViewSettings.getInstance()

        val appRoot = findAppDirectory(project) ?: return fallbackToRawProject(project, psiManager)
        val projectRoot = findProjectRoot(project)

        val children = mutableListOf<AbstractTreeNode<*>>()
        var ordinal = 0
        val claimedRootDirs = mutableSetOf("app")
        val claimedAppDirs = mutableSetOf<String>()

        for (def in orderedSections(projectRoot)) {
            if ((def.kind == SectionKind.SPEC || def.kind == SectionKind.TEST) && !appSettings.showTests) continue
            if (def.kind == SectionKind.ROUTES && !appSettings.showRoutes) continue

            if (def.filePath != null) {
                // File-backed section (e.g. Routes → config/routes.rb)
                val vf = projectRoot?.findFileByRelativePath(def.filePath)?.takeIf { !it.isDirectory } ?: continue
                psiManager.findFile(vf)?.let { psiFile ->
                    children.add(RoutesSectionNode(project, psiFile, settings, ordinal++))
                }
            } else {
                // Directory-backed section
                val parent = if (def.appLevel) appRoot else (projectRoot ?: continue)
                val dir = parent.findChild(def.dirName)?.takeIf { it.isDirectory } ?: continue
                val psiDir = psiManager.findDirectory(dir) ?: continue
                children.add(RailsSectionNode(project, psiDir, settings, def.label, def.kind, ordinal++))
                if (def.appLevel) claimedAppDirs.add(def.dirName) else claimedRootDirs.add(def.dirName)
            }
        }

        if (projectRoot != null) {
            val rootPsiDir = psiManager.findDirectory(projectRoot)
            val appPsiDir = psiManager.findDirectory(appRoot)
            if (rootPsiDir != null && appSettings.showProjectFiles) {
                children.add(RailsProjectFilesNode(project, rootPsiDir, settings, claimedRootDirs, ordinal, appPsiDir, claimedAppDirs))
            }
        }

        return children
    }

    private fun orderedSections(projectRoot: VirtualFile?): List<SectionDef> {
        // Priority 1: .railsview file in the project root
        val projectKeys = projectRoot?.findChild(".railsview")?.let { parseKeyFile { it.inputStream } }
        if (projectKeys != null) {
            if (projectKeys.isEmpty()) return DEFAULT_SECTIONS
            return resolveKeys(projectKeys)
        }

        // Priority 2: user-configured order from Tools → Rails View settings
        val settingsOrder = RailsViewSettings.getInstance().sectionOrder
        if (settingsOrder.isNotEmpty()) return resolveKeys(settingsOrder)

        // Priority 3: bundled railsview-defaults.txt
        return resolveKeys(loadBundledSectionOrder())
    }

    private fun resolveKeys(keys: List<String>): List<SectionDef> {
        val byKey = DEFAULT_SECTIONS.associateBy { it.key }
        val seen = mutableSetOf<String>()
        val ordered = keys.mapNotNull { byKey[it]?.also { d -> seen.add(d.key) } }.toMutableList()
        DEFAULT_SECTIONS.filterTo(ordered) { it.key !in seen }
        return ordered
    }

    private fun parseKeyFile(streamProvider: () -> java.io.InputStream?): List<String>? = try {
        streamProvider()?.bufferedReader()?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith('#') }
            ?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    private fun findAppDirectory(project: Project): VirtualFile? {
        for (root in ProjectRootManager.getInstance(project).contentRoots) {
            val app = root.findChild("app")
            if (app != null && app.isDirectory && root.findChild("config") != null) return app
            val engineApp = root.parent?.findChild("app")
            if (engineApp != null && engineApp.isDirectory) return engineApp
        }
        return null
    }

    private fun findProjectRoot(project: Project): VirtualFile? {
        val roots = ProjectRootManager.getInstance(project).contentRoots
        return roots.firstOrNull { it.findChild("config") != null || it.findChild("Gemfile") != null }
            ?: roots.firstOrNull()
    }

    private fun fallbackToRawProject(project: Project, psiManager: PsiManager): Collection<AbstractTreeNode<*>> {
        return ProjectRootManager.getInstance(project).contentRoots.mapNotNull { root ->
            psiManager.findDirectory(root)?.let {
                RailsSectionNode(project, it, settings, project.name, SectionKind.MODELS)
            }
        }
    }
}
