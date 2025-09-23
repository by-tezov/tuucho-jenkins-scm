def call(
        String buildType
) {
    switch (buildType) {
        case constant.buildType.mock:
            addBadge(icon: 'symbol-construct plugin-ionicons-api', text: 'mock')
            break
        case constant.buildType.dev:
            addBadge(icon: 'symbol-rocket plugin-ionicons-api', text: 'dev')
            break
        default:
            error("addBuildTypeBadge: unknown buildType: ${buildType}")
    }
}