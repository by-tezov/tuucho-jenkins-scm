def path(
        String agent = env.AGENT
) {
    def agentPaths = [
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