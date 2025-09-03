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
        string(name: 'TARGET_BRANCH', defaultValue: '', description: 'Target branch to merge (merge is done only locally, not on remote)')
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'], description: 'Build type')
        choice(name: 'FLAVOR_TYPE', choices: ['mock', 'prod'], description: 'Flavor type')
        choice(name: 'DEVICE_NAME', choices: ['iphone_16-18.5-simulator', ''], description: 'Device name to use')
        separator(name: '-system-', sectionHeader: '-system-')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
    }

    environment {
        AGENT = 'ios-builder'
        GITHUB_API_TOKEN = credentials('github-api-token')
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
                                log.success "buildType: ${params.BUILD_TYPE}, falvorType: ${params.FLAVOR_TYPE}, sourceBranch: ${params.SOURCE_BRANCH}, targetBranch: ${params.TARGET_BRANCH}"
                                addBuildTypeBadge(params.BUILD_TYPE)
                                addFlavorTypeBadge(params.FLAVOR_TYPE)
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-#${params.CALLER_BUILD_NUMBER}"
                                if (params.COMMIT_AUTHOR != '' && params.COMMIT_MESSAGE != '') {
                                    log.info "author: ${params.COMMIT_AUTHOR}, message: ${params.COMMIT_MESSAGE}"
                                    currentBuild.description = "${params.COMMIT_AUTHOR} - ${params.COMMIT_MESSAGE}<br>"
                                } else {
                                    currentBuild.description = ''
                                }
                                currentBuild.description += "${params.SOURCE_BRANCH}"
                                currentBuild.description += "<br>-> ${params.TARGET_BRANCH}"
                            },
                            'status pending': {
                                setStatus(
                                        constant.pullRequestStatus.pending,
                                        "Buid job initiated: build type"
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
                    }
                    git branch: params.SOURCE_BRANCH, credentialsId: "${env.GIT_CREDENTIAL_ID}", url: env.GIT_TUUCHO
                    script {
                        sh """
                            git fetch origin ${params.TARGET_BRANCH}:${params.TARGET_BRANCH}
                            git rebase ${params.TARGET_BRANCH}
                        """
                        def N = sh(
                                script: "git rev-list --count ${params.TARGET_BRANCH}..${params.SOURCE_BRANCH}",
                                returnStdout: true
                        ).trim()
                        log.info "Squash and Merge ${N} commits from ${params.SOURCE_BRANCH} into ${params.TARGET_BRANCH}"
                        sh """
                            git config --global user.email "tezov.app@gmail.com"
                            git config --global user.name "tezov.jenkins"
                            git checkout ${params.TARGET_BRANCH}
                            git merge --squash ${params.SOURCE_BRANCH} > /dev/null 2>&1
                            git commit -m "Merge from ${params.SOURCE_BRANCH}"
                        """
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
                        runGradleTask(':app:ios:bundleInstall')
                    }
                }
            }
        }

        stage('build') {
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
                            replaceFlavorType(params.FLAVOR_TYPE)
                            def arguments = [:]
                            arguments['device'] = params.DEVICE_NAME
                            runGradleTask(":app:ios:${constant.assembleTask[params.BUILD_TYPE]}", arguments)
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

