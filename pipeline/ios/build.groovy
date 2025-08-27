@Library('library@chore/add-ios-build') _

def setStatus = { status, message ->
    setPullRequestStatus(
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.pr_ios,
            status,
            "${env.BUILD_NUMBER} - ${message}"
    )
}

String deviceToLock_Id = null

pipeline {
    agent {
        node {
            label 'ios-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
        string(name: 'SOURCE_BRANCH', defaultValue: 'chore/minor-stuff', description: 'Source branch to build')
        string(name: 'TARGET_BRANCH', defaultValue: 'release/0.0.1-alpha10', description: 'Target branch to merge (merge is done only locally, not on remote)')
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'], description: 'Build type')
        choice(name: 'FLAVOR_TYPE', choices: ['mock', 'prod'], description: 'Flavor type')
        choice(name: 'LANGUAGE', choices: ['en', 'fr'], description: 'Language to use for e2e test')
        string(name: 'BRANCH_NAME_QA', defaultValue: 'master', description: 'Branch name qa of e2e test')
        booleanParam(name: 'TEST_E2E', defaultValue: false, description: 'Build APK and launch test end to end')
        booleanParam(name: 'TEST_E2E_WAIT_TO_SUCCEED', defaultValue: false, description: 'Wait end of test e2e')
        booleanParam(name: 'TEST_E2E_CLEAR_VISUAL_BASELINE', defaultValue: false, description: 'Clear visual baseline for device selected')
        booleanParam(name: 'TEST_E2E_UPDATE_VISUAL_BASELINE', defaultValue: false, description: 'Update visual baseline for device selected')
        choice(name: 'DEVICE_NAME', choices: ['iphone_16-18.5-simulator', ''], description: 'Device name to use')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
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
                                currentBuild.displayName = "#${env.BUILD_NUMBER}"
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
                                        "${env.BUILD_NUMBER} - Buiding job initiated: build type:${params.BUILD_TYPE}, flavor type:${params.FLAVOR_TYPE}"
                                )
                            },
                            'clean workspaces': {
                                timeout(time: 1, unit: 'MINUTES') {
                                    cleanWorkspaces()
                                }
                            }
                    )
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
                                "${env.BUILD_NUMBER} - Cloning and Merging: source: ${params.SOURCE_BRANCH} -> target:${params.TARGET_BRANCH}"
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
            when {
                expression { params.TEST_E2E }
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "${env.BUILD_NUMBER} - Bundle install"
                    )
                }
                script {
                    sourceEnv {
                        runGradleTask(':app:ios:bundleInstall', 'project')
                    }
                }
            }
        }

        stage('build for e2e test') {
            options {
                timeout(time: 20, unit: 'MINUTES')
            }
            when {
                expression { params.TEST_E2E }
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "${env.BUILD_NUMBER} - Building for e2e test"
                    )
                }
                script {
                    if (params.DEVICE_NAME) {
                        lock(resource: params.DEVICE_NAME, variable: 'LOCKED_RESOURCE') {
                            deviceToLock_Id = env.LOCKED_RESOURCE0_DEVICE_NAME_ID
                        }
                    } else {
                        lock(label: "${env.PLATFORM}-simulator", quantity: 1, variable: 'LOCKED_RESOURCE') {
                            deviceToLock_Id = env.LOCKED_RESOURCE0_DEVICE_NAME_ID
                        }
                    }
                    log.info "pipeline will use device ${deviceToLock_Id}"
                }
                //TODO: here the lock could have been took by someone else already... Need to find a way to lock and unlock on demand

                lock(resource: deviceToLock_Id) {
                    script {
                        sourceEnv {
                            replaceFlavorType(params.FLAVOR_TYPE)
                            def arguments = [:]
                            arguments['device'] = deviceToLock_Id
                            runGradleTask(":app:ios:${constant.assembleTask[params.BUILD_TYPE]}", 'project', arguments)
                            //TODO, use agent-repository to store apk and update getApplicationPath
                        }
                    }
                }
            }
        }

        stage('launch e2e test') {
            when {
                expression { params.TEST_E2E }
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "${env.BUILD_NUMBER} - Launch e2e test"
                    )
                }
                script {
                    build job: 'ios/test-e2e', parameters: [
                            string(name: 'PULL_REQUEST_SHA', value: params.PULL_REQUEST_SHA),
                            string(name: 'BUILD_TYPE', value: params.BUILD_TYPE),
                            string(name: 'FLAVOR_TYPE', value: params.FLAVOR_TYPE),
                            string(name: 'CALLER_JOB_NAME', value: env.JOB_NAME),
                            string(name: 'CALLER_BUILD_NUMBER', value: env.BUILD_NUMBER),
                            string(name: 'LANGUAGE', value: params.LANGUAGE),
                            string(name: 'BRANCH_NAME', value: params.BRANCH_NAME_QA),
                            string(name: 'APP_VERSION', value: getMarketingVersion()),
                            booleanParam(name: 'CLEAR_VISUAL_BASELINE', value: params.TEST_E2E_CLEAR_VISUAL_BASELINE),
                            booleanParam(name: 'UPDATE_VISUAL_BASELINE', value: params.TEST_E2E_UPDATE_VISUAL_BASELINE),
                            string(name: 'DEVICE_NAME', value: deviceToLock_Id),
                            string(name: 'COMMIT_AUTHOR', value: params.COMMIT_AUTHOR),
                            string(name: 'COMMIT_MESSAGE', value: params.COMMIT_MESSAGE),
                    ], wait: params.TEST_E2E_WAIT_TO_SUCCEED
                }
            }
        }
    }
    post {
        success {
            script {
                if (!params.TEST_E2E) {
                    setStatus(
                            constant.pullRequestStatus.success,
                            "${env.BUILD_NUMBER} - Succeed: Make sure to read yourself again before to merge ;)"
                    )
                }
            }
        }
        failure {
            script {
                if (!params.TEST_E2E) {
                    setStatus(
                            constant.pullRequestStatus.failure,
                            "${env.BUILD_NUMBER} - Failed: Mm, I can't help you, but maybe the logs will... :"
                    )
                }
            }
        }
    }

}

