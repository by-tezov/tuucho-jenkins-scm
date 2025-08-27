def call(
        String agent = env.AGENT,
        String jobName = env.JOB_NAME,
        Integer maxSubWorkspaces = env.CLEAN_WORKSPACE_MAX_SUB_WORKSPACES.toInteger()
) {
    timeout(time: 5, unit: 'MINUTES') {
        def workspace = "${workspace.path(agent)}/${jobName}"
        def workspaceTempPreFix = '@tmp'
        deleteSubWorkspaces(workspace, workspaceTempPreFix, maxSubWorkspaces)
    }
}

@NonCPS
private static def sortWorkspacesPath(workspaces) {
    return workspaces.sort { value ->
        (value =~ /_(\d+)$/)[0][1].toInteger()
    }
}

private def deleteSubWorkspaces(
        String workspace,
        String workspaceTempPreFix,
        Integer maxSubWorkspaces
) {
    def workspacesOutput = sh(
            script: "find ${workspace} -mindepth 1 -maxdepth 1 -type d -name '_*' ! -name '*${workspaceTempPreFix}'",
            returnStdout: true
    ).trim()
    if (!workspacesOutput) {
        return
    }
    def workspaces = workspacesOutput.split('\n')
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
