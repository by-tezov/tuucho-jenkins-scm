def call(
        String task,
        String folderPath = null,
        Map params = null
) {
    def paramString = params?.collect { key, value -> "-P${key}=${value}" }?.join(' ') ?: ''
    if (folderPath) {
        dir(folderPath) {
            sh "./gradlew ${task} ${paramString} --no-daemon"
        }
    } else {
        sh "./gradlew ${task} ${paramString} --no-daemon"
    }
}