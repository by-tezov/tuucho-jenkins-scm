def call(
        String task,
        String gradleFolderPath = null,
        Map params = null
) {
    def paramString = params?.collect { key, value -> "-P${key}=${value}" }?.join(' ') ?: ''
    if (gradleFolderPath) {
        dir(gradleFolderPath) {
            sh "./gradlew ${task} ${paramString} --no-daemon"
        }
    } else {
        sh "./gradlew ${task} ${paramString} --no-daemon"
    }
}