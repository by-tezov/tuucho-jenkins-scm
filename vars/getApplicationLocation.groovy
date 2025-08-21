def call(
        String buildType,
        String platform,
        String pipeline,
        String buildNumber
) {
    switch (platform) {
        case constant.platform.android:
            def environmentToken = null
            switch (buildType) {
                case constant.buildType.debug:
                    environmentToken = 'debug'
                    break
                case constant.buildType.release:
                    environmentToken = 'release'
                    break
                default:
                    error("Unknown environment: ${buildType}")
            }
            def ANDROID_BUILD_APP_FILE_PATH = "app/build/outputs/apk/real/${environmentToken}"
            return [
                    path: "${getProjectFolderPath(platform, pipeline, buildNumber)}/${ANDROID_BUILD_APP_FILE_PATH}",
                    file: "app-real-${environmentToken}.apk"
            ]
            break

        case constant.platform.ios:
            def (target, variant) = platform.contains('-') ? platform.split('-', 2) : [platform, null]
            if (variant == null) {
                def environmentToken = null
                switch (buildType) {
                    case constant.buildType.debug:
                        environmentToken = 'Debug'
                        break
                    case constant.buildType.release:
                        environmentToken = 'Release'
                        break
                    default:
                        error("Unknown environment: ${buildType}")
                }
                def IOS_BUILD_APP_FILE_PATH = "build/Build/Products/${environmentToken}-iphoneos"
                return [
                        path: "${getProjectFolderPath(target, pipeline, buildNumber)}/${IOS_BUILD_APP_FILE_PATH}",
                        file: "tuucho_ios.app"
                ]
            }

            if (variant == 'ipa') {
                def IOS_ARCHIVE_IPA_FILE_PATH = 'archive'
                return [
                        path: "${getProjectFolderPath(target, pipeline, buildNumber)}/${IOS_ARCHIVE_IPA_FILE_PATH}",
                        file: "tezov_ios.ipa"
                ]
            }
            break

        default:
            error("Unknown platform: ${platform}")
    }
}