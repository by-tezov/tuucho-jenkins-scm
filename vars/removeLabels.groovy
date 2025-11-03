def call(
        String number,
        List<String> labels,
        String repositoryFullName = "${env.GITHUB_ORGANIZATION}/${env.GITHUB_TUUCHO}",
        String credentialsId = env.GITHUB_API_TOKEN_ID
) {
    if (!number) {
        echo "Skipping GitHub remove labels $labels — PR number is empty"
        return
    }
    withCredentials([string(credentialsId: credentialsId, variable: 'GITHUB_TOKEN')]) {
        labels.each { label ->
            def encodedLabel = URLEncoder.encode(label, "UTF-8")
                    .replace("+", "%20")
            def url = "https://api.github.com/repos/${repositoryFullName}/issues/${number}/labels/${encodedLabel}"
            log.info "Removing label '${label}' from PR #${number}"
            httpRequest(
                    url: url,
                    httpMode: 'DELETE',
                    customHeaders: [
                            [name: 'User-Agent', value: 'Jenkins'],
                            [name: 'Authorization', value: "Bearer ${GITHUB_TOKEN}"],
                            [name: 'X-GitHub-Api-Version', value: '2022-11-28']
                    ],
                    validResponseCodes: '200'
            )
        }
    }
}