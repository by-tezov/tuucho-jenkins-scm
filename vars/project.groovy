def path(
        String agent = env.AGENT,
        String jobName = env.JOB_NAME,
        String buildNumber = env.BUILD_NUMBER
) {
    return "${workspace.path(agent)}/${jobName}/_${buildNumber}/project"
}

def applicationLocation(
        String platform,
        String buildType,
        String jobName,
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
                    error("project.applicationLocation: unknown environment: ${buildType}")
            }
            def ANDROID_BUILD_APP_FILE_PATH = "app/android/build/outputs/apk/${environmentToken}"
            return [
                    path: "${path(constant.agent.android_builder, jobName, buildNumber)}/${ANDROID_BUILD_APP_FILE_PATH}",
                    file: "android-${buildType}.apk"
            ]
            break

        case constant.platform.ios:
            def (_, variant) = platform.contains('-') ? platform.split('-', 2) : [platform, null]
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
                        error("project.applicationLocation: unknown environment: ${buildType}")
                }
                def IOS_BUILD_APP_FILE_PATH = "app/ios/build/Build/Products/${environmentToken}-iphonesimulator"
                return [
                        path: "${path(constant.agent.ios_builder, jobName, buildNumber)}/${IOS_BUILD_APP_FILE_PATH}",
                        file: "ios.app"
                ]
            }

            if (variant == 'ipa') {
                def IOS_ARCHIVE_IPA_FILE_PATH = 'archive'
                return [
                        path: "${path(constant.agent.ios_builder, jobName, buildNumber)}/${IOS_ARCHIVE_IPA_FILE_PATH}",
                        file: "tuucho_ios.ipa"
                ]
            }
            break

        default:
            error("project.applicationLocation: unknown platform: ${platform}")
    }
}
