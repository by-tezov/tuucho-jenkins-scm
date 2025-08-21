def call(
        String platform
) {
    switch (platform) {
        case constant.platform.android:
            addBadge(icon: 'symbol-logo-android plugin-ionicons-api', text: 'android')
            break
        case constant.platform.ios:
            addBadge(icon: 'symbol-logo-apple plugin-ionicons-api', text: 'ios')
            break
        default:
            error("Unknown platform: ${platform}")
    }
}