@Library('library@chore/migrate-to-tuucho') _

pipeline {
    agent {
        node {
            label 'android-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        string(name: 'BRANCH_NAME', defaultValue: 'master', description: 'Branch name to build')
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'], description: 'Build type')
        choice(name: 'FLAVOR_TYPE', choices: ['mock', 'prod'], description: 'Flavor type')
        choice(name: 'LANGUAGE', choices: ['en', 'fr'], description: 'Language to use for e2e test')
        string(name: 'DEVICE', defaultValue: '', description: 'Device resource for e2e tests - if empty, the pipeline will decide itself. - resource must be an existing android-simulator lockable resource DEVICE_NAME_ID')
        string(name: 'BRANCH_NAME_QA', defaultValue: 'master', description: 'Branch name qa of e2e test')
        booleanParam(name: 'TEST_E2E', defaultValue: false, description: 'Build APK and launch test end to end')
        booleanParam(name: 'TEST_E2E_WAIT_TO_SUCCEED', defaultValue: false, description: 'Wait end of test e2e')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
    }

    environment {
        AGENT = 'android'
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
                            addBuildTypeBadge(params.BUILD_TYPE)
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
                            cleanWorkspaces(env.AGENT_AN_BUILDER_PATH, env.JOB_NAME)
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
                    git branch: params.BRANCH_NAME, credentialsId: "${env.GIT_CREDENTIAL_ID}", url: env.GIT_TUUCHO
                }
            }
        }

        stage('config') {
            parallel {
                stage('properties') {
                    options {
                        timeout(time: 1, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            log.info 'properties'
                            // TODO set flavor
                            // other
                        }
                    }
                }
                stage('marketing version') {
                    steps {
                        script {
                            dir('project') {
                                log.info 'marketing version'
                                // def manifestPath = "./app/src/main/AndroidManifest.xml"
                                // def manifestContent = readFile(manifestPath)
                                // TODO appVersion =
                                // + add to description
                            }
                        }
                    }
                }
            }
        }

        stage('unit test') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    runGradleTask('testDebugUnitTest', 'project')
                    if(currentBuild.description != '') {
                        currentBuild.description += "<br>"
                    }
                    currentBuild.description += """<a href="http://localhost/jenkins/report/${env.AGENT}/${env.PIPELINE_NAME}/_${env.BUILD_NUMBER}/project/${env.REPORT_ANDROID_UNIT_TEST_FILE}" target="_blank">Tests report</a>"""
                }
            }
        }

//        stage('coverage') {
//            options {
//                timeout(time: 2, unit: 'MINUTES')
//            }
//            steps {
//                script {
//                    runGradleTask('koverHtmlReport', 'project')
//                    currentBuild.description += """<br><a href="http://localhost/jenkins/report/${env.AGENT}/${env.PIPELINE_NAME}/_${env.BUILD_NUMBER}/project/${env.REPORT_ANDROID_COVERAGE_FILE}" target="_blank">Coverage report</a>"""
//                }
//            }
//        }

//        stage('build for e2e test') {
//            options {
//                timeout(time: 20, unit: 'MINUTES')
//            }
//            when {
//                expression { params.TEST_E2E  }
//            }
//            steps {
//                script {
//                    // TODO: in vars
//                    def taskName = {
//                        switch (params.ENVIRONMENT) {
//                            case 'debug': return 'assembleDebug'
//                            case 'production': return 'assembleProduction'
//                            default: error("Unknown environment: ${params.ENVIRONMENT}")
//                        }
//                    }()
//                    runGradleTask(taskName, 'project')
//                }
//            }
//        }
//
//        stage('launch e2e test') {
//            when {
//                expression { params.TEST_E2E }
//            }
//            steps {
//                script {
//                    build job: 'android/test-e2e', parameters:[
//                            string(name: 'ENVIRONMENT', value: params.ENVIRONMENT),
//                            string(name: 'CALLER_PIPELINE_NAME', value: env.PIPELINE_NAME),
//                            string(name: 'CALLER_BUILD_NUMBER', value: env.BUILD_NUMBER),
//                            string(name: 'LANGUAGE', value: params.LANGUAGE),
//                            string(name: 'BRANCH_NAME', value: params.BRANCH_NAME_QA),
//                            string(name: 'DEVICE', value: params.DEVICE),
//                            string(name: 'COMMIT_AUTHOR', value: params.COMMIT_AUTHOR),
//                            string(name: 'COMMIT_MESSAGE', value: params.COMMIT_MESSAGE),
//                            string(name: 'APP_VERSION', value: appVersion),
//                    ], wait: params.TEST_E2E_WAIT_TO_SUCCEED
//                }
//            }
//        }
    }
}
