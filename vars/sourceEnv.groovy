def call(Closure body) {
    List<String> envVars = []
    switch (env.JOB_NAME) {
        case 'ios/build':
            def zshrcPath = "${env.HOME}/${env.CICD_FOLDER}/builder/.zshrc"
            envVars = readZshrcEnv(zshrcPath)
            break

        default:
            error("sourceEnv: nothing was source of ${env.JOB_NAME}")
    }
    withEnv(envVars) { body() }
}

private List<String> readZshrcEnv(String zshrcPath) {
    def envOutput = sh(
            script: """
                #!/bin/bash
                source '${zshrcPath}'
                while IFS='=' read -r key _; do
                  [ -n "\$key" ] && echo "\$key=\${!key}"
                done < '${zshrcPath}' | awk -F= '!seen[\$1]++'
            """,
            returnStdout: true
    ).trim()
    return envOutput.readLines()
}