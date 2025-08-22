@Library('library@chore/migrate-to-tuucho') _

pipeline {
    agent {
        node {
            label 'android-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        string(name: 'SOURCE_BRANCH', defaultValue: '', description: 'Source branch to build')
        string(name: 'TARGET_BRANCH', defaultValue: '', description: 'Target branch to merge (merge is done only locally, not on remote)')
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'], description: 'Build type')
        choice(name: 'FLAVOR_TYPE', choices: ['mock', 'prod'], description: 'Flavor type')
        choice(name: 'LANGUAGE', choices: ['en', 'fr'], description: 'Language to use for e2e test')
        string(name: 'BRANCH_NAME_QA', defaultValue: 'master', description: 'Branch name qa of e2e test')
        booleanParam(name: 'TEST_E2E', defaultValue: false, description: 'Build APK and launch test end to end')
        booleanParam(name: 'TEST_E2E_WAIT_TO_SUCCEED', defaultValue: true, description: 'Wait end of test e2e')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
    }

    environment {
        AGENT = 'android'
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
                            currentBuild.displayName = "#${env.BUILD_NUMBER}"
                            if (params.COMMIT_AUTHOR != '' && params.COMMIT_MESSAGE != '') {
                                currentBuild.description = "${params.COMMIT_AUTHOR} - ${params.COMMIT_MESSAGE}<br>"
                            } else {
                                currentBuild.description = ''
                            }
                            currentBuild.description += "${params.SOURCE_BRANCH}"
                            currentBuild.description += "<br>-> ${params.TARGET_BRANCH}"
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

        stage('clone and merge') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                dir('project') {
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
                        log.info "************ Squash and Merge ${N} commits from ${params.SOURCE_BRANCH} into ${params.TARGET_BRANCH} ************"
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

        stage('unit test') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    replaceFlavorType(constant.flavorType.mock)
                    runGradleTask('allUnitTestsDebug', 'project')
                    currentBuild.description += """<br><a href="http://localhost/jenkins/report/android-build/${env.JOB_NAME}/_${env.BUILD_NUMBER}/project/${env.REPORT_TUUCHO_UNIT_TEST_FILE}" target="_blank">Tests report</a>"""
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
                    currentBuild.description += """<br><a href="http://localhost/jenkins/report/android-build/${env.JOB_NAME}/_${env.BUILD_NUMBER}/project/${env.REPORT_TUUCHO_COVERAGE_FILE}" target="_blank">Coverage report</a>"""
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
                    replaceFlavorType(params.FLAVOR_TYPE)
                    runGradleTask(constant.assembleTask[params.BUILD_TYPE], 'project')
                }
            }
        }

        stage('launch e2e test') {
            when {
                expression { params.TEST_E2E }
            }
            steps {
                script {
                    build job: 'android/test-e2e', parameters: [
                            string(name: 'BUILD_TYPE', value: params.BUILD_TYPE),
                            string(name: 'FLAVOR_TYPE', value: params.FLAVOR_TYPE),
                            string(name: 'CALLER_JOB_NAME', value: env.JOB_NAME),
                            string(name: 'CALLER_BUILD_NUMBER', value: env.BUILD_NUMBER),
                            string(name: 'LANGUAGE', value: params.LANGUAGE),
                            string(name: 'BRANCH_NAME', value: params.BRANCH_NAME_QA),
                            string(name: 'COMMIT_AUTHOR', value: params.COMMIT_AUTHOR),
                            string(name: 'COMMIT_MESSAGE', value: params.COMMIT_MESSAGE),
                            string(name: 'APP_VERSION', value: getMarketingVersion()),
                    ], wait: params.TEST_E2E_WAIT_TO_SUCCEED
                }
            }
        }
    }
}
