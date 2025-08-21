def call() {
    def agentPaths = [
            (constant.agent.android): env.AGENT_AN_BUILDER_PATH,
            (constant.agent.ios)    : env.AGENT_IOS_BUILDER_PATH,
            (constant.agent.qa)     : env.AGENT_QA_PATH
    ]
    def agentPath = agentPaths[env.AGENT]
    if (!agentPath) {
        error("Unknown platform: ${env.AGENT}")
    }
    return "${agentPath}/workspace"
}