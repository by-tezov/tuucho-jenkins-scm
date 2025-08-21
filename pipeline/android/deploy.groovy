@Library('library@master') _

pipeline {
    agent {
        node {
            label 'android-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        string(name: 'BRANCH_NAME', defaultValue: '', description: 'Branch name to build')
        string(name: 'RELEASE_NOTE', defaultValue: '', description: 'Release note on firebase distribution')
        choice(name: 'ENVIRONMENT', choices: ['debug', 'production'], description: 'Environment --TODO')
        string(name: 'MARKETING_VERSION', defaultValue: '', description: 'Marketing version, if empty will keep source value ')
        string(name: 'BUNDLE_VERSION', defaultValue: '', description: 'Bundle version, if empty will take the job number')
        booleanParam(name: 'DRY_RUN', defaultValue: true, description: 'Build APK but don\'t push it to firebase')
        booleanParam(name: 'GRADLE_COMMON_CACHE', defaultValue: true, description: 'Use the gradle common cache')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
    }

    environment {
        FIREBASE_CLI_TOKEN = credentials('firebase-cli-token')
        FIREBASE_PROJECT_ID = credentials('firebase-android-app-id-debug')
        FIREBASE_PROJECT = 'tezov-debug'
        FIREBASE_TESTER_GROUP = 'tezov-Debug-Android'

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
                            cleanWorkspaces(env.AGENT_AN_BUILDER_PATH, env.JOB_NAME)
                        }
                    }
                }
                stage('set gradle cache path') {
                    when {
                        expression { !params.GRADLE_COMMON_CACHE }
                    }
                    steps {
                        script {
                            env.GRADLE_USER_HOME =  "${env.WORKSPACE}/.gradle"
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
                    git branch: params.BRANCH_NAME, credentialsId: "${env.GIT_CREADENTIAL_ID}", url: env.GIT_URL_ANDROID
                }
            }
        }

        // TODO: instead of build without any test
        //  - call the build pipeline with test e2e
        //  - when succeed, retrieve the apk and upload it

        stage('config') {
            options {
                timeout(time: 1, unit: 'MINUTES')
            }
            parallel {
                stage('properties') {
                    steps {
                        script {
                            log.info 'properties'
                        }
                    }
                }
                stage('manifest version') {
                    steps {
                        script {
                            dir('project') {
                                def manifestPath = "./app/src/main/AndroidManifest.xml"
                                def manifestContent = readFile(manifestPath)

                                manifestContent = manifestContent.replaceAll(
                                        'android:versionCode="\\d+"',
                                        "android:versionCode=\"${params.BUNDLE_VERSION ?: env.BUILD_NUMBER}\""
                                )

                                if(params.MARKETING_VERSION) {
                                    manifestContent = manifestContent.replaceAll(
                                            'android:versionName="[^"]+"',
                                            "android:versionName=\"${params.MARKETING_VERSION}\""
                                    )
                                }

                                writeFile(file: manifestPath, text: manifestContent)
                            }
                        }
                    }
                }
                stage('network security') {
                    steps {
                        script {
                            dir('project') {
                                log.info 'network security'
                            }
                        }
                    }
                }
                stage('gradle env') {
                    steps {
                        script {
                            dir('project') {
                                log.info 'gradle env'
                            }
                        }
                    }
                }
            }
        }

        stage('build apk') {
            options {
                timeout(time: 20, unit: 'MINUTES')
            }
            steps {
                script {
                    runGradleTask('assembleDebug', 'project')
                }
            }
        }

        stage('push apk to firebase') {
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
                            env.AGENT,
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
    }
}
