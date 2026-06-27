# Rails View — RubyMine Plugin

> **By [Wasabi Elements GmbH](https://susshi.io) · [susshi.io](https://susshi.io)**

The plugin adds a dedicated **Rails View** tab to the Project tool window that organises
your files the way Rails developers think — by function, not by raw directory structure.
Ruby files are shown with their **class names** instead of raw filenames wherever possible.

![rails-view-tree](media/rails-view-tree-open.png)

---

## What you get

```
▼ my-project
  │
  ├── Controllers     (app/controllers)
  │   └── PostsController
  │       ├── PostsHelper                    ← matching helper (expandable)
  │       ├── ▼ Concerns
  │       │   └── Authenticatable
  │       ├── ▼ Partials                     ← _form.html.erb, _card.html.erb …
  │       ├── ▼ Actions
  │       │   ├── index
  │       │   │   └── index.html.erb
  │       │   └── show
  │       │       └── show.html.erb
  │       └── ▼ Private Methods
  │           └── set_post
  │
  ├── Routes          (config/routes.rb)
  │   ├── ▼ api
  │   │   └── ▼ v1
  │   │       └── ▼ users
  │   │           ├── GET    /api/v1/users       → index
  │   │           ├── POST   /api/v1/users       → create
  │   │           ├── GET    /api/v1/users/:id   → show
  │   │           └── DELETE /api/v1/users/:id   → destroy
  │   └── ▼ errors
  │       ├── ALL  /403  → forbidden
  │       ├── ALL  /404  → not_found
  │       └── ALL  /500  → internal_server_error
  │
  ├── Assets, JavaScript …
  │
  ├── Models          (app/models)
  │   └── User
  │       ├── ▼ Concerns
  │       │   └── Searchable
  │       ├── ▼ Schema
  │       │   ├── id :bigint
  │       │   ├── email :string
  │       │   └── created_at :datetime
  │       ├── ▼ Associations
  │       │   ├── has_many :posts
  │       │   └── belongs_to :organisation
  │       ├── ▼ Scopes
  │       │   └── scope :active
  │       ├── ▼ Attributes
  │       │   ├── attr_accessor :display_name
  │       │   └── ▼ typed_store :settings
  │       │       ├── theme :string
  │       │       └── locale :string
  │       ├── ▼ Class Methods
  │       │   └── find_by_token
  │       ├── ▼ Instance Methods
  │       │   └── full_name
  │       └── ▼ Private Methods
  │           └── normalize_email
  │
  ├── Database        (db/)
  │   ├── schema.rb
  │   └── migrate/                           ← sorted newest-first
  │       ├── 2026-03-17-113427 CreatePosts  ← hover for full filename
  │       └── 2026-01-05-090000 CreateUsers
  │
  ├── Mailers, GraphQL, Helpers, Views, Channels, Decorators,
  │   Jobs, Policies, Uploaders, Serializers, Services,
  │   Config, Lib, Spec, Test …
  │
  ├── Project Files
  │   ├── bin/                               ← unclaimed root directories
  │   ├── Gemfile
  │   └── …
  │
  ├── External Libraries                     ← installed gems + Ruby SDK
  │   ├── bundler 2.5.9
  │   ├── rails 7.2.1
  │   └── …
  │
  └── Scratches and Consoles                 ← IDE scratch files
      ├── Scratches/
      └── Consoles/
```

Sections only appear when the corresponding directory (or file) exists in the project.
Schema columns, macros, concerns, and methods are grouped into folders by default; each
group can be toggled off in **Settings → Tools → Rails View**.

---

## Features

- **Class names** — `.rb` files show their Ruby class/module name (`PostsController`, `User`) instead of the filename. Falls back to PascalCase conversion when the PSI index is not yet ready.
- **Routes section** — parses `config/routes.rb` and displays all routes grouped by controller. Supports `get`/`post`/`patch`/`put`/`delete`/`head`, `root`, `resources`/`resource` (with `only:`, `except:`, `controller:` options), `namespace` blocks, `match` (including hash-rocket `:to =>` and `:via =>` syntax), and `concern` definitions.
- **Nested Routes view** — routes are shown as a folder hierarchy that mirrors the controller path (`api → v1 → users`), making it easy to navigate namespaced APIs. Switch to a flat list in settings.
- **Concerns** — `include`, `extend`, and `prepend` calls are shown as a grouped **Concerns** node under both model and controller nodes.
- **Controller → Views link** — each controller node expands to show its matching `app/views/<name>/` directory, including namespaced controllers (`Admin::UsersController` → `app/views/admin/users/`).
- **VCS status colours** — modified files appear in orange, unversioned files in red, matching IntelliJ's own file status colours.
- **Migration formatting** — `20260317113427_create_posts.rb` is displayed as `2026-03-17-113427 CreatePosts`, sorted newest-first. Hover to see the full raw filename.
- **External Libraries node** — shows all installed gems and the Ruby SDK, identical to the node in RubyMine's built-in Project view. Position it anywhere in the section order.
- **Scratches and Consoles node** — shows the IDE's scratch files and console history, identical to the standard Project view node.
- **Project Files catch-all** — shows curated root files (Gemfile, Rakefile, .env, Dockerfile …) plus any root-level directories not already covered by a dedicated section.
- **Configurable section order** — use the up/down arrows in **Settings → Rails View** to reorder sections, or commit a `.railsview` file so the whole team shares the same layout. Every node — including Project Files, External Libraries, and Scratches — participates in the order.
- **Team-wide hide via `.railsview`** — prefix any key with `!` to hide that section for everyone who has the file: `!spec` hides the Spec section even if it's enabled in personal Settings.

---

## Configuring section order

### Via Settings

Open **Settings → Tools → Rails View** and use the up/down arrows in the **Section Order** list to reorder sections. Changes are saved immediately and apply when no `.railsview` file is present in the project.

### Per-project: `.railsview`

Create a `.railsview` file in your project root (next to `Gemfile`) to pin the order for everyone on the team. When the file is present it takes full precedence — the GUI order is ignored entirely.

One key per line; `#` starts a comment. Prefix a key with `!` to **hide** that section
for the whole team (overrides each developer's personal Settings):

```
# .railsview — Rails View section order for this project
models
controllers
services
views
routes
graphql
database
jobs
mailers
helpers
!spec
!scratches
```

Sections listed in the file appear first in that order. Sections you omit are appended
after in the default order. Sections whose directory (or file) doesn't exist are silently skipped.
`!key` lines hide a section entirely, regardless of personal Settings.

**Available keys:**
`models` · `controllers` · `views` · `helpers` · `mailers` · `jobs` · `services` ·
`channels` · `uploaders` · `policies` · `serializers` · `decorators` · `assets` ·
`javascript` · `graphql` · `routes` · `config` · `database` · `lib` · `spec` · `test` ·
`project_files` · `external_files` · `scratches`

---

## Settings

**Settings → Tools → Rails View**

**Display Section Options**

| Option | Default | Description |
|---|---|---|
| Show Routes section | ✓ | Show the Routes section backed by `config/routes.rb` |
| Show Test sections (spec/, test/) | ✓ | Show `spec/` and `test/` in the tree |
| Show Project Files node | ✓ | Show Gemfile, Rakefile, unclaimed directories, etc. |
| Show External Libraries node | — | Show installed gems and the Ruby SDK |
| Show Scratches and Consoles node | — | Show IDE scratch files and console history |

**Section Behaviour Options**

| Option | Default | Description |
|---|---|---|
| Group Views under each controller | ✓ | Adds a `Views › posts` shortcut under each controller |
| Group Methods by scope | ✓ | Organises methods into Class Methods / Instance Methods / Private Methods |
| Group Model Declarations by type | ✓ | Organises model children into Schema / Associations / Scopes / Attributes |
| Nest Routes by path hierarchy | ✓ | Display routes as a folder tree (`api → v1 → …`) instead of a flat controller list |
