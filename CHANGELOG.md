# Changelog

## [Unreleased]

## [1.0.5] - 2026-06-27

### Added
- **External Libraries node**: Shows all installed gems and the Ruby SDK — identical to the node in RubyMine's built-in Project view. Opt-in via **Show External Libraries node** in Settings → Tools → Rails View.
- **Scratches and Consoles node**: Shows the IDE's scratch files and console history, mirroring the standard Project view node. Opt-in via **Show Scratches and Consoles node** in Settings → Tools → Rails View.
- **Project Files, External Libraries, and Scratches in section order**: All three nodes now participate in the section ordering list in Settings and in `.railsview` files (`project_files`, `external_files`, `scratches` keys).
- **Team-wide section hiding via `.railsview`**: Prefix any key with `!` (e.g. `!spec`, `!scratches`) to hide that section for the whole team, overriding each developer's personal Settings.

### Changed
- Settings panel reorganised into **Display Section Options** (show/hide toggles per section) and **Section Behaviour Options** (grouping and nesting toggles) for clarity.

## [1.0.4] - 2026-06-26

### Added
- **Routes section**: New dedicated section backed by `config/routes.rb`, slotted into the section order like any other section. Parses explicit verb routes (`get`, `post`, `patch`, `put`, `delete`, `head`) with both `to:` and `:to =>` syntax, `root`, `resources`/`resource` blocks (with `only:`, `except:`, `controller:` options), `namespace` blocks, `match` routes, and `concern` definitions with `concerns` usage.
- **Nested Routes view**: Routes can be displayed as a nested path hierarchy (e.g. `api → v1 → config → controller`) instead of a flat controller list — on by default. Toggle with **Nest routes by path hierarchy** in Settings → Rails View.
- **Show Routes section** toggle in Settings → Rails View.
- **Section ordering UI**: The Settings panel now has a orderable list for all sections, with a **Reset to Defaults** button. The chosen order is persisted across IDE restarts and applies when no `.railsview` file is present.
- **Concerns:** `include`, `extend`, and `prepend` calls are now shown as a grouped **Concerns** node under both model and controller nodes.
- **Migration tooltip**: Hovering over a migration node in the Database section shows the full raw filename (the formatted display name truncates it).
- **Schema caching**: `db/schema.rb` is now parsed once and cached by modification stamp — repeated tree expansions show schema columns immediately without re-reading the file.

### Changed
- `routes` added to the default section order (appears after controllers).

## [1.0.3] - 2026-06-22

### Added
- Model and controller class nodes now reflect VCS file status — modified files are shown in orange, unversioned/untracked files in red, matching the colors used by IntelliJ's own Changed Files view.

### Fixed
- View files with compound extensions (e.g. `show.html.haml`, `show.js.haml`, `show.json.jbuilder`) are now correctly grouped as children of their matching controller action.

## [1.0.2] - 2026-06-20

### Added
- Custom folders inside `app/` that Rails View doesn't know about (such as `app/data` or `app/lib`) now show up in **Project Files** under their full path, so nothing in your project gets hidden from view.

## [1.0.1] - 2026-06-19

### Fixed
- Restrict plugin to RubyMine by depending on `com.intellij.modules.ruby` instead of `org.jetbrains.plugins.ruby`, resolving a false compatibility warning against IntelliJ IDEA

## [1.0.0] - 2026-06-01

### Added
- Initial release
