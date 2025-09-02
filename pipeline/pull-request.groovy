@Library('library@master') _

def setStatus = { status, message ->
    setPullRequestStatus(
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.pull_request,
            status,
            "${env.BUILD_NUMBER} - ${env.CALLER_BUILD_NUMBER} - ${message}"
    )
}

String deviceToLock_Id_AN = null
String deviceToLock_Id_IOS = null

def childUnitTest
def childBuild_AN
def childBuild_IOS

pipeline {
    agent none

    parameters {
        separator(name: '-Build-', sectionHeader: '-build-')
        string(name: 'SOURCE_BRANCH', defaultValue: '', description: 'Source branch to build')
        string(name: 'TARGET_BRANCH', defaultValue: '', description: 'Target branch to merge (merge is done only locally, not on remote)')
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'], description: 'Build type')
        choice(name: 'FLAVOR_TYPE', choices: ['mock', 'prod'], description: 'Flavor type')
        separator(name: '-QA-', sectionHeader: '-QA-')
        choice(name: 'LANGUAGE', choices: ['en', 'fr'], description: 'Language to use for e2e test')
        string(name: 'BRANCH_NAME_QA', defaultValue: 'master', description: 'Branch name qa of e2e test')
        booleanParam(name: 'E2E_TEST_CREATE_VISUAL_BASELINE', defaultValue: false, description: 'Create visual baseline')
        separator(name: '-QA-Android-', sectionHeader: '-QA-Android-')
        booleanParam(name: 'E2E_TEST_AN', defaultValue: false, description: 'Build APK and launch Android end to end tests')
        choice(name: 'DEVICE_NAME_AN', choices: ['android-36-simulator-fluxbox', 'android-36-simulator-standalone', ''], description: 'Device name to use for Android')
        separator(name: '-QA-iOS-', sectionHeader: '-QA-iOS-')
        booleanParam(name: 'E2E_TEST_IOS', defaultValue: false, description: 'Build APP and launch iOS end to end tests')
        choice(name: 'DEVICE_NAME_IOS', choices: ['iphone_16-18.5-simulator', ''], description: 'Device name to use for iOS')
        separator(name: '-system-', sectionHeader: '-system-')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
    }

    environment {
        GITHUB_API_TOKEN = credentials('github-api-token')
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
                                if (params.E2E_TEST_AN) {
                                    addPlatformBadge(constant.platform.android)
                                }
                                if (params.E2E_TEST_IOS) {
                                    addPlatformBadge(constant.platform.ios)
                                }
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
                                        "build type:${params.BUILD_TYPE}, flavor type:${params.FLAVOR_TYPE}"
                                )
                            },
                            'prepare variables': {
                                // android device lock
                                if (params.E2E_TEST_AN) {
                                    lock(resource: params.DEVICE_NAME_AN ?: 'android-36-simulator-fluxbox', variable: 'LOCKED_RESOURCE') {
                                        deviceToLock_Id_AN = env.LOCKED_RESOURCE0_DEVICE_NAME_ID
                                    }
                                    log.info "will use simulator ${deviceToLock_Id_AN}"
                                }

                                // ios device lock
                                if (params.E2E_TEST_IOS) {
                                    if (params.DEVICE_NAME_IOS) {
                                        lock(resource: params.DEVICE_NAME_IOS, variable: 'LOCKED_RESOURCE') {
                                            deviceToLock_Id_IOS = env.LOCKED_RESOURCE0_DEVICE_NAME_ID
                                        }
                                    } else {
                                        lock(label: "ios-simulator", quantity: 1, variable: 'LOCKED_RESOURCE') {
                                            deviceToLock_Id_IOS = env.LOCKED_RESOURCE0_DEVICE_NAME_ID
                                        }
                                        log.info "will use simulator ${deviceToLock_Id_IOS}"
                                    }
                                }
                            })
                }
            }
        }

        stage('unit-test') {
            steps {
                script {
                    childUnitTest = build job: 'android/unit-test', parameters: [
                            string(name: 'SOURCE_BRANCH', value: params.SOURCE_BRANCH),
                            string(name: 'TARGET_BRANCH', value: params.TARGET_BRANCH),
                            string(name: 'BUILD_TYPE', value: params.BUILD_TYPE),
                            string(name: 'FLAVOR_TYPE', value: params.FLAVOR_TYPE),
                            string(name: 'COMMIT_AUTHOR', value: params.COMMIT_AUTHOR),
                            string(name: 'COMMIT_MESSAGE', value: params.COMMIT_MESSAGE),
                            string(name: 'CALLER_BUILD_NUMBER', value: env.BUILD_NUMBER),
                            string(name: 'PULL_REQUEST_SHA', value: params.PULL_REQUEST_SHA)
                    ], wait: true
                }
            }
        }

        stage('-build-') {
            parallel {
                stage('build android') {
                    when {
                        expression { params.E2E_TEST_AN }
                    }
                    steps {
                        script {
                            childBuild_AN = build job: 'android/build', parameters: [
                                    string(name: 'SOURCE_BRANCH', value: params.SOURCE_BRANCH),
                                    string(name: 'TARGET_BRANCH', value: params.TARGET_BRANCH),
                                    string(name: 'BUILD_TYPE', value: params.BUILD_TYPE),
                                    string(name: 'FLAVOR_TYPE', value: params.FLAVOR_TYPE),
                                    string(name: 'COMMIT_AUTHOR', value: params.COMMIT_AUTHOR),
                                    string(name: 'COMMIT_MESSAGE', value: params.COMMIT_MESSAGE),
                                    string(name: 'CALLER_BUILD_NUMBER', value: childUnitTest.buildVariables.BUILD_NUMBER),
                                    string(name: 'PULL_REQUEST_SHA', value: params.PULL_REQUEST_SHA)
                            ], wait: true
                        }
                    }
                }
                stage('build ios') {
                    when {
                        expression { params.E2E_TEST_IOS }
                    }
                    steps {
                        script {
                            childBuild_IOS = build job: 'ios/build', parameters: [
                                    string(name: 'SOURCE_BRANCH', value: params.SOURCE_BRANCH),
                                    string(name: 'TARGET_BRANCH', value: params.TARGET_BRANCH),
                                    string(name: 'BUILD_TYPE', value: params.BUILD_TYPE),
                                    string(name: 'FLAVOR_TYPE', value: params.FLAVOR_TYPE),
                                    string(name: 'DEVICE_NAME', value: deviceToLock_Id_IOS),
                                    string(name: 'COMMIT_AUTHOR', value: params.COMMIT_AUTHOR),
                                    string(name: 'COMMIT_MESSAGE', value: params.COMMIT_MESSAGE),
                                    string(name: 'CALLER_BUILD_NUMBER', value: childUnitTest.buildVariables.BUILD_NUMBER),
                                    string(name: 'PULL_REQUEST_SHA', value: params.PULL_REQUEST_SHA)
                            ], wait: true
                        }
                    }
                }
            }
        }

        stage('-e2e-test-') {
            parallel {
                stage('e2e-test android') {
                    when {
                        expression { params.E2E_TEST_AN }
                    }
                    steps {
                        script {
                            def appVersion = ''
                            node(childBuild_AN.buildVariables.AGENT) {
                                appVersion = getMarketingVersion(
                                        childBuild_AN.buildVariables.AGENT,
                                        childBuild_AN.buildVariables.JOB_NAME,
                                        childBuild_AN.buildVariables.BUILD_NUMBER
                                )
                            }
                            build job: 'android/e2e-test', parameters: [
                                    string(name: 'BRANCH_NAME', value: params.BRANCH_NAME_QA),
                                    string(name: 'APP_VERSION', value: appVersion),
                                    string(name: 'BUILD_TYPE', value: params.BUILD_TYPE),
                                    string(name: 'FLAVOR_TYPE', value: params.FLAVOR_TYPE),
                                    string(name: 'LANGUAGE', value: params.LANGUAGE),
                                    booleanParam(name: 'CREATE_VISUAL_BASELINE', value: params.E2E_TEST_CREATE_VISUAL_BASELINE),
                                    string(name: 'DEVICE_NAME', value: deviceToLock_Id_AN),
                                    string(name: 'COMMIT_AUTHOR', value: params.COMMIT_AUTHOR),
                                    string(name: 'COMMIT_MESSAGE', value: params.COMMIT_MESSAGE),
                                    string(name: 'CALLER_BUILD_NUMBER', value: childBuild_AN.buildVariables.BUILD_NUMBER),
                                    string(name: 'PULL_REQUEST_SHA', value: params.PULL_REQUEST_SHA)
                            ], wait: true
                        }
                    }
                }
                stage('e2e-test ios') {
                    when {
                        expression { params.E2E_TEST_IOS }
                    }
                    steps {
                        script {
                            def appVersion = ''
                            node(childBuild_IOS.buildVariables.AGENT) {
                                appVersion = getMarketingVersion(
                                        childBuild_IOS.buildVariables.AGENT,
                                        childBuild_IOS.buildVariables.JOB_NAME,
                                        childBuild_IOS.buildVariables.BUILD_NUMBER
                                )
                            }
                            build job: 'ios/e2e-test', parameters: [
                                    string(name: 'BRANCH_NAME', value: params.BRANCH_NAME_QA),
                                    string(name: 'BUILD_TYPE', value: params.BUILD_TYPE),
                                    string(name: 'FLAVOR_TYPE', value: params.FLAVOR_TYPE),
                                    string(name: 'LANGUAGE', value: params.LANGUAGE),
                                    string(name: 'APP_VERSION', value: appVersion),
                                    booleanParam(name: 'CREATE_VISUAL_BASELINE', value: params.E2E_TEST_CREATE_VISUAL_BASELINE),
                                    string(name: 'DEVICE_NAME', value: deviceToLock_Id_IOS),
                                    string(name: 'COMMIT_AUTHOR', value: params.COMMIT_AUTHOR),
                                    string(name: 'COMMIT_MESSAGE', value: params.COMMIT_MESSAGE),
                                    string(name: 'CALLER_BUILD_NUMBER', value: childBuild_IOS.buildVariables.BUILD_NUMBER),
                                    string(name: 'PULL_REQUEST_SHA', value: params.PULL_REQUEST_SHA)
                            ], wait: true
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
