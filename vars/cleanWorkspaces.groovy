def call(
    String agentPath,
    String pipeline,
    Integer maxSubWorkspaces = env.CLEAN_WORKSPACE_MAX_SUB_WORKSPACES.toInteger()
) {
    timeout(time: 5, unit: 'MINUTES') {
        def workspace = "${agentPath}/workspace/${pipeline}"
        def workspaceTempPreFix = '@tmp'

        def workspaces = sh(
                script: "find ${workspace} -mindepth 1 -maxdepth 1 -type d -name '_*' ! -name '*${workspaceTempPreFix}'",
                returnStdout: true
        ).trim().split('\n')
        def workspacesSorted = sortWorkspacesPath(workspaces)

        def toDeleteCount = workspacesSorted.size() - maxSubWorkspaces
        if (toDeleteCount >= 1) {
            for (int i = 0; i < toDeleteCount; i++) {
                def workspaceToDelete = workspacesSorted[i]
                def tempWorkspaceToDelete = "${workspaceToDelete}${workspaceTempPreFix}"
                log.info "Deleting sub-workspace: ${workspaceToDelete} and ${workspaceTempPreFix}"
                sh "rm -rf ${workspaceToDelete}"
                sh "rm -rf ${tempWorkspaceToDelete}"
            }
        }
    }
}

@NonCPS
private static def sortWorkspacesPath(workspaces) {
    return workspaces.sort { value ->
        (value =~ /_(\d+)$/)[0][1].toInteger()
    }
}