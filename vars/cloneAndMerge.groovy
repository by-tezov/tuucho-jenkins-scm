def call(
        String sourceBranch,
        String targetBranch,
        String url = env.GIT_TUUCHO,
        String credentialsId = env.GIT_CREDENTIAL_ID
) {
    git branch: sourceBranch, credentialsId: credentialsId, url: url
    sh """
        git config --global user.email "tezov.app@gmail.com"
        git config --global user.name "tezov.jenkins"
        git fetch origin ${targetBranch}:${targetBranch}
        git rebase ${targetBranch}
    """
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
