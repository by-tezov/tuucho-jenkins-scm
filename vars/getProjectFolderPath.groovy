def call(
        String agent = env.AGENT,
        String jobName = env.JOB_NAME,
        String buildNumber = env.BUILD_NUMBER
) {
    return "${getWorkspaceFolderPath(agent)}/${jobName}/_${buildNumber}/project"
}