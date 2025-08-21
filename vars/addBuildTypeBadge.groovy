def call(
        String buildType
) {
    switch (buildType) {
        case constant.buildType.debug:
            addBadge(icon: 'symbol-construct plugin-ionicons-api', text: 'debug')
            break
        case constant.buildType.release:
            addBadge(icon: 'symbol-rocket plugin-ionicons-api', text: 'release')
            break
        default:
            error("Unknown buildType: ${buildType}")
    }
}