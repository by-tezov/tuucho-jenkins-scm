@Library('library@master') _

def setStatus = { status, message ->
    setPullRequestStatus(
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.build_an,
            status,
            "${message}"
    )
}

pipeline {
    agent {
        node {
            label 'android-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        separator(name: '-build-', sectionHeader: '-build-')
        string(name: 'SOURCE_BRANCH', defaultValue: '', description: 'Source branch to build')
        string(name: 'TARGET_BRANCH', defaultValue: '', description: 'Target branch to merge into (merge is done only locally, not on remote)')
        choice(name: 'BUILD_TYPE', choices: ['mock', 'dev', 'stage', 'prod'], description: 'Build type')
        separator(name: '-system-', sectionHeader: '-system-')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
    }

    environment {
        AGENT = 'android-builder'
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
                                        "Build job initiated"
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
                        clone(params.SOURCE_BRANCH, params.TARGET_BRANCH, true)
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
                            runGradleTask('project/tuucho', 'rootPublishReleaseToMavenLocal')
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
                            def backendName = getBackendContainerName(constant.platform.android)
                            parallel(
                                    'config.properties': {
                                        withCredentials([file(credentialsId: env.TUUCHO_CONFIG_PROPERTIES, variable: 'TUUCHO_CONFIG_PROPERTIES')]) {
                                            sh "cp \"$TUUCHO_CONFIG_PROPERTIES\" config.properties"
                                        }
                                        sh """
                                            sed -i "s|^serverBaseUrlAndroid=.*|serverBaseUrlAndroid=http://${backendName}:3000|" config.properties
                                            grep -q "serverBaseUrlAndroid=http://${backendName}:3000" config.properties || {
                                                echo "ERROR: serverBaseUrlAndroid not updated correctly in config.properties" >&2
                                                exit 1
                                            }
                                        """
                                    },
                                    'network-security': {
                                        def networkSecurityFile = 'app/android/src/dev/res/xml/network_security_config.xml'
                                        sh """
                                            sed -i '/<domain-config cleartextTrafficPermitted="true">/a \\
                                            <domain includeSubdomains="true">${backendName}</domain>' ${networkSecurityFile}
                                            if ! grep -Fq '<domain includeSubdomains="true">${backendName}</domain>' ${networkSecurityFile}; then
                                                echo "ERROR: domain entry for ${backendName} not found in ${networkSecurityFile}" >&2
                                                exit 1
                                            fi
                                        """
                                    })
                        }
                    }
                    else if (params.BUILD_TYPE == constant.buildType.mock) {
                        dir('project/sample') {
                            withCredentials([file(credentialsId: env.TUUCHO_CONFIG_PROPERTIES, variable: 'TUUCHO_CONFIG_PROPERTIES')]) {
                                sh "cp \"$TUUCHO_CONFIG_PROPERTIES\" config.properties"
                            }
                            sh """
                                sed -i "s|^serverBaseUrlAndroid=.*|serverBaseUrlAndroid=http://localhost|" config.properties
                                grep -q "serverBaseUrlAndroid=http://localhost" config.properties || {
                                    echo "ERROR: serverBaseUrlAndroid not updated correctly in config.properties" >&2
                                    exit 1
                                }
                            """
                        }
                    }
                    else {
                        error("Build type not implemented yet: ${params.BUILD_TYPE }")
                    }

                }
            }
        }

        stage('build sample app') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Building sample app"
                    )
                    runGradleTask('project/sample', ":app:android:${constant.assembleTask[params.BUILD_TYPE]}")
                    //TODO, use agent-repository to store apk and update getApplicationPath
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
