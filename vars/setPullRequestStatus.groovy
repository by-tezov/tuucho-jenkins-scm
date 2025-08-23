def call(
        String sha,
        String status,
        String context,
        String description,
        String repositoryFullName = env.GITHUB_TUUCHO_REPOSITORY
) {
    def requestBody = """{
        "state": "${status}",
        "context": "${context}",
        "description": "${description}"       
    }"""
    httpRequest(
            url: "https://api.github.com/repos/${repositoryFullName}/statuses/${sha}",
            httpMode: 'POST',
            contentType: 'APPLICATION_JSON',
            requestBody: requestBody,
            customHeaders: [
                    [name: 'Authorization', value: "Bearer ${env.GITHUB_API_TOKEN}"]
            ],
            validResponseCodes: '201'
    )
}