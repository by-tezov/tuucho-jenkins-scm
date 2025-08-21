@Library('library@master') _

def deviceToUse = ''
String appVersion = ''

pipeline {
    agent {
        node {
            label 'ios-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        string(name: 'BRANCH_NAME', defaultValue: '', description: 'Branch name to build')
        choice(name: 'ENVIRONMENT', choices: ['debug', 'production'], description: 'Environment')
        choice(name: 'LANGUAGE', choices: ['fr', 'en'], description: 'Language to use for e2e test')
        string(name: 'DEVICE', defaultValue: '', description: 'Device resource for unit and e2e tests - if empty, the pipeline will decide itself. - resource must be an existing ios-simulator lockable resource DEVICE_NAME_ID')
        string(name: 'BRANCH_NAME_QA', defaultValue: 'main', description: 'Branch name qa of e2e test')
        booleanParam(name: 'TEST_E2E', defaultValue: false, description: 'Build APP and launch test end to end')
        booleanParam(name: 'TEST_E2E_WAIT_TO_SUCCEED', defaultValue: false, description: 'Wait end of test e2e')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
    }

    environment {
        AGENT = 'ios'
        PIPELINE_NAME = env.JOB_NAME.replace("${env.AGENT}/", '')
        PROJECT_FOLDER_PATH = getProjectFolderPath(env.AGENT, env.PIPELINE_NAME, env.BUILD_NUMBER)
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
                            addEnvironmentBadge(params.ENVIRONMENT)
                            currentBuild.displayName = "#${env.BUILD_NUMBER}-${params.BRANCH_NAME}"
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
                            cleanWorkspaces(env.AGENT_IOS_BUILDER_PATH, env.JOB_NAME)
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
                                lock(label: 'ios-simulator', variable: 'LOCKED_RESOURCE') {
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

        stage('clone git') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                dir('project') {
                    git branch: params.BRANCH_NAME, credentialsId: "${env.GIT_CREADENTIAL_ID}",  url: env.GIT_URL_IOS
                }
            }
        }

        stage('config') {
            parallel {
                stage('marketing version') {
                    steps {
                        script {
                            log.info 'marketing version'
                            //TODO appVersion =
                            // + add to description
                        }
                    }
                }
            }
        }

        stage('build for unit test') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    def arguments = [:]
                    arguments['device'] = deviceToUse
                    arguments['applicationDir'] = env.PROJECT_FOLDER_PATH
                    runGradleTask('assembleDevForTest', 'jenkins/gradle', arguments)
                }
            }
        }

        stage('unit test') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    lock(resource: deviceToUse) {
                        def arguments = [:]
                        arguments['device'] = deviceToUse
                        arguments['applicationDir'] = env.PROJECT_FOLDER_PATH
                        //runGradleTask('allTests', 'jenkins/gradle', arguments)
                    }
                    if(currentBuild.description != '') {
                        currentBuild.description += "<br>"
                    }
                    currentBuild.description += """<a href="http://localhost:8081/${env.AGENT}/${env.PIPELINE_NAME}/_${env.BUILD_NUMBER}/project/${env.REPORT_IOS_UNIT_TEST_FILE}" target="_blank">Tests report</a>"""
                }
            }
        }

        stage('coverage') {
            options {
                timeout(time: 2, unit: 'MINUTES')
            }
            steps {
                script {
                    def arguments = [:]
                    arguments['applicationDir'] = env.PROJECT_FOLDER_PATH
                    //runGradleTask('slatherFullReport', 'jenkins/gradle', arguments)
                    currentBuild.description += """<br><a href="http://localhost:8081/${env.AGENT}/${env.PIPELINE_NAME}/_${env.BUILD_NUMBER}/project/${env.REPORT_IOS_COVERAGE_FILE}" target="_blank">Coverage report</a>"""
                }
            }
        }

        stage('build for e2e test') {
            when {
                expression { params.TEST_E2E  }
            }
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    // TODO: in vars
                    def taskName = {
                        switch (params.ENVIRONMENT) {
                            case 'debug': return 'assembleDebug'
                            case 'production': return 'assembleProduction'
                            default: error("Unknown environment: ${params.ENVIRONMENT}")
                        }
                    }()
                    def arguments = [:]
                    arguments['device'] = deviceToUse
                    arguments['applicationDir'] = env.PROJECT_FOLDER_PATH
                    runGradleTask(taskName, 'jenkins/gradle', arguments)
                }
            }
        }

        stage('launch e2e test') {
            when {
                expression { params.TEST_E2E }
            }
            steps {
                script {
                    build job: 'qa/test-e2e', parameters:[
                            string(name: 'PLATFORM', value: env.AGENT),
                            string(name: 'ENVIRONMENT', value: params.ENVIRONMENT),
                            string(name: 'CALLER_PIPELINE_NAME', value: env.PIPELINE_NAME),
                            string(name: 'CALLER_BUILD_NUMBER', value: env.BUILD_NUMBER),
                            string(name: 'LANGUAGE', value: params.LANGUAGE),
                            string(name: 'BRANCH_NAME', value: params.BRANCH_NAME_QA),
                            string(name: 'DEVICE', value: deviceToUse),
                            string(name: 'COMMIT_AUTHOR', value: params.COMMIT_AUTHOR),
                            string(name: 'COMMIT_MESSAGE', value: params.COMMIT_MESSAGE),
                            string(name: 'APP_VERSION', value: appVersion),
                    ], wait: params.TEST_E2E_WAIT_TO_SUCCEED
                }
            }
        }
    }
}

