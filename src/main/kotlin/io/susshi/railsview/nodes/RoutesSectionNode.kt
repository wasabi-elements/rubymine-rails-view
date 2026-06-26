package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import icons.RailsViewIcons
import io.susshi.railsview.settings.RailsViewSettings
import java.awt.Color

data class RouteEntry(
    val verb: String,
    val path: String,
    val controller: String,
    val action: String,
    val charOffset: Int,
)

/**
 * Top-level Routes section backed by config/routes.rb.
 *
 * Parses explicit verb routes, root, resources, and namespace blocks.
 * Children are grouped by controller name (RouteControllerGroupNode),
 * each listing individual route entries (RouteEntryNode).
 *
 * Stores VirtualFile as value (not PsiFile) so TreeAnchorizer does not wrap it.
 */
class RoutesSectionNode(
    project: Project,
    routesPsi: PsiFile,
    viewSettings: ViewSettings,
    private val ordinal: Int = 0,
) : ProjectViewNode<VirtualFile>(project, routesPsi.virtualFile!!, viewSettings) {

    override fun getWeight(): Int = ordinal
    override fun getSortKey(): Comparable<*> = "%04d".format(ordinal)
    override fun contains(file: VirtualFile): Boolean = file == value

    override fun update(presentation: PresentationData) {
        presentation.setIcon(RailsViewIcons.ROUTES_ICON)
        presentation.addText("Routes", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        presentation.locationString = "config/routes.rb"
    }

    override fun canNavigate() = true
    override fun canNavigateToSource() = true
    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(myProject, value!!, 0).navigate(requestFocus)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val vf = value ?: return emptyList()
        val text = try { VfsUtilCore.loadText(vf) } catch (_: Exception) { return emptyList() }
        val routes = parseRoutes(text)
        return if (RailsViewSettings.getInstance().routesNestedPaths) {
            buildNestedView(routes, vf)
        } else {
            buildFlatView(routes, vf)
        }
    }

    private fun buildFlatView(routes: List<RouteEntry>, vf: VirtualFile): List<AbstractTreeNode<*>> =
        routes
            .groupBy { it.controller }
            .entries
            .sortedBy { it.key }
            .map { (controller, entries) ->
                RouteControllerGroupNode(myProject, controller, entries, vf, settings)
            }

    private fun buildNestedView(routes: List<RouteEntry>, vf: VirtualFile): List<AbstractTreeNode<*>> {
        class Trie {
            val sub: MutableMap<String, Trie> = sortedMapOf()
            val entries: MutableList<RouteEntry> = mutableListOf()
        }

        val root = Trie()
        for ((controller, entries) in routes.groupBy { it.controller }) {
            var node = root
            for (seg in controller.split("/").filter { it.isNotEmpty() }) {
                node = node.sub.getOrPut(seg) { Trie() }
            }
            node.entries.addAll(entries)
        }

        fun trieToNodes(trie: Trie, path: String): List<AbstractTreeNode<*>> =
            trie.sub.map { (seg, child) ->
                val fullPath = if (path.isEmpty()) seg else "$path/$seg"
                val routeNodes = child.entries.mapIndexed { i, e -> RouteEntryNode(myProject, e, vf, settings, i) }
                val childSegNodes = trieToNodes(child, fullPath)
                RoutePathSegmentNode(myProject, seg, fullPath, routeNodes + childSegNodes, vf, settings)
            }

        return trieToNodes(root, "")
    }

    companion object {
        // Handles both `to: 'ctrl#action'` and `:to => 'ctrl#action'`
        private val VERB_ROUTE_RE = Regex(
            """(get|post|put|patch|delete|head)\s+['"]([^'"]+)['"]\s*[^#\n]*?(?::?to\s*(?:=>|:))\s*['"](\w[\w/]*)#(\w+)['"]""",
            RegexOption.IGNORE_CASE
        )
        // verb "path" => "ctrl#action" shorthand (no to: keyword)
        private val ARROW_ROUTE_RE = Regex(
            """(get|post|put|patch|delete|head)\s+['"]([^'"]+)['"]\s*=>\s*['"](\w[\w/]*)#(\w+)['"]""",
            RegexOption.IGNORE_CASE
        )
        // get '/path', controller: :name, action: :name (or :action => :name)
        private val VERB_CTRL_ACTION_RE = Regex(
            """(get|post|put|patch|delete|head)\s+['"]([^'"]+)['"]\s*[^#\n]*?\bcontroller:\s*:(\w+)[^#\n]*?:?action\s*(?:=>|:)\s*:(\w+)""",
            RegexOption.IGNORE_CASE
        )
        // get '/path', action: :name — controller inferred from enclosing namespace
        private val ACTION_ONLY_RE = Regex(
            """(get|post|put|patch|delete|head)\s+['"]([^'"]+)['"]\s*[^#\n]*?:?action\s*(?:=>|:)\s*:(\w+)""",
            RegexOption.IGNORE_CASE
        )
        // Handles `root 'ctrl#action'`, `root to: '...'`, and `root :to => '...'`
        private val ROOT_ROUTE_RE = Regex(
            """root\s+(?:(?::?to\s*(?:=>|:))\s*)?['"](\w[\w/]*)#(\w+)['"]"""
        )
        // match '/path', :to => / to: 'ctrl#action'
        private val MATCH_RE = Regex(
            """match\s+['"]([^'"]+)['"]\s*[^#\n]*?(?::?to\s*(?:=>|:))\s*['"](\w[\w/]*)#(\w+)['"]""",
            RegexOption.IGNORE_CASE
        )
        private val VIA_ALL_RE = Regex(""":?via\s*(?:=>|:)\s*:all""", RegexOption.IGNORE_CASE)
        private val VIA_LIST_RE = Regex(""":?via\s*(?:=>|:)\s*\[([^\]]*)\]""", RegexOption.IGNORE_CASE)
        private val VIA_SINGLE_RE = Regex(""":?via\s*(?:=>|:)\s*:(\w+)""", RegexOption.IGNORE_CASE)

        private val NAMESPACE_RE = Regex("""namespace\s+:(\w+)""")
        private val CONCERN_DEF_RE = Regex("""concern\s+:(\w+)""")
        // concerns :name (plural) — applies a concern definition
        private val CONCERNS_USE_RE = Regex("""\bconcerns\s""")
        private val CONCERN_NAME_RE = Regex(""":(\w+)""")
        private val RESOURCES_RE = Regex("""(resources?)\s+:(\w+)""")
        private val RESOURCES_CTRL_RE = Regex("""\bcontroller:\s*:(\w+)""")
        // Handles both `only: [...]` and `:only => [...]`
        private val ONLY_RE = Regex(""":?only\s*(?:=>|:)\s*\[([^\]]*)\]""")
        private val EXCEPT_RE = Regex(""":?except\s*(?:=>|:)\s*\[([^\]]*)\]""")

        private val PLURAL_ACTIONS = listOf(
            "index" to "GET", "new" to "GET", "create" to "POST",
            "show" to "GET", "edit" to "GET", "update" to "PATCH", "destroy" to "DELETE"
        )
        private val SINGULAR_ACTIONS = listOf(
            "new" to "GET", "create" to "POST",
            "show" to "GET", "edit" to "GET", "update" to "PATCH", "destroy" to "DELETE"
        )

        private data class RouteTemplate(val verb: String, val path: String, val action: String)

        fun parseRoutes(text: String): List<RouteEntry> {
            val lines = text.lines()
            val concernTemplates = parseConcernDefinitions(lines)
            return parseRoutesPass2(lines, concernTemplates)
        }

        /** Pre-scan: collect route templates from every `concern :name do … end` block. */
        private fun parseConcernDefinitions(lines: List<String>): Map<String, List<RouteTemplate>> {
            val result = mutableMapOf<String, MutableList<RouteTemplate>>()
            var i = 0
            while (i < lines.size) {
                val stripped = lines[i].trim()
                val withoutComment = stripped.substringBefore("#").trimEnd()
                val opensBlock = withoutComment.endsWith(" do") || withoutComment == "do"

                if (opensBlock && CONCERN_DEF_RE.containsMatchIn(stripped)) {
                    val concernName = CONCERN_DEF_RE.find(stripped)!!.groupValues[1]
                    val templates = mutableListOf<RouteTemplate>()
                    var depth = 1
                    i++
                    while (i < lines.size && depth > 0) {
                        val inner = lines[i].trim()
                        if (inner.isNotEmpty() && !inner.startsWith("#")) {
                            val isEnd = inner == "end" || (inner.startsWith("end") && inner.length > 3
                                    && !inner[3].isLetterOrDigit() && inner[3] != '_')
                            val innerWithout = inner.substringBefore("#").trimEnd()
                            val innerOpens = innerWithout.endsWith(" do") || innerWithout == "do"
                            when {
                                isEnd -> depth--
                                innerOpens -> depth++
                                else -> ACTION_ONLY_RE.find(inner)?.let { m ->
                                    templates.add(RouteTemplate(m.groupValues[1].uppercase(), m.groupValues[2], m.groupValues[3]))
                                }
                            }
                        }
                        if (depth > 0) i++ else break
                    }
                    result[concernName] = templates
                }
                i++
            }
            return result
        }

        private fun parseRoutesPass2(
            lines: List<String>,
            concernTemplates: Map<String, List<RouteTemplate>>,
        ): List<RouteEntry> {
            val routes = mutableListOf<RouteEntry>()
            // ns:       namespace prefix contributed by this frame ("" = none)
            // suppress: true when inside a concern *definition* (routes here are not real)
            data class Frame(val ns: String, val suppress: Boolean)
            val blockStack = mutableListOf<Frame>()
            var charOffset = 0

            for (line in lines) {
                val lineLen = line.length + 1
                val stripped = line.trim()

                if (stripped.isEmpty() || stripped.startsWith("#")) {
                    charOffset += lineLen
                    continue
                }

                // "end" closes the most recent block.
                // Guard: end[letter/_] is an identifier, not a block terminator.
                if (stripped == "end" || (stripped.startsWith("end") && stripped.length > 3
                        && !stripped[3].isLetterOrDigit() && stripped[3] != '_')) {
                    if (blockStack.isNotEmpty()) blockStack.removeLast()
                    charOffset += lineLen
                    continue
                }

                val suppressed = blockStack.any { it.suppress }
                val controllerPrefix = blockStack.filter { it.ns.isNotEmpty() }.joinToString("/") { it.ns }
                val withoutComment = stripped.substringBefore("#").trimEnd()
                val opensBlock = withoutComment.endsWith(" do") || withoutComment == "do"

                when {
                    // Namespace block — contributes a controller prefix
                    opensBlock && NAMESPACE_RE.containsMatchIn(stripped) -> {
                        blockStack.add(Frame(NAMESPACE_RE.find(stripped)!!.groupValues[1], false))
                        charOffset += lineLen
                        continue
                    }
                    // Concern *definition* block — routes inside are not real; suppress them
                    opensBlock && CONCERN_DEF_RE.containsMatchIn(stripped) -> {
                        blockStack.add(Frame("", true))
                        charOffset += lineLen
                        continue
                    }
                    // Any other block opening (resources do, scope do, member do, …)
                    opensBlock -> blockStack.add(Frame("", false))
                }

                if (!suppressed) {
                    // concerns :name — expand pre-parsed templates into current namespace context
                    if (CONCERNS_USE_RE.containsMatchIn(stripped) && controllerPrefix.isNotEmpty()) {
                        val pathPrefix = "/$controllerPrefix"
                        for (name in CONCERN_NAME_RE.findAll(stripped.substringAfter("concerns")).map { it.groupValues[1] }) {
                            for (t in concernTemplates[name] ?: emptyList()) {
                                val fullPath = if (t.path.startsWith("/")) pathPrefix + t.path else "$pathPrefix/${t.path}"
                                routes.add(RouteEntry(t.verb, fullPath, controllerPrefix, t.action, charOffset))
                            }
                        }
                    }

                    // Explicit verb routes — try patterns in priority order, use first match
                    val verbEntry = VERB_ROUTE_RE.find(stripped)?.let { m ->
                        RouteEntry(m.groupValues[1].uppercase(), m.groupValues[2],
                            prefixed(m.groupValues[3], controllerPrefix), m.groupValues[4], charOffset)
                    } ?: ARROW_ROUTE_RE.find(stripped)?.let { m ->
                        RouteEntry(m.groupValues[1].uppercase(), m.groupValues[2],
                            prefixed(m.groupValues[3], controllerPrefix), m.groupValues[4], charOffset)
                    } ?: VERB_CTRL_ACTION_RE.find(stripped)?.let { m ->
                        RouteEntry(m.groupValues[1].uppercase(), m.groupValues[2],
                            prefixed(m.groupValues[3], controllerPrefix), m.groupValues[4], charOffset)
                    } ?: if (controllerPrefix.isNotEmpty()) {
                        ACTION_ONLY_RE.find(stripped)?.let { m ->
                            RouteEntry(m.groupValues[1].uppercase(), m.groupValues[2],
                                controllerPrefix, m.groupValues[3], charOffset)
                        }
                    } else null
                    verbEntry?.let { routes.add(it) }

                    // Root route
                    ROOT_ROUTE_RE.find(stripped)?.let { m ->
                        routes.add(RouteEntry("GET", "/",
                            prefixed(m.groupValues[1], controllerPrefix), m.groupValues[2], charOffset))
                    }

                    // match verb — extract :via to determine HTTP verb(s)
                    MATCH_RE.find(stripped)?.let { m ->
                        val verb = when {
                            VIA_ALL_RE.containsMatchIn(stripped) -> "ALL"
                            else -> {
                                val list = VIA_LIST_RE.find(stripped)
                                val single = VIA_SINGLE_RE.find(stripped)
                                when {
                                    list != null -> list.groupValues[1].split(",")
                                        .map { it.trim().trimStart(':').uppercase() }
                                        .joinToString("|")
                                    single != null -> single.groupValues[1].uppercase()
                                    else -> "ALL"
                                }
                            }
                        }
                        routes.add(RouteEntry(verb, m.groupValues[1],
                            prefixed(m.groupValues[2], controllerPrefix), m.groupValues[3], charOffset))
                    }

                    // resources / resource — honour controller: override
                    RESOURCES_RE.find(stripped)?.let { m ->
                        val isSingular = m.groupValues[1] == "resource"
                        val name = m.groupValues[2]
                        val controllerOverride = RESOURCES_CTRL_RE.find(stripped)?.groupValues?.get(1)
                        val controller = prefixed(controllerOverride ?: name, controllerPrefix)
                        val pathBase = if (controllerPrefix.isEmpty()) "/$name" else "/$controllerPrefix/$name"
                        val baseActions = if (isSingular) SINGULAR_ACTIONS else PLURAL_ACTIONS
                        for ((action, verb) in filterActions(baseActions, stripped)) {
                            routes.add(RouteEntry(verb, actionPath(pathBase, action, isSingular), controller, action, charOffset))
                        }
                    }
                }

                charOffset += lineLen
            }
            return routes
        }

        private fun prefixed(controller: String, prefix: String): String =
            if (prefix.isEmpty() || controller.contains("/")) controller else "$prefix/$controller"

        private fun filterActions(
            base: List<Pair<String, String>>,
            line: String,
        ): List<Pair<String, String>> {
            ONLY_RE.find(line)?.let { m ->
                val only = m.groupValues[1].split(",").map { it.trim().trimStart(':') }.toSet()
                return base.filter { it.first in only }
            }
            EXCEPT_RE.find(line)?.let { m ->
                val except = m.groupValues[1].split(",").map { it.trim().trimStart(':') }.toSet()
                return base.filter { it.first !in except }
            }
            return base
        }

        private fun actionPath(base: String, action: String, singular: Boolean): String = when (action) {
            "index"   -> base
            "new"     -> "$base/new"
            "create"  -> base
            "show"    -> if (singular) base else "$base/:id"
            "edit"    -> if (singular) "$base/edit" else "$base/:id/edit"
            "update"  -> if (singular) base else "$base/:id"
            "destroy" -> if (singular) base else "$base/:id"
            else -> base
        }
    }
}

