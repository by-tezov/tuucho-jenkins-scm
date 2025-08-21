@Library('library@chore/migrate-to-tuucho') _

pipeline {
    agent {
        node {
            label 'android-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        string(name: 'SOURCE_BRANCH', defaultValue: 'chore/integrate-cicd', description: 'Source branch to build')
        string(name: 'TARGET_BRANCH', defaultValue: 'release/0.0.1-alpha9', description: 'Target branch to merge (merge is done only locally, not on remote)')
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
                            addFlavorTypeBadge(params.FLAVOR_TYPE)
                            currentBuild.displayName = "#${env.BUILD_NUMBER}-${params.SOURCE_BRANCH}"
                            if (params.COMMIT_AUTHOR != '' && params.COMMIT_MESSAGE != '') {
                                currentBuild.description = "${params.COMMIT_AUTHOR} - ${params.COMMIT_MESSAGE}"
                            } else {
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
                        script { cleanWorkspaces() }
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
                    git branch: params.SOURCE_BRANCH, credentialsId: "${env.GIT_CREDENTIAL_ID}", url: env.GIT_TUUCHO
                    //TODO merge on target
                }
            }
        }

        stage('unit test') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    replaceFlavorType(constant.flavorType.mock)
                    runGradleTask('allUnitTestsDebug', 'project')
                    if (currentBuild.description != '') {
                        currentBuild.description += "<br>"
                    }
                    currentBuild.description += """<a href="http://localhost/jenkins/report/${env.JOB_NAME}/_${env.BUILD_NUMBER}/project/${env.REPORT_TUUCHO_UNIT_TEST_FILE}" target="_blank">Tests report</a>"""
                }
            }
        }

        stage('coverage') {
            options {
                timeout(time: 2, unit: 'MINUTES')
            }
            steps {
                script {
                    runGradleTask('koverHtmlReport', 'project')
                    currentBuild.description += """<br><a href="http://localhost/jenkins/report/${env.JOB_NAME}/_${env.BUILD_NUMBER}/project/${env.REPORT_TUUCHO_COVERAGE_FILE}" target="_blank">Coverage report</a>"""
                }
            }
        }

        //TODO
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
//TODO replace flavor with params floavor
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
