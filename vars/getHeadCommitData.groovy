def call(
        String commitSha,
        String repositoryFullName = env.GITHUB_TUUCHO_REPOSITORY,
        String credentialsId = env.GITHUB_API_TOKEN_ID
) {
    def response = null
    withCredentials([string(credentialsId: credentialsId, variable: 'GITHUB_TOKEN')]) {
        response = httpRequest(
                url: "https://api.github.com/repos/${repositoryFullName}/commits?sha=${commitSha}&per_page=1&page=1",
                timeout: env.GITHUB_API_REQUEST_TIMEOUT.toInteger(),
                httpMode: 'GET',
                customHeaders: [
                        [name: 'User-Agent', value: 'Jenkins'],
                        [name: 'Accept', value: "application/json"],
                        [name: 'Authorization', value: 'Bearer ' + GITHUB_TOKEN]
                ],
                validResponseCodes: '200'
        )
    }
    def jsonResponseContent = readJSON text: response.content
    if (jsonResponseContent.size() <= 0) {
        error("No commit data found for commit ${commitSha}")
    }
    return jsonResponseContent[0]
}