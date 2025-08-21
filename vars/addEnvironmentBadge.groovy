def call(
    String environment
) {
    switch (environment) {
        case 'debug':
            addBadge(icon: 'symbol-build plugin-ionicons-api', text: 'debug')
            break
        case 'production':
            addBadge(icon: 'symbol-car-sport plugin-ionicons-api', text: 'production')
            break
        default:
            error("Unknown environment: ${environment}")
    }
}