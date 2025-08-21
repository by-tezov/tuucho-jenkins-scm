@Library('library@master') _

pipeline {
    agent {
        node {
            label 'ios-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        string(name: 'BRANCH_NAME', defaultValue: '', description: 'Branch name to build')
        string(name: 'RELEASE_NOTE', defaultValue: '', description: 'Release note on firebase distribution')
        choice(name: 'ENVIRONMENT', choices: ['debug', 'production'], description: 'Environment --TODO')
        string(name: 'MARKETING_VERSION', defaultValue: '', description: 'Marketing version, if empty will keep source value ')
        string(name: 'BUNDLE_VERSION', defaultValue: '', description: 'Bundle version, if empty will take the job number')
        booleanParam(name: 'DRY_RUN', defaultValue: true, description: 'Build IPA but don\'t push it to firebase')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
    }

    environment {
        FIREBASE_CLI_TOKEN = credentials('firebase-cli-token')
        FIREBASE_PROJECT_ID = credentials('firebase-ios-app-id-debug')
        FIREBASE_PROJECT = 'tezov-debug'
        FIREBASE_TESTER_GROUP = 'tezov-Debug-iOS'

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
                            currentBuild.displayName = "#${env.BUILD_NUMBER}-${params.BRANCH_NAME}"
                            if (params.COMMIT_AUTHOR != '' && params.COMMIT_MESSAGE != '') {
                                currentBuild.description = "${params.COMMIT_AUTHOR} - ${params.COMMIT_MESSAGE}"
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
                            cleanWorkspaces(env.AGENT_PATH_IOS, env.JOB_NAME)
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

        //TODO: instead of building without any test
        //  - call the pull-request with test e2e
        //  - when succeed, build the IPA and upload it

        stage('add plist option') {
            options {
                timeout(time: 1, unit: 'MINUTES')
            }
            steps {
                script {
                    dir('project') {
                        def configDir = 'debug/'
                        sh "mkdir -p ./archive/"
                        sh "cp ${env.AGENT_PATH_IOS}/helperFiles/archive-plist/${configDir}/options.plist ./archive/"
                    }
                }
            }
        }

        stage('build ipa') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    def arguments = [:]
                    arguments['applicationDir'] = env.PROJECT_FOLDER_PATH
                    arguments['marketingVersion'] = params.MARKETING_VERSION ?: 'nil'
                    arguments['bundleVersion'] = params.BUNDLE_VERSION ?: env.BUILD_NUMBER
                    runGradleTask('exportArchiveDebugApp', 'jenkins/gradle', arguments)
                }
            }
        }

        stage('push ipa to firebase') {
            when {
                expression { !params.DRY_RUN }
            }
            options {
                timeout(time: 3, unit: 'MINUTES')
            }
            steps {
                script {
                    def applicationLocation = getApplicationLocation(
                            params.ENVIRONMENT,
                            "${env.AGENT}-ipa",
                            env.PIPELINE_NAME,
                            env.BUILD_NUMBER
                    )
                    sh """
                        firebase appdistribution:distribute ${applicationLocation.path}/${applicationLocation.file} \
                            --app ${FIREBASE_PROJECT_ID} \
                            --token ${FIREBASE_CLI_TOKEN} \
                            --groups ${FIREBASE_TESTER_GROUP} \
                            --release-notes "${env.BRANCH_NAME}: ${params.RELEASE_NOTE}" \
                    """
                }
             }
        }

        stage('push dSym to firebase') {
            when {
                expression { !params.DRY_RUN }
            }
            options {
                timeout(time: 1, unit: 'MINUTES')
            }
            steps {
                script {
                    log.info "TODO" //TODO:
                }
            }
        }
    }
}
