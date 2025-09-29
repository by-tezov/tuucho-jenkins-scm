def call(Closure body) {
    List<String> envVars = []
    switch (env.JOB_NAME) {
        case 'ios/build':
            def zshrcPath = "${env.HOME}/${env.CICD_FOLDER}/builder/.zshrc"
            envVars = readZshrcEnv(zshrcPath)
            break
        case 'ios/e2e-test':
            def zshrcPath = "${env.HOME}/${env.CICD_FOLDER}/qa/.zshrc"
            envVars = readZshrcEnv(zshrcPath)
            break

        case 'maven-publish':
            def zshrcPath = "${env.HOME}/${env.CICD_FOLDER}/builder/.zshrc" //TODO replace by ios-publication when done
            envVars = readZshrcEnv(zshrcPath)
            break

        default:
            error("sourceEnv: nothing was source of ${env.JOB_NAME}")
    }
    withEnv(envVars) { body() }
}

private def readZshrcEnv(String zshrcPath) {
    def envOutput = sh(
            script: """
                set +x
                source '${zshrcPath}'
                while IFS='=' read -r key _; do
                  [ -n "\$key" ] && echo "\$key=\${!key}"
                done < '${zshrcPath}' | awk -F= '!seen[\$1]++'
                set -x
            """,
            returnStdout: true,
            label: ''
    ).trim()
    return envOutput.readLines()
}