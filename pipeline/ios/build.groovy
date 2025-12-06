@Library('library@master') _

def setStatus = { status, message ->
    setPullRequestStatus(
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.build_ios,
            status,
            "${message}"
    )
}

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
        string(name: 'TARGET_BRANCH', defaultValue: '', description: 'Target branch to merge into (merge is done only locally, not on remote)')
        choice(name: 'BUILD_TYPE', choices: ['mock', 'dev', 'stage', 'prod'], description: 'Build type')
        choice(name: 'DEVICE_NAME', choices: ['iphone_16-26.0-simulator', ''], description: 'Device name to use')
        separator(name: '-system-', sectionHeader: '-system-')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
    }

    environment {
        AGENT = 'ios-builder'
        PLATFORM = 'ios'
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
                                log.success "buildType: ${params.BUILD_TYPE}, sourceBranch: ${params.SOURCE_BRANCH}, targetBranch: ${params.TARGET_BRANCH}"
                                addBuildTypeBadge(params.BUILD_TYPE)
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
                                        "Build job initiated: build type"
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
                                "Cloning and Merging to ${params.TARGET_BRANCH}"
                        )
                    }
                    clone(params.SOURCE_BRANCH, params.TARGET_BRANCH, true)
                }
            }
        }

        stage('api validation') {
            options {
                timeout(time: 2, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Api validating"
                    )
                    sourceEnv {
                        runGradleTask("rootValidateReleaseApi")
                    }
                }
            }
        }

        stage('build lib') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Building lib"
                    )
                    withCredentials([
                            file(credentialsId: env.MAVEN_SIGNING_KEY, variable: 'MAVEN_SIGNING_KEY_FILE'),
                            string(credentialsId: env.MAVEN_SIGNING_PASSWORD, variable: 'MAVEN_SIGNING_PASSWORD')
                    ]) {
                        withEnv(["MAVEN_SIGNING_KEY=" + readFile(MAVEN_SIGNING_KEY_FILE)]) {
                            sourceEnv {
                                runGradleTask("rootPublishReleaseToMavenLocal")
                            }
                        }
                    }
                }
            }
        }

        stage('update configuration') {
            options {
                timeout(time: 1, unit: 'MINUTES')
            }
            steps {
                script {
                    if (params.BUILD_TYPE == constant.buildType.dev) {
                        dir('project/sample') {
                            def dockerMachineIp = '192.168.1.10'
                            def backendName = getBackendContainerName(constant.platform.ios)
                            parallel(
                                    'config.properties': {
                                        withCredentials([file(credentialsId: env.TUUCHO_CONFIG_PROPERTIES, variable: 'TUUCHO_CONFIG_PROPERTIES')]) {
                                            sh "cp \"$TUUCHO_CONFIG_PROPERTIES\" config.properties"
                                        }
                                        sh """
                                            sed -i '' "s|^serverBaseUrlIos=.*|serverBaseUrlIos=http://${dockerMachineIp}/backend/${backendName}|" config.properties
                                            grep -q "serverBaseUrlIos=http://${dockerMachineIp}/backend/${backendName}" config.properties || {
                                                echo "ERROR: serverBaseUrlIos not updated correctly in config.properties" >&2
                                                exit 1
                                            }
                                        """
                                    },
                                    'network-security': {
                                        def networkSecurityFile = 'app/ios/ios/ios/Info.plist'
                                        sh """
                                            sed -i '' '/<key>NSExceptionDomains<\\/key>/,/<\\/dict>/ {
                                                /<\\/dict>/ i\\
                                                    <key>${dockerMachineIp}</key>\\
                                                    <dict>\\
                                                        <key>NSIncludesSubdomains</key>\\
                                                        <true/>\\
                                                        <key>NSExceptionAllowsInsecureHTTPLoads</key>\\
                                                        <true/>\\
                                                    </dict>
                                            }' ${networkSecurityFile}
                                
                                            if ! grep -Fq "<key>${dockerMachineIp}</key>" ${networkSecurityFile}; then
                                                echo "ERROR: domain entry for ${dockerMachineIp} not found in ${networkSecurityFile}" >&2
                                                exit 1
                                            fi
                                        """
                                    }
                            )
                        }
                    }
                    else if (params.BUILD_TYPE == constant.buildType.mock) {
                        dir('project/sample') {
                            withCredentials([file(credentialsId: env.TUUCHO_CONFIG_PROPERTIES, variable: 'TUUCHO_CONFIG_PROPERTIES')]) {
                                sh "cp \"$TUUCHO_CONFIG_PROPERTIES\" config.properties"
                            }
                        }
                    }
                    else {
                        error("Build type not implemented yet: ${params.BUILD_TYPE }")
                    }
                }
            }
        }

        stage('bundle install') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Bundle install"
                    )
                }
                script {
                    sourceEnv {
                        runGradleTask(':app:ios:iosBundleInstall', null, 'project/sample')
                    }
                }
            }
        }

        stage('build sample app') {
            options {
                timeout(time: 20, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "building"
                    )
                }
                lock(resource: params.DEVICE_NAME) {
                    script {
                        sourceEnv {
                            def arguments = [:]
                            arguments['device'] = params.DEVICE_NAME
                            runGradleTask(":app:ios:${constant.assembleTask[params.BUILD_TYPE]}", arguments, 'project/sample')
                            //TODO, use agent-repository to store apk and update getApplicationPath
                        }
                    }
                }
            }
        }
    }
    post {
        success {
            script {
                setStatus(
                        constant.pullRequestStatus.success,
                        "Succeed"
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