class RouteControllerGroupNode(
    project: Project,
    private val controllerName: String,
    private val entries: List<RouteEntry>,
    private val routesFile: VirtualFile,
    viewSettings: ViewSettings,
) : ProjectViewNode<String>(project, controllerName, viewSettings) {

    override fun contains(file: VirtualFile): Boolean = false

    override fun getChildren(): Collection<AbstractTreeNode<*>> =
        entries.mapIndexed { idx, entry -> RouteEntryNode(myProject, entry, routesFile, settings, idx) }

    override fun update(presentation: PresentationData) {
        presentation.setIcon(AllIcons.Nodes.Controller)
        presentation.addText(controllerName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        presentation.addText("  ${entries.size}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    // Navigate to the controller file if it exists, otherwise no-op
    override fun canNavigate(): Boolean = findControllerFile() != null
    override fun canNavigateToSource(): Boolean = canNavigate()
    override fun navigate(requestFocus: Boolean) {
        val vf = findControllerFile() ?: return
        OpenFileDescriptor(project, vf, 0).navigate(requestFocus)
    }

    private fun findControllerFile(): VirtualFile? {
        val relPath = "app/controllers/${controllerName}_controller.rb"
        for (root in ProjectRootManager.getInstance(project).contentRoots) {
            root.findFileByRelativePath(relPath)?.takeIf { !it.isDirectory }?.let { return it }
        }
        return null
    }
}

class RoutePathSegmentNode(
    project: Project,
    private val segment: String,
    private val controllerPath: String,
    private val childNodes: List<AbstractTreeNode<*>>,
    private val routesFile: VirtualFile,
    viewSettings: ViewSettings,
) : ProjectViewNode<String>(project, controllerPath, viewSettings) {

    override fun contains(file: VirtualFile) = false
    override fun getChildren() = childNodes

    override fun update(presentation: PresentationData) {
        val hasSubfolders = childNodes.any { it is RoutePathSegmentNode }
        if (hasSubfolders) {
            presentation.setIcon(AllIcons.Nodes.Package)
            presentation.addText(segment, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        } else {
            presentation.setIcon(AllIcons.Nodes.Controller)
            presentation.addText(segment, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            presentation.addText("  ${childNodes.size}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }

    override fun canNavigate() = findControllerFile() != null
    override fun canNavigateToSource() = canNavigate()
    override fun navigate(requestFocus: Boolean) {
        findControllerFile()?.let { OpenFileDescriptor(project, it, 0).navigate(requestFocus) }
    }

    private fun findControllerFile(): VirtualFile? {
        val relPath = "app/controllers/${controllerPath}_controller.rb"
        for (root in ProjectRootManager.getInstance(project).contentRoots) {
            root.findFileByRelativePath(relPath)?.takeIf { !it.isDirectory }?.let { return it }
        }
        return null
    }
}

class RouteEntryNode(
    project: Project,
    private val entry: RouteEntry,
    private val routesFile: VirtualFile,
    viewSettings: ViewSettings,
    private val sortIndex: Int = 0,
) : ProjectViewNode<String>(project, "${entry.verb} ${entry.path}", viewSettings) {

    override fun getSortKey(): Comparable<*> = "%06d".format(sortIndex)
    override fun contains(file: VirtualFile): Boolean = false
    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun update(presentation: PresentationData) {
        presentation.setIcon(AllIcons.Nodes.Method)
        presentation.addText(entry.verb, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, verbColor(entry.verb)))
        presentation.addText("  ${entry.path}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.addText("  → ${entry.action}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    override fun canNavigate() = true
    override fun canNavigateToSource() = true
    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(project, routesFile, entry.charOffset).navigate(requestFocus)
    }

    companion object {
        private fun verbColor(verb: String): Color = when (verb) {
            "GET"           -> JBColor(Color(0x007700), Color(0x6ABF69))
            "POST"          -> JBColor(Color(0x000099), Color(0x6897BB))
            "PATCH", "PUT"  -> JBColor(Color(0x885500), Color(0xCC7832))
            "DELETE"        -> JBColor(Color(0x990000), Color(0xFF6B68))
            else            -> JBColor.GRAY
        }
    }
}
