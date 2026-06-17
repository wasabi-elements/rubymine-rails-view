package io.susshi.railsview.nodes

/**
 * Identifies which logical Rails section a node belongs to.
 * Used to pick the right icon and apply section-specific child logic.
 */
enum class SectionKind {
    MODELS,
    CONTROLLERS,
    VIEWS,
    HELPERS,
    MAILERS,
    JOBS,
    SERVICES,
    CHANNELS,
    UPLOADERS,
    POLICIES,
    SERIALIZERS,
    DECORATORS,
    CONCERNS,
    ASSETS,
    JAVASCRIPT,
    CONFIG,
    DATABASE,
    LIB,
    SPEC,
    TEST,
    GRAPHQL,
    PROJECT_FILES,
    ;

    val icon get() = icons.RailsViewIcons.forSection(this)
}
