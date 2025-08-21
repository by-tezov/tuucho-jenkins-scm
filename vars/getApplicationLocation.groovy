def call(
    String environment,
    String platform,
    String pipeline,
    String buildNumber
) {
    // ANDROID
    if (platform == 'android') {
        def environmentToken = null
        switch (environment) {
            case 'debug':
                environmentToken = 'debug'
                break
            case 'production':
                environmentToken = 'release'
                break
            default:
                error("Unknown environment: ${environment}")
        }

        def ANDROID_BUILD_APP_FILE_PATH = "app/build/outputs/apk/real/${environmentToken}"
        return [
            path: "${getProjectFolderPath(platform, pipeline, buildNumber)}/${ANDROID_BUILD_APP_FILE_PATH}",
            file: "app-real-${environmentToken}.apk"
        ]
    }

    // IOS
    if (platform.startsWith('ios')) {
        def (target, variant) = platform.contains('-') ? platform.split('-', 2) : [platform, null]

        if(variant == null) {
            def environmentToken = null
            switch (environment) {
                case 'debug':
                    environmentToken = 'debug'
                    break
                case 'production':
                    environmentToken = 'Release'
                    break
                default:
                    error("Unknown environment: ${environment}")
            }

            def IOS_BUILD_APP_FILE_PATH = "build/Build/Products/${environmentToken}-iphoneos"
            return [
                    path: "${getProjectFolderPath(target, pipeline, buildNumber)}/${IOS_BUILD_APP_FILE_PATH}",
                    file: "tezov_ios.app"
            ]
        }

        if(variant == 'ipa') {
            def IOS_ARCHIVE_IPA_FILE_PATH = 'archive'
            return [
                    path: "${getProjectFolderPath(target, pipeline, buildNumber)}/${IOS_ARCHIVE_IPA_FILE_PATH}",
                    file: "tezov_ios.ipa"
            ]
        }
    }

    error("Unknown platform: ${platform}")
}