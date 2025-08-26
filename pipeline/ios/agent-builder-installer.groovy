@Library('library@chore/add-ios-build') _

pipeline {
    agent {
        node {
            label 'master'
        }
    }
    stages {
        stage('Agent Builder Installer') {
            steps {
                script {
                    sh "mkdir -p helper"
                    sh "cp ${env.JENKINS_HELPER_FILES}/mac-agent-builder-installer.bash helper/"
                    stash name: 'mac-agent-builder-installer', includes: 'helper/mac-agent-builder-installer.bash'
                    node('ios-builder') {
                        unstash 'mac-agent-builder-installer'
                        sh "bash helper/mac-agent-builder-installer.bash"
                    }
                }
            }
        }
    }
}
