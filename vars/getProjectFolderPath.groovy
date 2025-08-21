def call(
        String agent,
        String pipeline,
        String buildNumber
) {
    switch (agent) {
        case constant.agent.android:
            return "${env.AGENT_PATH_AN}/workspace/${agent}/${pipeline}/_${buildNumber}/project"
        case constant.agent.ios:
            return "${env.AGENT_PATH_IOS}/workspace/${agent}/${pipeline}/_${buildNumber}/project"
        case constant.agent.qa:
            return "${env.AGENT_PATH_QA}/workspace/${agent}/${pipeline}/_${buildNumber}/project"
        default:
            error("Unknown platform: ${agent}")
    }
}