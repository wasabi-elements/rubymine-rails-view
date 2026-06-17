package io.susshi.railsview.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.SimpleTextAttributes

/**
 * Represents a single column-like field: either a database column from db/schema.rb,
 * or a typed_store attribute defined inline in a model file. The schemaFile parameter
 * points to whichever source file to navigate to on click, and charOffset is the
 * character position within that file.
 *
 * Stores "name:type" (String) so TreeAnchorizer does NOT wrap → mayContain() = true.
 */
class SchemaColumnNode(
    project: Project,
    private val schemaFile: VirtualFile,
    private val columnName: String,
    private val columnType: String,
    private val charOffset: Int,
    viewSettings: ViewSettings,
    private val sortIndex: Int = 0,
) : ProjectViewNode<String>(project, "$columnName:$columnType", viewSettings) {

    override fun getSortKey(): Comparable<*> = "%06d".format(sortIndex)

    override fun contains(file: VirtualFile): Boolean = false
    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun update(presentation: PresentationData) {
        presentation.setIcon(AllIcons.Nodes.Field)
        presentation.addText(columnName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.addText(" :$columnType", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true
    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(myProject, schemaFile, charOffset).navigate(requestFocus)
    }

    // -------------------------------------------------------------------------
    companion object {

        private val COLUMN_TYPES = setOf(
            "string", "integer", "bigint", "text", "float", "decimal",
            "boolean", "date", "datetime", "time", "binary",
            "json", "jsonb", "hstore", "citext", "uuid",
            "references", "attachment", "virtual"
        )

        data class ColumnInfo(val name: String, val type: String, val charOffset: Int)

        /**
         * Returns (schemaFile, columns) for the model with the given filename, or null if
         * db/schema.rb doesn't exist or the table can't be found.
         */
        fun columnsForModel(
            project: Project,
            modelFileName: String,
        ): Pair<VirtualFile, List<ColumnInfo>>? {
            val tableName = tableNameFor(modelFileName) ?: return null
            val schemaFile = findSchemaFile(project) ?: return null
            val text = try {
                VfsUtilCore.loadText(schemaFile)
            } catch (_: Exception) {
                return null
            }
            val columns = parseColumns(text, tableName)
            return if (columns.isEmpty()) null else Pair(schemaFile, columns)
        }

        private fun parseColumns(text: String, tableName: String): List<ColumnInfo> {
            val colTypes = COLUMN_TYPES.joinToString("|") { Regex.escape(it) }
            val colPattern = Regex("""^\s+t\.($colTypes)\s+"(\w+)"""")
            val result = mutableListOf<ColumnInfo>()

            val lines = text.lines()
            var inTable = false
            var tableIndent = 0
            var charOffset = 0

            for (line in lines) {
                val lineLen = line.length + 1  // +1 for newline
                val trimmed = line.trimStart()

                if (!inTable) {
                    if (trimmed.startsWith("create_table") && line.contains(""""$tableName"""")) {
                        inTable = true
                        tableIndent = line.length - trimmed.length
                    }
                } else {
                    // A non-blank line at same or lesser indentation signals end of block
                    if (trimmed.isNotEmpty()) {
                        val lineIndent = line.length - trimmed.length
                        if (lineIndent <= tableIndent && trimmed.startsWith("end")) {
                            break
                        }
                        // Column definition: t.<type> "<name>"
                        val m = colPattern.find(line)
                        if (m != null) {
                            result.add(ColumnInfo(
                                name = m.groupValues[2],
                                type = m.groupValues[1],
                                charOffset = charOffset + m.range.first,
                            ))
                        }
                    }
                }
                charOffset += lineLen
            }
            return result
        }

        // Simple Rails-like pluralization for model file names (snake_case, singular)
        private fun tableNameFor(modelFileName: String): String? {
            if (!modelFileName.endsWith(".rb")) return null
            return pluralize(modelFileName.removeSuffix(".rb"))
        }

        private fun pluralize(word: String): String {
            if (word.isEmpty()) return word
            val irregular = mapOf(
                "person" to "people",
                "man" to "men",
                "woman" to "women",
                "child" to "children",
                "tooth" to "teeth",
                "foot" to "feet",
                "mouse" to "mice",
                "goose" to "geese",
                "ox" to "oxen",
                "leaf" to "leaves",
                "life" to "lives",
                "knife" to "knives",
                "wife" to "wives",
                "half" to "halves",
                "loaf" to "loaves",
            )
            // Handle compound words (e.g. "super_person" → "super_people")
            for ((singular, plural) in irregular) {
                if (word == singular) return plural
                if (word.endsWith("_$singular")) return word.dropLast(singular.length) + plural
            }
            return when {
                word.endsWith("quiz")                -> word + "zes"
                word.endsWith("ix") || word.endsWith("ex") -> word.dropLast(2) + "ices"
                word.endsWith("x") || word.endsWith("ch") ||
                    word.endsWith("sh") || word.endsWith("ss") -> word + "es"
                word.endsWith("fe")                  -> word.dropLast(2) + "ves"
                word.endsWith("y") && word.length > 1 &&
                    word[word.length - 2].lowercaseChar() !in "aeiou" -> word.dropLast(1) + "ies"
                else                                 -> word + "s"
            }
        }

        private fun findSchemaFile(project: Project): VirtualFile? {
            for (root in ProjectRootManager.getInstance(project).contentRoots) {
                root.findFileByRelativePath("db/schema.rb")
                    ?.takeIf { !it.isDirectory }
                    ?.let { return it }
            }
            return null
        }
    }
}
