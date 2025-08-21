def call(
        String flavorType
) {
    switch (flavorType) {
        case constant.flavorType.mock:
            //TODO
            break
        case constant.flavorType.prod:
            //TODO
            break
        default:
            error("Unknown flavorType: ${flavorType}")
    }
}