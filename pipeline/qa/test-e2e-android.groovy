@Library('library@master') _

String deviceToLock_Name = null
String deviceToLock_Id = null
Boolean appiumHasBeenStarted = false
Boolean deviceHasBeenStarted = false
def applicationLocation = null
String deviceSdkVersion = ''

pipeline {
    agent {
        node {
            label 'android-qa'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
        string(name: 'BRANCH_NAME', defaultValue: 'master', description: 'Branch name to use')
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'], description: 'Build type')
        choice(name: 'FLAVOR_TYPE', choices: ['mock', 'prod'], description: 'Flavor type')
        choice(name: 'LANGUAGE', choices: ['en', 'fr'], description: 'Language')
        choice(name: 'CALLER_JOB_NAME', choices: ['android/build'], description: 'Caller Job name')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        booleanParam(name: 'QUICK_ESCAPE_TEST_ONLY', defaultValue: true, description: 'Execute only "Quick Escape Test"')
        string(name: 'QUICK_ESCAPE_TEST_TAGS', defaultValue: '@_quickEscape', description: 'Test tags to use for "Quick Escape Test" (space separated)')
        string(name: 'TESTS_TAGS', defaultValue: '', description: 'Test tags to use for "All Tests" (space separated) - if empty, all tests available be will executed ')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
        string(name: 'APP_VERSION', defaultValue: '', description: 'Application version')
    }

    environment {
        AGENT = 'qa'
        GITHUB_API_TOKEN = credentials('github-api-token')
        DEVICE_START_TIMEOUT_IN_SECONDS = '240'
        DEVICE_SHUTDOWN_TIMEOUT_IN_SECONDS = '120'
        PLATFORM = 'android'
        ADB_PORT = '5000'
    }

    options {
        parallelsAlwaysFailFast()
    }

    stages {
        stage('init') {
            parallel {
                stage('update description') {
                    steps {
                        script {
                            addPlatformBadge(env.PLATFORM)
                            addBuildTypeBadge(params.BUILD_TYPE)
                            addFlavorTypeBadge(params.FLAVOR_TYPE)
                            // TODO: CALLER_Job_NAME icon
                            currentBuild.displayName = "#${env.BUILD_NUMBER}-${params.CALLER_BUILD_NUMBER}"
                            if (params.COMMIT_AUTHOR != '' && params.COMMIT_MESSAGE != '') {
                                currentBuild.description = "${params.COMMIT_AUTHOR} - ${params.COMMIT_MESSAGE}<br>"
                            } else {
                                currentBuild.description = ''
                            }
                            currentBuild.description += "${params.BRANCH_NAME}"
                        }
                    }
                }
                stage('status initiating') {
                    steps {
                        script {
                            setPullRequestStatus(
                                    params.PULL_REQUEST_SHA,
                                    constant.pullRequestStatus.pending,
                                    constant.pullRequestContextStatus.pr,
                                    "${env.CALLER_BUILD_NUMBER} - Test e2e job initiated: branch:${params.BRANCH_NAME}, language:${params.LANGUAGE}"
                            )
                        }
                    }
                }
                stage('clean workspaces') {
                    options {
                        timeout(time: 1, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            cleanWorkspaces()
                        }
                    }
                }
            }
        }

        stage('clone Project') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                setPullRequestStatus(
                        params.PULL_REQUEST_SHA,
                        constant.pullRequestStatus.pending,
                        constant.pullRequestContextStatus.pr,
                        "${env.CALLER_BUILD_NUMBER} - Cloning: source: ${params.BRANCH_NAME}"
                )
                dir('project') {
                    git branch: "${params.BRANCH_NAME}", credentialsId: "${env.GIT_CREDENTIAL_ID}", url: "${env.GIT_TUUCHO_QA}"
                }
            }
        }

        stage('npm install') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                setPullRequestStatus(
                        params.PULL_REQUEST_SHA,
                        constant.pullRequestStatus.pending,
                        constant.pullRequestContextStatus.pr,
                        "${env.CALLER_BUILD_NUMBER} - Npm install"
                )
//                cache(
//                        maxCacheSize: 250,
//                        caches: [
//                                arbitraryFileCache(
//                                        path: 'project/node_modules',
//                                        cacheValidityDecidingFile: 'package-lock.json'
//                                )
//                        ]) {
//
//                }
                script {
                    runGradleTask('npm.install', 'project')
                }
            }
        }

        stage('testing') {
            options {
                timeout(time: 180, unit: 'MINUTES')
            }
            steps {
                script {
                    lock(label: "${env.PLATFORM}-simulator", quantity: 1, variable: 'LOCKED_RESOURCE') {
                        deviceToLock_Name = env.LOCKED_RESOURCE0_DEVICE_NAME
                        deviceToLock_Id = env.LOCKED_RESOURCE0_DEVICE_NAME_ID
                    }
                    log.info "pipeline will use device ${deviceToLock_Id}"
                }
                // here the lock could have been took by someone else already... Need to find a way to lock and unlock on demand
                lock(resource: deviceToLock_Name) {
                    setPullRequestStatus(
                            params.PULL_REQUEST_SHA,
                            constant.pullRequestStatus.pending,
                            constant.pullRequestContextStatus.pr,
                            "${env.CALLER_BUILD_NUMBER} - Launching Emulator, Appium and Installing application"
                    )
                    script {
                        applicationLocation = getApplicationLocation(
                                env.PLATFORM,
                                params.BUILD_TYPE,
                                params.CALLER_JOB_NAME,
                                params.CALLER_BUILD_NUMBER
                        )
                        deviceSdkVersion = (deviceToLock_Id =~ /-(\d+)/)[0][1]
                        log.info "deviceSdkVersion ${deviceSdkVersion}"

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
                            stage('start emulator') {
                                timeout(time: 2, unit: 'MINUTES') {
                                    deviceHasBeenStarted = true
                                    def result = sh(script: "docker start ${deviceToLock_Id}", returnStdout: true).trim()
                                    if (result != "true") {
                                        error("failed to start ${deviceToLock_Id}")
                                    }
                                }
                            }
                            stage('wait emulator to be ready') {
                                timeout(time: 5, unit: 'MINUTES') {
                                    while (true) {
                                        def result = sh(script: "docker status ${deviceToLock_Id}", returnStdout: true).trim()
                                        if (result == "true") {
                                            break
                                        }
                                        sleep time: 5, unit: 'SECONDS'
                                    }
                                }
                            }
                        }

                        stage('connect appium to emulator') {
                            def request = [
                                    host: deviceToLock_Id,
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

                        setPullRequestStatus(
                                params.PULL_REQUEST_SHA,
                                constant.pullRequestStatus.pending,
                                constant.pullRequestContextStatus.pr,
                                "${env.CALLER_BUILD_NUMBER} - Testing: Good luck ;)"
                        )

                        stage('quick escape tests') {
                            timeout(time: 15, unit: 'MINUTES') {
                                def arguments = [:]
                                arguments['language'] = params.LANGUAGE
                                arguments['platform'] = env.PLATFORM
                                arguments['buildType'] = params.BUILD_TYPE
                                arguments['flavorType'] = params.FLAVOR_TYPE
                                arguments['deviceName'] = "${deviceToLock_Id}:${env.ADB_PORT}"
                                arguments['deviceSdkVersion'] = deviceSdkVersion
                                arguments['appVersion'] = params.APP_VERSION
                                arguments['appPath'] = applicationLocation.path
                                arguments['appFile'] = applicationLocation.file
                                arguments['tags'] = params.QUICK_ESCAPE_TEST_TAGS
                                runGradleTask('test', 'project', arguments)
                            }
                        }

                        if (!params.QUICK_ESCAPE_TEST_ONLY) {
                            stage('tests') {
                                timeout(time: 180, unit: 'MINUTES') {
                                    def arguments = [:]
                                    arguments['language'] = params.LANGUAGE
                                    arguments['platform'] = env.PLATFORM
                                    arguments['buildType'] = params.BUILD_TYPE
                                    arguments['flavorType'] = params.FLAVOR_TYPE
                                    arguments['deviceName'] = "${deviceToLock_Id}:${env.ADB_PORT}"
                                    arguments['deviceSdkVersion'] = deviceSdkVersion
                                    arguments['appVersion'] = params.APP_VERSION
                                    arguments['appPath'] = applicationLocation.path
                                    arguments['appFile'] = applicationLocation.file
                                    arguments['tags'] = params.TESTS_TAGS
                                    runGradleTask('test', 'project', arguments)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                if (deviceHasBeenStarted || appiumHasBeenStarted) {
                    node('master') {
                        if (appiumHasBeenStarted) {
                            // I didn't implement any lock on appium server.
                            // I bet on the fact there is only one build allowed
                            // at time on the QA agent. But I should find a better way
                            timeout(time: 2, unit: 'MINUTES') {
                                sh "docker stop ${env.PLATFORM}-appium"
                                appiumHasBeenStarted = false
                            }
                        }
                        if (deviceHasBeenStarted) {
                            // could be an issue because the lock has already been released
                            // and maybe another pipeline has relocked it for itself.
                            timeout(time: 2, unit: 'MINUTES') {
                                sh "docker stop ${deviceToLock_Id}"
                                deviceHasBeenStarted = false
                            }
                        }
                    }
                }
            }
            script {
                timeout(time: 2, unit: 'MINUTES') {
                    runGradleTask('allure.generate', 'project')
                    currentBuild.description += """<br><a href="http://localhost/jenkins/report/android-qa-test-e2e/${env.JOB_NAME}/_${env.BUILD_NUMBER}/project/${env.REPORT_TUUCHO_QA_TEST_E2E_FILE}" target="_blank">Report</a>"""
                }
            }
        }
        success {
            script {
                setPullRequestStatus(
                        params.PULL_REQUEST_SHA,
                        constant.pullRequestStatus.success,
                        constant.pullRequestContextStatus.pr,
                        "${env.CALLER_BUILD_NUMBER} - Succeed: Make sure to read yourself again before to merge ;)"
                )
            }
        }
        failure {
            script {
                setPullRequestStatus(
                        params.PULL_REQUEST_SHA,
                        constant.pullRequestStatus.failure,
                        constant.pullRequestContextStatus.pr,
                        "${env.CALLER_BUILD_NUMBER} - Failed: Mm, I can't help you, but maybe the logs will... :"
                )
            }
        }
    }
}

