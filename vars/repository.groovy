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
    dir(target) {
        stash name: key, includes: "**/*"
    }
    node('repository') {
        dir("${absolutePath()}/${target}") {
            unstash key
        }
    }
}

def storeCache(
        String folder,
        Map input
) {
    if (input.containsKey('fileValidity')) {
        def fileValidity = "cache/${input.fileValidity}"
        if (!fileExists(fileValidity)) {
            log.info "file validity not found at ${fileValidity}, skipping cache store"
            return
        }
        def hashValidity = sh(
                script: "sha256sum '${fileValidity}' | awk '{print \$1}'",
                returnStdout: true
        ).trim()
        return storeCache(folder, hashValidity)
    }
    if (input.containsKey('hashValidity')) {
        return storeCache(folder, input.hashValidity)
    }
    error("no named argument valid for store cache ")
}

def storeCache(
        String folder,
        String hashValidity
) {
    def workspace = folder
    if (!fileExists(workspace) || sh(script: "ls -A ${workspace} | wc -l", returnStdout: true).trim() == "0") {
        log.info "${folder} does not exist or is empty, skipping cache store"
        return
    }
    def repoWorkspace = "${absolutePath('')}/${folder}"
    def tarFile = "${hashValidity}.tar"
    def upToDateCache = false
    node('repository') {
        if (fileExists("${repoWorkspace}/${tarFile}")) {
            upToDateCache = true
        }
    }
    if (upToDateCache) {
        log.info "Cache for ${folder} already up-to-date, skipping store"
        return
    }
    log.info "storing cache ${folder}"
    def key = key()
    dir(workspace) {
        sh """
            tar -cf ${env.WORKSPACE}/project@tmp/${tarFile} .
            mv ${env.WORKSPACE}/project@tmp/${tarFile} ${tarFile}
        """
        stash name: key, includes: tarFile
    }
    node('repository') {
        dir(repoWorkspace) {
            unstash key
        }
    }
}

def restoreCache(
        String folder,
        Map input
) {
    if (input.containsKey('fileValidity')) {
        def fileValidity = "cache/${input.fileValidity}"
        if (!fileExists(fileValidity)) {
            log.info "file validity not found at ${fileValidity}, skipping cache store"
            return false
        }
        def hashValidity = sh(
                script: "sha256sum '${fileValidity}' | awk '{print \$1}'",
                returnStdout: true
        ).trim()
        return restoreCache(folder, hashValidity)
    }
    if (input.containsKey('hashValidity')) {
        return restoreCache(folder, input.hashValidity)
    }
}

def restoreCache(
        String folder,
        String hashValidity
) {
    def repoWorkspace = "${absolutePath('')}/${folder}"
    def tarFile = "${hashValidity}.tar"
    def hasCache = false
    def key = key()
    node('repository') {
        if (fileExists("${repoWorkspace}/${tarFile}")) {
            dir(repoWorkspace) {
                stash name: key, includes: tarFile
                hasCache = true
            }
        }
    }
    if (!hasCache) {
        log.info "No cache to restore for folder ${folder}"
        return false
    }
    log.info "restoring cache for ${folder}"
    dir(folder) {
        deleteDir()
        unstash key
        sh """
            tar -xf ${tarFile}
            rm -f ${tarFile}
        """
    }
    return true
}
