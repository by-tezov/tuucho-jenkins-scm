@Library('library@chore/add-ios-build') _

String deviceToLock_Id = null
Boolean appiumHasBeenStarted = false
Boolean deviceHasBeenStarted = false

def setStatus = { status, message ->
    setPullRequestStatus(
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.pr_ios,
            status,
            "${env.BUILD_NUMBER} - ${message}"
    )
}

pipeline {
    agent {
        node {
            label 'ios-qa'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
        string(name: 'BRANCH_NAME', defaultValue: 'master', description: 'Branch name to use')
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'], description: 'Build type')
        choice(name: 'FLAVOR_TYPE', choices: ['mock', 'prod'], description: 'Flavor type')
        choice(name: 'LANGUAGE', choices: ['en', 'fr'], description: 'Language')
        choice(name: 'CALLER_JOB_NAME', choices: ['ios/build'], description: 'Caller Job name')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        booleanParam(name: 'QUICK_ESCAPE_TEST_ONLY', defaultValue: true, description: 'Execute only "Quick Escape Test"')
        string(name: 'QUICK_ESCAPE_TEST_TAGS', defaultValue: '@_quickEscape', description: 'Test tags to use for "Quick Escape Test" (space separated)')
        string(name: 'TESTS_TAGS', defaultValue: '', description: 'Test tags to use for "All Tests" (space separated) - if empty, all tests available be will executed ')
        string(name: 'APP_VERSION', defaultValue: '', description: 'Application version')
        booleanParam(name: 'CLEAR_VISUAL_BASELINE', defaultValue: false, description: 'Clear visual baseline for device selected')
        booleanParam(name: 'UPDATE_VISUAL_BASELINE', defaultValue: false, description: 'Update visual baseline for device selected')
        choice(name: 'DEVICE_NAME', choices: ['iphone_16-18.5-simulator', ''], description: 'Device name to use')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
    }

    environment {
        AGENT = 'ios-qa'
        GITHUB_API_TOKEN = credentials('github-api-token')
        DEVICE_START_TIMEOUT_IN_SECONDS = '240'
        DEVICE_SHUTDOWN_TIMEOUT_IN_SECONDS = '120'
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
                                log.success "buildType: ${params.BUILD_TYPE}, falvorType: ${params.FLAVOR_TYPE}, qaBranch: ${params.BRANCH_NAME}"
                                addPlatformBadge(env.PLATFORM)
                                addBuildTypeBadge(params.BUILD_TYPE)
                                addFlavorTypeBadge(params.FLAVOR_TYPE)
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-${params.CALLER_BUILD_NUMBER}"
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
                                        "${env.CALLER_BUILD_NUMBER} - Test e2e job initiated: branch:${params.BRANCH_NAME}, language:${params.LANGUAGE}"
                                )
                            },
                            'clean workspaces': {
                                workspace.clean()
                            }
                    )
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
                            "${env.CALLER_BUILD_NUMBER} - Cloning: source: ${params.BRANCH_NAME}"
                    )
                }
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
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "${env.CALLER_BUILD_NUMBER} - Npm install"
                    )
                }
                script {
                    sourceEnv {
                        repository.restoreCache('node_modules', 'package-lock.json')
                        runGradleTask('npm.install', 'project')
                        repository.storeCache('node_modules', 'package-lock.json')
                    }
                }
            }
        }

        stage('---') {
            options {
                timeout(time: 180, unit: 'MINUTES')
            }
            steps {
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
                //TODO: here the lock could have been took by someone else already... Need to find a way to lock and unlock on demand without dsl block

                lock(resource: deviceToLock_Id) {
                    script {
                        setStatus(
                                constant.pullRequestStatus.pending,
                                "${env.CALLER_BUILD_NUMBER} - Launching Simulator, Appium and prepare visual baseline"
                        )
                        parallel(
                                'start appium': {
                                    stage('start appium') {
                                        timeout(time: 2, unit: 'MINUTES') {
                                            appiumHasBeenStarted = true
                                            sourceEnv {
                                                runGradleTask('appium.start', 'project')
                                            }
                                        }
                                    }
                                },
                                'start simulator': {
                                    stage('start appium') {
                                        timeout(time: 2, unit: 'MINUTES') {
                                            deviceHasBeenStarted = true
                                            sourceEnv {
                                                def arguments = [:]
                                                arguments['deviceName'] = deviceToLock_Id
                                                runGradleTask('simulator.start', 'project', arguments)
                                            }
                                        }
                                    }
                                })

                        setStatus(
                                constant.pullRequestStatus.pending,
                                "${env.CALLER_BUILD_NUMBER} - Testing: Good luck ;)"
                        )
                        def applicationLocation = project.applicationLocation(env.PLATFORM, params.BUILD_TYPE, params.CALLER_JOB_NAME, params.CALLER_BUILD_NUMBER)
                        def deviceOsVersion = (deviceToLock_Id =~ /-([^ -]+)-/)[0][1]
                        log.info "deviceOsVersion ${deviceOsVersion}"

                        log.info "visual baseline cache skipRestore: ${params.CLEAR_VISUAL_BASELINE}, skipSave: ${!params.UPDATE_VISUAL_BASELINE}"

                        cache(
                                maxCacheSize: 500,
                                skipRestore: params.CLEAR_VISUAL_BASELINE,
                                skipSave: !params.UPDATE_VISUAL_BASELINE,
                                caches: [
                                        arbitraryFileCache(
                                                path: 'project/visual-testing/baseline',
                                                cacheValidityDecidingFile: 'project/visual-testing/baseline/baseline.lock'
                                        )
                                ]) {

//                            if(!params.CLEAR_VISUAL_BASELINE) {
//                                repository.restoreCache('visual-testing/baseline', 'visual-testing/baseline/baseline.lock')
//                            }

                            stage('quick escape tests') {
                                timeout(time: 15, unit: 'MINUTES') {
                                    def arguments = [:]
                                    arguments['language'] = params.LANGUAGE
                                    arguments['platform'] = env.PLATFORM
                                    arguments['buildType'] = params.BUILD_TYPE
                                    arguments['flavorType'] = params.FLAVOR_TYPE
                                    arguments['deviceName'] = "${deviceToLock_Id}"
                                    arguments['deviceOsVersion'] = deviceOsVersion
                                    arguments['appVersion'] = params.APP_VERSION
                                    arguments['appPath'] = applicationLocation.path
                                    arguments['appFile'] = applicationLocation.file
                                    arguments['tags'] = params.QUICK_ESCAPE_TEST_TAGS
                                    sourceEnv {
                                        runGradleTask('test', 'project', arguments)
                                    }
                                }
                            }

                            stage('tests') {
                                if (!params.QUICK_ESCAPE_TEST_ONLY) {
                                    timeout(time: 180, unit: 'MINUTES') {
                                        def arguments = [:]
                                        arguments['language'] = params.LANGUAGE
                                        arguments['platform'] = env.PLATFORM
                                        arguments['buildType'] = params.BUILD_TYPE
                                        arguments['flavorType'] = params.FLAVOR_TYPE
                                        arguments['deviceName'] = "${deviceToLock_Id}"
                                        arguments['deviceOsVersion'] = deviceOsVersion
                                        arguments['appVersion'] = params.APP_VERSION
                                        arguments['appPath'] = applicationLocation.path
                                        arguments['appFile'] = applicationLocation.file
                                        arguments['tags'] = params.TESTS_TAGS
                                        sourceEnv {
                                            runGradleTask('test', 'project', arguments)
                                        }
                                    }
                                }
                            }

//                            if(params.UPDATE_VISUAL_BASELINE) {
//                                repository.storeCache('visual-testing/baseline', 'visual-testing/baseline/baseline.lock')
//                            }

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
                    parallel(
                            'stop appium': {
                                // I didn't implement any lock on appium server.
                                // I bet on the fact there is only one build allowed
                                // at time on the QA agent. But I should find a better way
                                stage('stop appium') {
                                    if (appiumHasBeenStarted) {
                                        timeout(time: 2, unit: 'MINUTES') {
                                            sourceEnv {
                                                runGradleTask('appium.stop', 'project')
                                            }
                                            appiumHasBeenStarted = false
                                        }
                                    }
                                }
                            },
                            'stop simulator': {
                                // could be an issue because the lock has already been released
                                // and maybe another pipeline has relocked it for itself.
                                stage('stop simulator') {
                                    if (deviceHasBeenStarted) {
                                        timeout(time: 2, unit: 'MINUTES') {
                                            sourceEnv {
                                                def arguments = [:]
                                                arguments['deviceName'] = deviceToLock_Id
                                                runGradleTask('simulator.stop', 'project', arguments)
                                            }
                                            deviceHasBeenStarted = false
                                        }
                                    }
                                }
                            }
                    )
                }
            }
            script {
                timeout(time: 2, unit: 'MINUTES') {
                    sourceEnv {
                        runGradleTask('allure.generate', 'project')
                    }
                    repository.storeReport('allure-report')
                    currentBuild.description += """<br><a href="http://localhost/jenkins/tuucho-report/${repository.relativePath()}/allure-report/index.html" target="_blank">Report</a>"""
                }
            }
        }
        success {
            //TODO: if the baseline didn't exist, should not put success
            script {
                setStatus(
                        constant.pullRequestStatus.success,
                        "${env.CALLER_BUILD_NUMBER} - Succeed: Make sure to read yourself again before to merge ;)"
                )
            }
        }
        failure {
            script {
                setStatus(
                        constant.pullRequestStatus.failure,
                        "${env.CALLER_BUILD_NUMBER} - Failed: Mm, I can't help you, but maybe the logs will... :"
                )
            }
        }
    }
}

