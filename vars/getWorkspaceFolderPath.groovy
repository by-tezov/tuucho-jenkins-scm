def call(
        String agent = env.AGENT
) {
    def agentPaths = [
            (constant.agent.android): env.AGENT_AN_BUILDER_PATH,
            (constant.agent.ios)    : env.AGENT_IOS_BUILDER_PATH,
            (constant.agent.qa)     : env.AGENT_QA_PATH
    ]
    def agentPath = agentPaths[agent]
    if (!agentPath) {
        error("getWorkspaceFolderPath: unknown platform: ${agent}")
    }
    return "${agentPath}/workspace"
}