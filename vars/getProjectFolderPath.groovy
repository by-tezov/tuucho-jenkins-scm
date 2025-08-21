def call(
    String agent,
    String pipeline,
    String buildNumber
) {
    switch (agent) {
        case 'android':
            return "${env.AGENT_PATH_AN}/workspace/${agent}/${pipeline}/_${buildNumber}/project"
        case 'ios':
            return "${env.AGENT_PATH_IOS}/workspace/${agent}/${pipeline}/_${buildNumber}/project"
        case 'qa':
            return "${env.AGENT_PATH_QA}/workspace/${agent}/${pipeline}/_${buildNumber}/project"
        default:
            error("Unknown platform: ${agent}")
    }
}