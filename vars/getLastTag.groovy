def call(
        String repositoryFullName = env.GITHUB_TUUCHO_REPOSITORY,
        String credentialsId = env.GITHUB_API_TOKEN_ID
) {
    withCredentials([string(credentialsId: credentialsId, variable: 'GITHUB_TOKEN')]) {
        def response = httpRequest(
                url: "https://api.github.com/repos/${repositoryFullName}/releases?per_page=100",
                httpMode: 'GET',
                customHeaders: [
                        [name: 'User-Agent', value: 'Jenkins'],
                        [name: 'Authorization', value: "Bearer"+GITHUB_TOKEN],
                        [name: 'X-GitHub-Api-Version', value: '2022-11-28']
                ],
                validResponseCodes: '200'
        )

        def releases = readJSON text: response.content
        if (!releases || releases.isEmpty()) {
            log.info "No releases found in ${repositoryFullName}"
            return null
        }
        def latestTag = releases[0].tag_name
        echo "Latest release tag in ${repositoryFullName}: ${latestTag}"
        return latestTag
    }
}