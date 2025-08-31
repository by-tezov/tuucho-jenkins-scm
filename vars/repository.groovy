def relativePath(
        String buildNumber = env.BUILD_NUMBER,
        String agent = env.AGENT,
        String jobName = env.JOB_NAME
) {
    return "${agent}/${jobName}/_${buildNumber}"
}

def absolutePath(
        String buildNumber = env.BUILD_NUMBER,
        String agent = env.AGENT,
        String jobName = env.JOB_NAME
) {
    return "${workspace.path(constant.agent.repository)}/${relativePath(buildNumber, agent, jobName)}"
}

def key(
        String agent = env.AGENT,
        String jobName = env.JOB_NAME,
        String buildNumber = env.BUILD_NUMBER
) {
    return "${agent}-${jobName}}-_$buildNumber".replaceAll('[^a-zA-Z0-9_.-]', '_')
}

def storeReport(
        String target
) {
    def key = key()
    dir("project/${target}") {
        stash name: key, includes: "**/*"
    }
    node('repository') {
        dir("${absolutePath()}/${target}") {
            unstash key
        }
    }
}

def storeCache(
        String target,
        String validityTarget
) {
    def workspaceTarget = "project/${target}"
    if (!fileExists(workspaceTarget) || sh(script: "ls -A ${workspaceTarget} | wc -l", returnStdout: true).trim() == "0") {
        log.info "Target ${target} does not exist or is empty, skipping cache store"
        return
    }
    def fileValidityTarget = "project/${validityTarget}"
    if (!fileExists(fileValidityTarget)) {
        log.info "No validity file found at ${fileValidityTarget}, skipping cache store"
        return
    }
    def hashValidity = sh(
            script: "sha256sum '${fileValidityTarget}' | awk '{print \$1}'",
            returnStdout: true
    ).trim()
    def repoWorkspace = "${absolutePath('')}/${target}/${hashValidity}"
    def upToDateCache = false
    node('repository') {
        if (fileExists(repoWorkspace)) {
            upToDateCache = true
        }
    }
    if (upToDateCache) {
        log.info "Cache for ${target} already up-to-date, skipping store"
        return
    }
    log.info "storing cache ${hashValidity} for target ${target}"
    def key = key()
    dir(workspaceTarget) {
        stash name: key, includes: "**/*"
    }
    node('repository') {
        dir(repoWorkspace) {
            unstash key
        }
    }
}

def restoreCache(
        String target,
        String validityTarget
) {
    def fileValidityTarget = "project/${validityTarget}"
    if (!fileExists(fileValidityTarget)) {
        log.info "No validity file found at ${fileValidityTarget}, skipping cache restore"
        return
    }
    def hashValidity = sh(
            script: "sha256sum '${fileValidityTarget}' | awk '{print \$1}'",
            returnStdout: true
    ).trim()
    def repoWorkspace = "${absolutePath('')}/${target}/${hashValidity}"
    def hasCache = false
    def key = key()
    node('repository') {
        if (fileExists(repoWorkspace)) {
            dir(repoWorkspace) {
                stash name: key, includes: '**/*'
                hasCache = true
            }
        }
    }
    if (!hasCache) {
        log.info "No cache to restore for target ${target}"
        return
    }
    log.info "restoring cache ${hashValidity} for ${target}"
    dir("project/${target}") {
        deleteDir()
        unstash key
    }
}
