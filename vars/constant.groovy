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
def repository = [
        tuucho: 'tuucho'
]

@groovy.transform.Field
def agent = [
        repository     : 'agent-repository',
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
