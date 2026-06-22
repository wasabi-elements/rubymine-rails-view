# Changelog

## [Unreleased]

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
