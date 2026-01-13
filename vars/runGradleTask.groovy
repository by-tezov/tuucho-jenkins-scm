def call(
        String folder,
        String task,
        Map params = null
) {
    def paramString = params?.collect { key, value -> "-P${key}='" + value + "'" }?.join(' ') ?: ''
    dir(folder) {
        sh "./gradlew ${task} ${paramString} --no-daemon"
    }
}