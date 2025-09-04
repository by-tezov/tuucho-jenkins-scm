def call(
        String sha,
        String context,
        String status,
        String description,
        String repositoryFullName = env.GITHUB_TUUCHO_REPOSITORY,
        String credentialsId = env.GITHUB_API_TOKEN_ID
) {
    if (!sha) {
        echo "Skipping GitHub status update because SHA is empty"
        return
    }
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
                        [name: 'Authorization', value: 'Bearer ' + GITHUB_TOKEN]
                ],
                validResponseCodes: '201'
        )
    }
}