def call(
        String task,
        Map params = null,
        String folder = 'project'
) {
    def paramString = params?.collect { key, value -> "-P${key}='${value}'" }?.join(' ') ?: ''
    if (folder) {
        dir(folder) {
            sh "./gradlew ${task} ${paramString} --no-daemon"
        }
    } else {
        sh "./gradlew ${task} ${paramString} --no-daemon"
    }
}