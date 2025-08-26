def call(
        String sha,
        String context,
        String status,
        String description,
        String repositoryFullName = env.GITHUB_TUUCHO_REPOSITORY
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