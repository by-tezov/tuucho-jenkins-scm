def call(
        String sourceBranch,
        String repositoryFullName = env.GITHUB_TUUCHO_REPOSITORY,
        String repositoryOrganization = env.GITHUB_ORGANIZATION,
        String credentialsId = env.GITHUB_API_TOKEN_ID
) {
    def response = null
    withCredentials([string(credentialsId: credentialsId, variable: 'GITHUB_TOKEN')]) {
        response = httpRequest(
                url: "https://api.github.com/repos/${repositoryFullName}/pulls?head=${repositoryOrganization}:${sourceBranch}",
                timeout: env.GITHUB_API_REQUEST_TIMEOUT.toInteger(),
                httpMode: 'GET',
                customHeaders: [
                        [name: 'User-Agent', value: 'Jenkins'],
                        [name: 'Accept', value: "application/json"],
                        [name: 'Authorization', value: 'Bearer ${GITHUB_TOKEN}']
                ],
                validResponseCodes: '200'
        )
    }
    def jsonResponseContent = readJSON text: response.content
    if (jsonResponseContent.size() == 0) {
        error("No pull request found for source branch ${sourceBranch}")
    }
    if (jsonResponseContent.size() > 1) {
        error("Multiple pull requests found, source ${sourceBranch} can have only one pull request...")
    }
    return jsonResponseContent[0]
}