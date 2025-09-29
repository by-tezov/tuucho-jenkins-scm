def call(
        String agent = env.AGENT,
        String jobName = env.JOB_NAME,
        String buildNumber = env.BUILD_NUMBER
) {
    def libsFile = "${project.path(agent, jobName, buildNumber)}/libs.versions.toml"
    def libsContent = readFile libsFile
    def matcher = libsContent =~ /(?m)^(?!#)\s*isSnapshot\s*=\s*["'](.+)["']\s*$/
    if (!matcher) {
        error("getIsSnapshot: no 'versionName' found in ${libsFile}.")
    }
    def version = matcher[0][1]
    return version
}