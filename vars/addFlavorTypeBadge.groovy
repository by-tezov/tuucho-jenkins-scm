def call(
        String flavorType
) {
    switch (flavorType) {
        case constant.flavorType.mock:
            addBadge(icon: 'symbol-cloud-offline plugin-ionicons-api', text: 'mock')
            break
        case constant.flavorType.prod:
            addBadge(icon: 'symbol-globe plugin-ionicons-api', text: 'prod')
            break
        default:
            error("addFlavorTypeBadge: unknown flavorType: ${flavorType}")
    }
}