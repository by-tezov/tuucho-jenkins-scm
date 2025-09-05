@Library('library@master') _

Boolean appiumHasBeenStarted = false
Boolean deviceHasBeenStarted = false

def setStatus = { status, message ->
    setPullRequestStatus(
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.e2e_test_an,
            status,
            "${message}"
    )
}

def setVisualBaselineStatus = { status, message ->
    setPullRequestStatus(
            env.GITHUB_CREDENTIAL_ID,
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.e2e_test_visual_baseline_an,
            status,
            "${message}"
    )
}

Map arguments = [:]
def applicationLocation

pipeline {
    agent {
        node {
            label 'android-qa'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        string(name: 'BRANCH_NAME', defaultValue: 'master', description: 'Branch name to use')
        string(name: 'APP_VERSION', defaultValue: '', description: 'Application version')
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'], description: 'Build type')
        choice(name: 'FLAVOR_TYPE', choices: ['mock', 'prod'], description: 'Flavor type')
        choice(name: 'LANGUAGE', choices: ['en', 'fr'], description: 'Language')
        booleanParam(name: 'QUICK_ESCAPE_TEST_ONLY', defaultValue: true, description: 'Execute only "Quick Escape Test"')
        string(name: 'QUICK_ESCAPE_TEST_TAGS', defaultValue: '@_quickEscape', description: 'Test tags to use for "Quick Escape Test" (space separated)')
        string(name: 'TESTS_TAGS', defaultValue: '', description: 'Test tags to use for "All Tests" (space separated) - if empty, all tests available be will executed ')
        booleanParam(name: 'CREATE_VISUAL_BASELINE', defaultValue: false, description: 'Create visual baseline')
        choice(name: 'DEVICE_NAME', choices: ['android-36-simulator-fluxbox', 'android-36-simulator-standalone'], description: 'Device name to use')
        separator(name: '-system-', sectionHeader: '-system-')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
    }

    environment {
        AGENT = 'android-qa'
        DEVICE_START_TIMEOUT_IN_SECONDS = '240'
        DEVICE_SHUTDOWN_TIMEOUT_IN_SECONDS = '120'
        PLATFORM = 'android'
        ADB_PORT = '5000'
    }

    options {
        parallelsAlwaysFailFast()
        ansiColor('xterm')
        lock(resource: params.DEVICE_NAME)
    }

    stages {
        stage('init') {
            steps {
                script {
                    parallel(
                            'update description': {
                                log.success "buildType: ${params.BUILD_TYPE}, falvorType: ${params.FLAVOR_TYPE}, qaBranch: ${params.BRANCH_NAME}"
                                addBuildTypeBadge(params.BUILD_TYPE)
                                addFlavorTypeBadge(params.FLAVOR_TYPE)
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-#${params.CALLER_BUILD_NUMBER}"
                                if (params.COMMIT_AUTHOR != '' && params.COMMIT_MESSAGE != '') {
                                    log.info "author: ${params.COMMIT_AUTHOR}, message: ${params.COMMIT_MESSAGE}"
                                    currentBuild.description = "${params.COMMIT_AUTHOR} - ${params.COMMIT_MESSAGE}<br>"
                                } else {
                                    currentBuild.description = ''
                                }
                                currentBuild.description += "${params.BRANCH_NAME}"
                            },
                            'status pending': {
                                setStatus(
                                        constant.pullRequestStatus.pending,
                                        "Test e2e job initiated"
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

                                // arguments + hash
                                arguments['language'] = params.LANGUAGE
                                arguments['platform'] = env.PLATFORM
                                arguments['buildType'] = params.BUILD_TYPE
                                arguments['flavorType'] = params.FLAVOR_TYPE
                                arguments['appVersion'] = params.APP_VERSION
                                arguments['deviceName'] = "${params.DEVICE_NAME}:${env.ADB_PORT}"
                                arguments['hash'] = hash(arguments)

                                // application location
                                applicationLocation = project.applicationLocation(
                                        env.PLATFORM,
                                        params.BUILD_TYPE,
                                        'android/build', //TODO when apk is archived on agent repository, this will disappear
                                        params.CALLER_BUILD_NUMBER
                                )
                            })
                }
            }
        }

        stage('clone Project') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Cloning: source: ${params.BRANCH_NAME}"
                    )
                }
                dir('project') {
                    git branch: "${params.BRANCH_NAME}", credentialsId: "${env.GIT_CREDENTIAL_ID}", url: "${env.GIT_TUUCHO_QA}"
                }
            }
        }

        stage('npm install') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Npm install"
                    )
                    repository.restoreCache('node_modules', [fileValidity: 'package-lock.json'])
                    runGradleTask('npm.install')
                    repository.storeCache('node_modules', [fileValidity: 'package-lock.json'])
                }
            }
        }

        stage('-start-') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Launching Simulator, Appium"
                    )
                    parallel(
                            'start appium': {
                                node('master') {
                                    stage('start appium') {
                                        timeout(time: 2, unit: 'MINUTES') {
                                            appiumHasBeenStarted = true
                                            def result = sh(script: "docker start ${env.PLATFORM}-appium", returnStdout: true).trim()
                                            if (result != "true") {
                                                error("failed to start appium-${env.PLATFORM}")
                                            }
                                        }
                                    }
                                }
                            },
                            'start simulator': {
                                node('master') {
                                    stage('start simulator') {
                                        timeout(time: 2, unit: 'MINUTES') {
                                            deviceHasBeenStarted = true
                                            def result = sh(script: "docker start ${params.DEVICE_NAME}", returnStdout: true).trim()
                                            if (result != "true") {
                                                error("failed to start ${params.DEVICE_NAME}")
                                            }
                                        }
                                        timeout(time: 5, unit: 'MINUTES') {
                                            while (true) {
                                                def result = sh(script: "docker status ${params.DEVICE_NAME}", returnStdout: true).trim()
                                                if (result == "true") {
                                                    break
                                                }
                                                sleep time: 5, unit: 'SECONDS'
                                            }
                                        }
                                    }
                                }
                            })
                }
            }
        }

        stage('connect appium to simulator') {
            options {
                timeout(time: 1, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Connect Simulator to Appium"
                    )
                    def request = [
                            host: params.DEVICE_NAME,
                            port: env.ADB_PORT
                    ]
                    def requestString = writeJSON returnText: true, json: request
                    httpRequest(
                            url: "http://${env.PLATFORM}-appium:4723/adb/connect",
                            timeout: env.APPIUM_API_REQUEST_TIMEOUT.toInteger(),
                            httpMode: 'POST',
                            customHeaders: [
                                    [name: 'Accept', value: "application/json; charset=utf-8"],
                                    [name: 'Content-type', value: "application/json; charset=utf-8"]
                            ],
                            requestBody: requestString,
                            validResponseCodes: '204'
                    )
                }
            }
        }

        stage('restore visual baseline') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    if (!params.CREATE_VISUAL_BASELINE) {
                        setStatus(
                                constant.pullRequestStatus.pending,
                                "Restore visual baseline"
                        )
                        def isRestored = repository.restoreCache("visual-testing/baseline/${arguments.hash}", [hashValidity: arguments.hash])
                        if (isRestored) {
                            setVisualBaselineStatus(
                                    constant.pullRequestStatus.success,
                                    "Visual baseline found and restored"
                            )
                        } else {
                            setVisualBaselineStatus(
                                    constant.pullRequestStatus.failure,
                                    "No visual baseline found, need to create the visual baseline"
                            )
                            error("No visual baseline found, need to create the visual baseline")
                        }
                    } else {
                        setVisualBaselineStatus(
                                constant.pullRequestStatus.pending,
                                "Will create visual baseline"
                        )
                    }
                }
            }
        }

        stage('quick escape tests') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Quick escape testing"
                    )
                    def _arguments = arguments.clone()
                    _arguments['appPath'] = applicationLocation.path
                    _arguments['appFile'] = applicationLocation.file
                    _arguments['tags'] = params.QUICK_ESCAPE_TEST_TAGS
                    runGradleTask('test', _arguments)
                }
            }
        }

        stage('tests') {
            options {
                timeout(time: 180, unit: 'MINUTES')
            }
            when {
                expression { !params.QUICK_ESCAPE_TEST_ONLY }
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Testing: Good luck ;)"
                    )
                    def _arguments = arguments.clone()
                    _arguments['appPath'] = applicationLocation.path
                    _arguments['appFile'] = applicationLocation.file
                    _arguments['tags'] = params.TESTS_TAGS
                    runGradleTask('test', _arguments)
                }
            }
        }

        stage('store visual baseline') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            when {
                expression { params.CREATE_VISUAL_BASELINE }
            }
            steps {
                script {
                    repository.storeCache("visual-testing/baseline/${arguments.hash}", [hashValidity: arguments.hash])
                }
            }
        }
    }
    post {
        always {
            script {
                if (deviceHasBeenStarted || appiumHasBeenStarted) {
                    node('master') {
                        parallel(
                                'stop appium': {
                                    // I didn't implement any lock on appium server.
                                    // I bet on the fact there is only one build allowed
                                    // at time on the QA agent. But I should find a better way
                                    stage('stop appium') {
                                        if (appiumHasBeenStarted) {
                                            timeout(time: 2, unit: 'MINUTES') {
                                                sh "docker stop ${env.PLATFORM}-appium"
                                                appiumHasBeenStarted = false
                                            }
                                        }
                                    }
                                },
                                'stop simulator': {
                                    stage('stop simulator') {
                                        if (deviceHasBeenStarted) {
                                            timeout(time: 2, unit: 'MINUTES') {
                                                sh "docker stop ${params.DEVICE_NAME}"
                                                deviceHasBeenStarted = false
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
            }
            script {
                timeout(time: 2, unit: 'MINUTES') {
                    runGradleTask('allure.generate')
                    repository.storeReport('allure-report')
                    currentBuild.description += """<br><a href="http://localhost/jenkins/tuucho-report/${repository.relativePath()}/allure-report/index.html" target="_blank">Report</a>"""
                }
            }
        }
        success {
            script {
                if (!params.CREATE_VISUAL_BASELINE) {
                    setStatus(
                            constant.pullRequestStatus.success,
                            "Succeed"
                    )
                } else {
                    setVisualBaselineStatus(
                            constant.pullRequestStatus.pending,
                            "Visual baseline creation successful, need to run again the e2e test"
                    )
                }
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

