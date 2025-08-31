@groovy.transform.Field
def label = [
        e2eTest              : 'E2E Test',
        unitTest             : 'Unit Test',
        e2eTestClearBaseline : 'E2E Test Clear Baseline',
        e2eTestUpdateBaseline: 'E2E Test Update Baseline',
]

@groovy.transform.Field
def language = [
        en: 'en',
        fr: 'fr'
]

@groovy.transform.Field
def flavorType = [
        mock: 'mock',
        prod: 'prod'
]

@groovy.transform.Field
def buildType = [
        debug  : 'debug',
        release: 'release'
]

@groovy.transform.Field
def assembleTask = [
        debug  : 'assembleDebug',
        release: 'assembleRelease'
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
        testE2E              : 'test_e2e',
        buildType            : 'build_type',
        flavorType           : 'flavor_type',
        language             : 'language',
        branchNameQA         : 'branch_name_qa',
        testE2EWaitToSucceed : 'test_e2e_wait_to_succeed',
        deviceIOS            : 'device_ios',
        deviceAN             : 'device_an',
        testE2EClearBaseline : 'test_e2e_clear_baseline',
        testE2EUpdateBaseline: 'test_e2e_update_baseline'

]

@groovy.transform.Field
def pullRequestContextStatus = [
        pr_an : 'PR-AN',
        pr_ios: 'PR-IOS'
]

@groovy.transform.Field
def pullRequestStatus = [
        pending: 'pending',
        error  : 'error',
        failure: 'failure',
        success: 'success'
]
