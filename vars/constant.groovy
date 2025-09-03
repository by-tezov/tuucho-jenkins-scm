@groovy.transform.Field
def label = [
        e2eTestAN                  : 'E2E Test Android',
        e2eTestIOS                 : 'E2E Test iOS',
        unitTest                   : 'Unit Test',
        e2eTestCreateVisualBaseline: 'E2E Test - Create Visual Baseline'
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
        buildType                  : 'build_type',
        flavorType                 : 'flavor_type',
        language                   : 'language',
        branchNameQA               : 'branch_name_qa',
        e2eTestAN                  : 'e2e_test_an',
        deviceAN                   : 'device_an',
        e2eTestIOS                 : 'e2e_test_ios',
        deviceIOS                  : 'device_ios',
        e2eTestWaitToSucceed       : 'e2e_test_wait_to_succeed',
        e2eTestCreateVisualBaseline: 'e2e_test_create_visual_baseline'
]

@groovy.transform.Field
def pullRequestContextStatus = [
        pull_request: 'CI Check',
        unit_test   : 'Unit test Check',
        build_an    : 'Android: Build',
        e2e_test_an : 'Android: Test End To End',
        e2e_test_visual_baseline_an: 'Android: Visual baseline',
        build_ios   : 'iOS: Build',
        e2e_test_ios: 'iOS: Test End to End',
        e2e_test_visual_baseline_ios: 'iOS: Visual baseline'
]

@groovy.transform.Field
def pullRequestStatus = [
        pending: 'pending',
        error  : 'error',
        failure: 'failure',
        success: 'success'
]
