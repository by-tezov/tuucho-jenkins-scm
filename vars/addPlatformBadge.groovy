def call(
    String platform
) {
    switch (platform) {
        case 'ios':
            addBadge(icon: 'symbol-logo-apple plugin-ionicons-api', text: 'ios')
            break
        case 'android':
            addBadge(icon: 'symbol-logo-android plugin-ionicons-api', text: 'android')
            break
        default:
            error("Unknown platform: ${platform}")
    }
}