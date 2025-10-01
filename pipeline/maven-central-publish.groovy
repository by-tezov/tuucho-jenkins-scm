@Library('library@master') _

def setStatus = { status, message ->
    setPullRequestStatus(
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.maven,
            status,
            "${message}"
    )
}

String deploymentId = null

pipeline {
    agent {
        node {
            label 'ios-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        separator(name: '-build-', sectionHeader: '-build-')
        string(name: 'SOURCE_BRANCH', defaultValue: '', description: 'Source branch to build')
        string(name: 'TARGET_BRANCH', defaultValue: 'master', description: 'Target branch to merge into')
        booleanParam(name: 'UPLOAD', defaultValue: false, description: 'Upload to Maven Central')
        booleanParam(name: 'RELEASE', defaultValue: false, description: 'Release the publication')
        booleanParam(name: 'MERGE', defaultValue: false, description: 'Merge source into target')
        separator(name: '-system-', sectionHeader: '-system-')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        string(name: 'PULL_REQUEST_NUMBER', defaultValue: '', description: 'Pull request number')
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha ')
    }

    environment {
        AGENT = 'ios-builder'
    }

    options {
        parallelsAlwaysFailFast()
        ansiColor('xterm')
    }

    stages {
        stage('init') {
            steps {
                script {
                    parallel(
                            'update description': {
                                log.success "sourceBranch: ${params.SOURCE_BRANCH}, targetBranch: ${params.TARGET_BRANCH}"
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-#${params.CALLER_BUILD_NUMBER}"
                                if (params.COMMIT_AUTHOR != '' && params.COMMIT_MESSAGE != '') {
                                    log.info "author: ${params.COMMIT_AUTHOR}, message: ${params.COMMIT_MESSAGE}"
                                    currentBuild.description = "${params.COMMIT_AUTHOR}<br>"
                                } else {
                                    currentBuild.description = ''
                                }
                                currentBuild.description += "${params.SOURCE_BRANCH}"
                                currentBuild.description += "<br>-> ${params.TARGET_BRANCH}"
                            },
                            'status pending': {
                                setStatus(
                                        constant.pullRequestStatus.pending,
                                        "Publishing job initiated"
                                )
                            },
                            'clean workspaces': {
                                workspace.clean()
                            },
                            'prepare variables': {
                                // send to upstream
                                env.AGENT = env.AGENT
                                env.JOB_NAME = env.JOB_NAME
                                env.BUILD_NUMBER = env.BUILD_NUMBER
                            })
                }
            }
        }

        stage('clone and merge') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                dir('project') {
                    script {
                        setStatus(
                                constant.pullRequestStatus.pending,
                                "Cloning and Merging: source: ${params.SOURCE_BRANCH} -> target:${params.TARGET_BRANCH}"
                        )
                        cloneAndMerge(params.SOURCE_BRANCH, params.TARGET_BRANCH)
                    }
                }
            }
        }

        stage('check version') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Check version"
                    )
                    def parseVersion = { String input ->
                        // Regex matches:
                        //  group1 = tuucho version x.x.x
                        //  group2 = optional variant + number (e.g. -alpha4)
                        //  group3 = optional kotlin version x.x.x
                        //  group4 = optional -SNAPSHOT
                        def m = input =~ /^(\d+\.\d+\.\d+)(?:-([A-Za-z0-9]+))?(?:_(\d+\.\d+\.\d+))?(-SNAPSHOT)?$/
                        if (!m.matches()) {
                            error("Invalid version format: ${input}")
                        }
                        return [
                                tuucho    : m[0][1],
                                variant   : m[0][2],
                                kotlin    : m[0][3],
                                isSnapshot: m[0][4] == 'true'
                        ]
                    }

                    def compareVersion = { String a, String b ->
                        def pa = a.tokenize('.').collect { it as int }
                        def pb = b.tokenize('.').collect { it as int }
                        while (pa.size() < 3) pa << 0
                        while (pb.size() < 3) pb << 0
                        for (int i = 0; i < 3; i++) {
                            if (pa[i] < pb[i]) return -1
                            if (pa[i] > pb[i]) return 1
                        }
                        return 0
                    }

                    def compareVariant = { String a, String b ->
                        if (!a && !b) return 0
                        if (!a) return -1
                        if (!b) return 1
                        def parse = { v ->
                            def m = v =~ /^([a-zA-Z]+)(\d+)$/
                            if (!m.matches()) {
                                error("Invalid variant format: $v (must be alphaN or betaN)")
                            }
                            return [prefix: m[0][1].toLowerCase(), num: m[0][2] as int]
                        }
                        def va = parse(a)
                        def vb = parse(b)
                        def order = ["alpha": 0, "beta": 1]
                        if (!order.containsKey(va.prefix) || !order.containsKey(vb.prefix)) {
                            error("Unsupported variant prefix: ${va.prefix} or ${vb.prefix}")
                        }
                        def cmpPrefix = order[va.prefix] <=> order[vb.prefix]
                        if (cmpPrefix != 0) return cmpPrefix
                        return va.num <=> vb.num
                    }

                    def marketingVersion = getMarketingVersion()
                    log.info "Marketing version: ${marketingVersion}"
                    def currentRelease = parseVersion(marketingVersion)

                    if (currentRelease.isSnapshot) {
                        error("current version is SNAPSHOT, for now this pipeline don't support it")
                    }

                    if (!currentRelease.isSnapshot) {
                        def lastTag = getLastTag()
                        log.info "Last release: ${lastTag}"
                        if (!lastTag) {
                            error("No last tag found")
                        }
                        def lastRelease = parseVersion(lastTag)

                        def cmpTuuchoVersion = compareVersion(currentRelease.tuucho, lastRelease.tuucho)
                        if (cmpTuuchoVersion < 0) {
                            error("last release version is higher")
                        }
                        if (cmpTuuchoVersion == 0) {
                            def cmpVariant = compareVariant(currentRelease.variant, lastRelease.variant)
                            if (cmpVariant < 0) {
                                error("last release variant is higher")
                            }
                            if (cmpVariant == 0) {
                                error("last release variant is equal")
                            }
                        }
                    }
                }
            }
        }

        stage('Publishing lib') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Publishing lib"
                    )
                    if (getIsSnapshot() == 'true') {
                        error("❌ SNAPSHOT version found in catalog lib — release build cannot continue")
                    }
                    withCredentials([
                            file(credentialsId: env.MAVEN_SIGNING_KEY, variable: 'MAVEN_SIGNING_KEY_FILE'),
                            string(credentialsId: env.MAVEN_SIGNING_PASSWORD, variable: 'MAVEN_SIGNING_PASSWORD'),
                    ]) {
                        withEnv(["MAVEN_SIGNING_KEY=" + readFile(MAVEN_SIGNING_KEY_FILE)]) {
                            sourceEnv {
                                runGradleTask("rootPublishProdToMavenLocal")
                            }
                        }
                    }
                }
            }
        }

        stage('Upload to Maven Central') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            when {
                expression { params.UPLOAD }
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Upload lib"
                    )
                    dir('project/.m2') {
                        def fileName = getMarketingVersion()
                        sh "zip -r ${fileName}.zip ."
                        withCredentials([
                                string(credentialsId: env.MAVEN_AUTHORIZATION_TOKEN, variable: 'MAVEN_AUTHORIZATION_TOKEN'),
                        ]) {
                            def response = httpRequest(
                                    httpMode: 'POST',
                                    url: 'https://central.sonatype.com/api/v1/publisher/upload',
                                    multipartName: 'bundle',
                                    uploadFile: "${fileName}.zip",
                                    customHeaders: [
                                            [name: 'User-Agent', value: 'Jenkins'],
                                            [name: 'Authorization', value: "Bearer $MAVEN_AUTHORIZATION_TOKEN"]
                                    ],
                                    validResponseCodes: '201'
                            )
                            deploymentId = response.content
                            log.info "Deployment ID: ${response.content}"

                        }
                    }
                }
            }
        }

        stage('Release publication') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            when {
                expression { params.RELEASE && deploymentId != null }
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Release publication"
                    )
                    withCredentials([
                            string(credentialsId: env.MAVEN_AUTHORIZATION_TOKEN, variable: 'MAVEN_AUTHORIZATION_TOKEN'),
                    ]) {
                        while (true) {
                            def resp = httpRequest(
                                    httpMode: 'POST',
                                    url: "https://central.sonatype.com/api/v1/publisher/status?id=685dfb8b-bf12-4185-a941-d042b7ab766a",
                                    customHeaders: [
                                            [name: 'User-Agent', value: 'Jenkins'],
                                            [name: 'Authorization', value: "Bearer $MAVEN_AUTHORIZATION_TOKEN"]
                                    ],
                                    validResponseCodes: '200'
                            )
                            def json = readJSON text: resp.content
                            def deploymentState = json.deploymentState
                            log.info "Deployment ${deploymentId} status: ${deploymentState}"
                            if (deploymentState == 'VALIDATED') break
                            if (deploymentState == 'FAILED') {
                                error("Deployment failed")
                            }
                            sleep 30
                        }
                        httpRequest(
                                httpMode: 'POST',
                                url: "https://central.sonatype.com/api/v1/publisher/deployment/${deploymentId}",
                                customHeaders: [
                                        [name: 'User-Agent', value: 'Jenkins'],
                                        [name: 'Authorization', value: "Bearer $MAVEN_AUTHORIZATION_TOKEN"]
                                ],
                                validResponseCodes: '204'
                        )
                    }
                }
            }
        }

        stage('Wait online') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            when {
                expression { params.RELEASE && deploymentId != null }
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Release publication"
                    )
                    withCredentials([
                            string(credentialsId: env.MAVEN_AUTHORIZATION_TOKEN, variable: 'MAVEN_AUTHORIZATION_TOKEN'),
                    ]) {
                        while (true) {
                            def resp = httpRequest(
                                    httpMode: 'POST',
                                    url: "https://central.sonatype.com/api/v1/publisher/status?id=685dfb8b-bf12-4185-a941-d042b7ab766a",
                                    customHeaders: [
                                            [name: 'User-Agent', value: 'Jenkins'],
                                            [name: 'Authorization', value: "Bearer $MAVEN_AUTHORIZATION_TOKEN"]
                                    ],
                                    validResponseCodes: '200'
                            )
                            def json = readJSON text: resp.content
                            def deploymentState = json.deploymentState
                            log.info "Deployment ${deploymentId} status: ${deploymentState}"
                            if (deploymentState == 'PUBLISHED') break
                            if (deploymentState == 'FAILED') {
                                error("Deployment failed")
                            }
                            sleep 30
                        }
                    }
                }
            }
        }

        stage('Merge into target') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            when {
                expression { params.MERGE }
            }
            steps {
                script {
                    build job: 'merge-request', parameters: [
                            string(name: 'SOURCE_BRANCH', value: params.SOURCE_BRANCH),
                            string(name: 'TARGET_BRANCH', value: params.TARGET_BRANCH),
                            string(name: 'COMMIT_AUTHOR', value: params.COMMIT_AUTHOR),
                            string(name: 'COMMIT_MESSAGE', value: params.COMMIT_MESSAGE),
                            string(name: 'CALLER_BUILD_NUMBER', value: env.BUILD_NUMBER),
                            string(name: 'PULL_REQUEST_NUMBER', value: params.PULL_REQUEST_NUMBER),
                            string(name: 'PULL_REQUEST_SHA', value: params.PULL_REQUEST_SHA),
                            string(name: 'PULL_REQUEST_LABELS', value: params.PULL_REQUEST_LABELS)
                    ], wait: true
                }
            }
        }
    }

    post {
        success {
            script {
                def status = (params.RELEASE && params.MERGE) ? constant.pullRequestStatus.success : constant.pullRequestStatus.pending
                setStatus(
                        status,
                        "Succeed: uploaded: ${params.UPLOAD}, released: ${params.RELEASE}, merged: ${params.MERGE}"
                )
            }
        }
        failure {
            script {
                setStatus(
                        constant.pullRequestStatus.failure,
                        "Failed: Mm, I can't help you, but maybe the logs will... :"
                )
            }
        }
    }
}
