def call() {
    return "${getWorkspaceFolderPath()}/${env.JOB_NAME}/_${env.BUILD_NUMBER}/project"
}