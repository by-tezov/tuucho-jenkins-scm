def call() {
    def libsFile = "${project.path()}/gradle/libs.versions.toml"
    def libsContent = readFile libsFile
    def matcher = libsContent =~ /(?m)^(?!#)\s*versionName\s*=\s*["'](.+)["']\s*$/
    if (!matcher) {
        error("getMarketingVersion: no 'versionName' found in ${libsFile}.")
    }
    def version = matcher[0][1]
    return version
}