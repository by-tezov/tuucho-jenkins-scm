def path(
        String agent = env.AGENT
) {
    def agentPaths = [
            (constant.agent.repository)     : env.AGENT_REPOSITORY_PATH,
            (constant.agent.android_danger) : env.AGENT_AN_DANGER_PATH,
            (constant.agent.android_builder): env.AGENT_AN_BUILDER_PATH,
            (constant.agent.android_qa)     : env.AGENT_AN_QA_PATH,
            (constant.agent.ios_builder)    : env.AGENT_IOS_BUILDER_PATH,
            (constant.agent.ios_qa)         : env.AGENT_IOS_QA_PATH,
    ]
    def agentPath = agentPaths[agent]
    if (!agentPath) {
        error("getWorkspacePath: unknown platform: ${agent}")
    }
    return "${agentPath}/workspace"
}

def clean(
        String agent = env.AGENT,
        String jobName = env.JOB_NAME,
        Integer maxSubWorkspaces = env.CLEAN_WORKSPACE_MAX_SUB_WORKSPACES.toInteger()
) {
    timeout(time: 5, unit: 'MINUTES') {
        def workspace = "${path(agent)}/${jobName}"
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