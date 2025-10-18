def call(
        String platform,
        String buildNumber = env.BUILD_NUMBER
) {
    return "${platform}-${buildNumber}"
}