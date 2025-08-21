def call(
        String flavor
) {
    def libsFile = "${getProjectFolderPath()}/gradle/libs.versions.toml"
    def libsContent = readFile libsFile
    def matcher = libsContent =~ /(?m)^(?!#)flavor\s*=.*$/
    if (!matcher) {
        error("No active 'flavor' line found in ${libsFile}, cannot replace.")
    }
    def newContent = libsContent.replaceFirst(/(?m)^(?!#)flavor\s*=.*$/, "flavor = \"${flavor}\"")
    writeFile file: libsFile, text: newContent
    echo "Flavor updated to: ${flavor}"
}