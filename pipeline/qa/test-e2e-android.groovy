@Library('library@master') _

String deviceToUse = null
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
        string(name: 'BRANCH_NAME', defaultValue: 'master', description: 'Branch name to use')
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'], description: 'Build type')
        choice(name: 'FLAVOR_TYPE', choices: ['mock', 'prod'], description: 'Flavor type')
        choice(name: 'LANGUAGE', choices: ['fr', 'en'], description: 'Language')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        choice(name: 'CALLER_PIPELINE_NAME', choices: ['build', 'deploy'], description: 'Pipeline name')
        string(name: 'DEVICE', defaultValue: '', description: 'Device resource for e2e test - if empty, the pipeline will decide itself. - resource must be an existing simulator lockable resource DEVICE_NAME_ID')
        booleanParam(name: 'QUICK_ESCAPE_TEST_ONLY', defaultValue: false, description: 'Execute only "Quick Escape Test"')
        string(name: 'QUICK_ESCAPE_TEST_TAGS', defaultValue: '@_connection', description: 'Test tags to use for "Quick Escape Test" (space separated)')
        string(name: 'TESTS_TAGS', defaultValue: '', description: 'Test tags to use for "All Tests" (space separated) - if empty, all tests available be will executed ')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
        string(name: 'APP_VERSION', defaultValue: '', description: 'Application version')
    }

    environment {
        DEVICE_START_TIMEOUT_IN_SECONDS = '240'
        DEVICE_SHUTDOWN_TIMEOUT_IN_SECONDS = '120'

        PLATFORM = 'android'
        AGENT = 'qa'
        PIPELINE_NAME = env.JOB_NAME.replace("${env.PLATFORM}/", '')
        PROJECT_FOLDER_PATH = getProjectFolderPath()
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
                            // TODO: CALLER_PIPELINE_NAME icon
                            currentBuild.displayName = "#${env.BUILD_NUMBER}-${params.CALLER_BUILD_NUMBER}-${params.BRANCH_NAME}"
                            if (params.COMMIT_AUTHOR != '' && params.COMMIT_MESSAGE != '') {
                                currentBuild.description = "${params.COMMIT_AUTHOR} - ${params.COMMIT_MESSAGE}"
                            }
                            else {
                                currentBuild.description = ''
                            }
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
                stage('chose simulator') {
                    options {
                        timeout(time: 1, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            if (!params.DEVICE) {
                                lock(label: "${env.PLATFORM}-simulator", variable: 'LOCKED_RESOURCE') {
                                    deviceToUse = env.LOCKED_RESOURCE0_DEVICE_NAME_ID
                                }
                            }
                            else {
                                deviceToUse = params.DEVICE
                            }
                            log.info "pipeline will use device ${deviceToUse}"
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
                dir('project') {
                    git branch: "${params.BRANCH_NAME}", credentialsId: "${env.GIT_CREADENTIAL_ID}", url: "${env.GIT_QA}"
                }
            }
        }

        stage('npm install') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
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
                lock(resource: deviceToUse) {
                    script {
                        applicationLocation = getApplicationLocation(
                                params.BUILD_TYPE,
                                env.PLATFORM,
                                params.CALLER_PIPELINE_NAME,
                                params.CALLER_BUILD_NUMBER
                        )
                        deviceSdkVersion = (deviceToUse =~ /-(\d+$)/)[0][1]

                        node('master') {
                            stage('start appium') {
                                timeout(time: 2, unit: 'MINUTES') {
                                    appiumHasBeenStarted = true
                                    def result = sh(script: "docker start ${env.PLATFORM}-appium", returnStdout: true).trim()
                                    if(result != "true") {
                                        error("failed to start appium-${env.PLATFORM}")
                                    }
                                }
                            }
                            stage('start emulator') {
                                timeout(time: 2, unit: 'MINUTES') {
                                    deviceHasBeenStarted = true
                                    def result = sh(script: "docker start ${env.PLATFORM}-${deviceToUse}", returnStdout: true).trim()
                                    if(result != "true") {
                                        error("failed to start a${env.PLATFORM}-${deviceToUse}")
                                    }
                                }
                            }
                            stage('wait emulator to be ready') {
                                timeout(time: 5, unit: 'MINUTES') {
                                    while (true) {
                                        def result = sh(script: "docker status ${env.PLATFORM}-${deviceToUse}", returnStdout: true).trim()
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
                                    host: "${env.PLATFORM}-${deviceToUse}",
                                    port: "5000"
                            ]
                            def requestString = writeJSON returnText: true, json: request
                            httpRequest(
                                    url: "http://appium-android:4723/adb/connect",
                                    timeout : env.APPIUM_API_REQUEST_TIMEOUT.toInteger(),
                                    httpMode: 'POST',
                                    customHeaders: [
                                            [name: 'Accept', value: "application/json; charset=utf-8"],
                                            [name: 'Content-type', value: "application/json; charset=utf-8"]
                                    ],
                                    requestBody: requestString,
                                    validResponseCodes: '204'
                            )
                        }

                        stage('quick escape tests') {
                            timeout(time: 15, unit: 'MINUTES') {
                                def arguments = [:]
                                arguments['language'] = params.LANGUAGE
                                arguments['platform'] = env.PLATFORM
                                arguments['environment'] = params.ENVIRONMENT
                                arguments['deviceName'] = "${env.PLATFORM}-${deviceToUse}:5000"
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
                                    arguments['environment'] = params.ENVIRONMENT
                                    arguments['deviceName'] = "${env.PLATFORM}-${deviceToUse}:5000"
                                    arguments['deviceSdkVersion'] = deviceSdkVersion
                                    arguments['appVersion'] = params.APP_VERSION
                                    arguments['appPath'] = applicationLocation.path
                                    arguments['appFile'] = applicationLocation.file
                                    arguments['tags'] = params.TESTS_TAGS
                                    runGradleTask('test', 'project', arguments)
                                }
                            }
                        }

                        if (deviceHasBeenStarted || appiumHasBeenStarted) {
                            node('master') {
                                if (deviceHasBeenStarted) {
                                    stage('shutdown emulator') {
                                        timeout(time: 2, unit: 'MINUTES') {
                                            sh "docker stop ${env.PLATFORM}-appium"
                                            deviceHasBeenStarted = false
                                        }
                                    }
                                }
                                if (appiumHasBeenStarted) {
                                    stage('shutdown appium') {
                                        timeout(time: 2, unit: 'MINUTES') {
                                            sh "docker stop ${env.PLATFORM}-${deviceToUse}"
                                            appiumHasBeenStarted = false
                                        }
                                    }
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
                timeout(time: 2, unit: 'MINUTES') {
                    //runGradleTask('allure.generate', 'project') // TODO uncomment when qa project is present in repo
                    if(currentBuild.description != '') {
                        currentBuild.description += "<br>"
                    }
                    currentBuild.description += """<a href="http://localhost/jenkins/report/${env.AGENT}/${env.PLATFORM}/${env.PIPELINE_NAME}/_${env.BUILD_NUMBER}/project/${env.REPORT_QA_TEST_E2E_FILE}" target="_blank">Report</a>"""
                }
            }
        }
        failure {
            script {
                if (deviceHasBeenStarted || appiumHasBeenStarted) {
                    node('master') {
                        if (deviceHasBeenStarted) {
                            // could be an issue because the lock has already been released
                            // and maybe the ios pull-request pipeline has relocked it for itself.
                            // But I didn't find a way yet to lock until here on failure
                            // so I just bet I'm lucky and won't have concurrent issue.
                            timeout(time: 2, unit: 'MINUTES') {
                                sh "docker stop appium-${env.PLATFORM}"
                                deviceHasBeenStarted = false
                            }
                        }
                        if (appiumHasBeenStarted) {
                            // same as above, I didn't implement any lock on appium server.
                            // This time, I bet on the fact there is only one build allowed
                            // at time on the QA agent. So I'm sure there won't be any
                            // concurrent issue for this. But if the number of allowed build on QA agent
                            // is more than 1, there will be issues. But that's a problem for another time.
                            timeout(time: 2, unit: 'MINUTES') {
                                sh "docker stop ${env.PLATFORM}-${deviceToUse}"
                                appiumHasBeenStarted = false
                            }
                        }
                    }
                }
            }
        }
    }
}
