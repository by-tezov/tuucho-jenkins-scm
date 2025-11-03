def call(
        String sha,
        String context,
        String status,
        String description,
        String repositoryFullName = "${env.GITHUB_ORGANIZATION}/${env.GITHUB_TUUCHO}",
        String credentialsId = env.GITHUB_API_TOKEN_ID
) {
    if (!sha) {
        log.warning "Skipping GitHub status update because SHA is empty"
        return
    }
    log.info "$context: $status</br>$description"
    def requestBody = """{
        "state": "${status}",
        "context": "${context}",
        "description": "${description}"       
    }"""
    withCredentials([string(credentialsId: credentialsId, variable: 'GITHUB_TOKEN')]) {
        httpRequest(
                url: "https://api.github.com/repos/${repositoryFullName}/statuses/${sha}",
                httpMode: 'POST',
                contentType: 'APPLICATION_JSON',
                requestBody: requestBody,
                customHeaders: [
                        [name: 'User-Agent', value: 'Jenkins'],
                        [name: 'Authorization', value: 'Bearer ' + GITHUB_TOKEN],
                        [name: 'X-GitHub-Api-Version', value: '2022-11-28']
                ],
                validResponseCodes: '201'
        )
    }
}