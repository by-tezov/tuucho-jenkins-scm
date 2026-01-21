def call(
        String sourceBranch,
        String targetBranch,
        Boolean merge,
        String url = env.GIT_TUUCHO,
        String credentialsId = env.GIT_CREDENTIAL_ID
) {
    // end of hack
    git branch: sourceBranch, credentialsId: credentialsId, url: url
    sh "git fetch origin ${targetBranch}:${targetBranch}"
    if (merge) {
        sh "git rebase ${targetBranch}"
        def N = sh(
                script: "git rev-list --count ${targetBranch}..${sourceBranch}",
                returnStdout: true
        ).trim()
        log.info "Squash and Merge ${N} commits from ${sourceBranch} into ${targetBranch}"
        sh """
            git checkout ${targetBranch}
            git merge --squash ${sourceBranch} > /dev/null 2>&1
            git commit -m "Merge from ${sourceBranch}"
        """
    }

}
