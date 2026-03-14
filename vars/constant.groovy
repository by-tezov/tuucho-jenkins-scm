@groovy.transform.Field
def label = [
        triggerCI: 'Trigger CI'
]

@groovy.transform.Field
def language = [
        en: 'en',
        fr: 'fr'
]

@groovy.transform.Field
def buildType = [
        mock : 'mock',
        dev  : 'dev',
        stage: 'stage',
        prod : 'prod',
]

@groovy.transform.Field
def assembleTask = [
        mock : 'assembleMock',
        dev  : 'assembleDev',
        stage: 'assembleStage',
        prod : 'assembleProd'
]

@groovy.transform.Field
def agent = [
        repository     : 'agent-repository',
        linux          : 'linux',
        macOS          : 'mac-os',
        android_builder: 'android-builder',
        android_qa     : 'android-qa',
        ios_builder    : 'ios-builder',
        ios_qa         : 'ios-qa'
]

@groovy.transform.Field
def platform = [
        android: 'android',
        ios    : 'ios',
]

@groovy.transform.Field
def commitOption = [
        buildType                  : 'build type',
        triggerOnDraft             : 'push trigger CI when on draft',
        danger                     : 'static-analysis',
        unitTest                   : 'unit-test',
        branchNameQA               : 'QA branch name',
        branchNameBackend          : 'Backend branch name',
        e2eTestCreateVisualBaseline: 'create visual baseline',
        language                   : 'language',
        e2eTestAN                  : 'Android test',
        deviceAN                   : 'Android test device',
        e2eTestIOS                 : 'iOS test',
        deviceIOS                  : 'iOS test device'
]

@groovy.transform.Field
def pullRequestContextStatus = [
        pull_request                : 'CI Check',
        danger_linux                : 'Danger-Linux',
        danger_mac_os               : 'Danger-MacOs',
        danger                      : 'Danger',
        unit_test                   : 'Unit test Check',
        build_an                    : 'Android: Build',
        e2e_test_an                 : 'Android: Test End To End',
        e2e_test_visual_baseline_an : 'Android: Visual baseline',
        build_ios                   : 'iOS: Build',
        e2e_test_ios                : 'iOS: Test End to End',
        e2e_test_visual_baseline_ios: 'iOS: Visual baseline',
        maven                       : 'Maven publication',
        merge_request               : 'Merge request'
]

@groovy.transform.Field
def pullRequestStatus = [
        pending: 'pending',
        error  : 'error',
        failure: 'failure',
        success: 'success'
]

@groovy.transform.Field
def system = [
        AGENT_AN_BUILDER_PATH: '/home/android',
        AGENT_AN_QA_PATH: '/home/android-qa',
        AGENT_IOS_BUILDER_PATH: '/Users/jenkins/Local/cicd/builder',
        AGENT_IOS_QA_PATH: '/Users/jenkins/Local/cicd/qa',
        AGENT_LINUX_PATH: '/home/linux',
        AGENT_MAC_OS_PATH: '/Users/jenkins/Local/cicd/mac-os',
        AGENT_REPOSITORY_PATH: '/home/repository',
        APPIUM_API_REQUEST_TIMEOUT: '10',
        CLEAN_WORKSPACE_MAX_SUB_WORKSPACES: '3',
        GIT_CREDENTIAL_ID: 'ssh-git-jenkins',
        GIT_TUUCHO: 'git@github.com:by-tezov/tuucho.git',
        GIT_TUUCHO_QA: 'git@github.com:by-tezov/tuucho-qa.git',
        GITHUB_API_REQUEST_TIMEOUT: '20',
        GITHUB_API_TOKEN_ID: 'github-api-token',
        GITHUB_ORGANIZATION: 'by-tezov',
        GITHUB_TUUCHO: 'tuucho',
        IS_CI: 'true',
        MAVEN_AUTHORIZATION_TOKEN: 'maven-authorization-token',
        MAVEN_PASSWORD: 'maven-user-password',
        MAVEN_SIGNING_KEY: 'maven-signing-key',
        MAVEN_SIGNING_PASSWORD: 'maven-signing-password',
        TUUCHO_CONFIG_PROPERTIES: 'tuucho-config-properties'
]
