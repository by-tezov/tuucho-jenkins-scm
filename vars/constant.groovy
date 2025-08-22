@groovy.transform.Field
def label = [
        testAuto: 'Test Auto'
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
        debug: 'assembleDebug',
        release: 'assembleRelease'
]

@groovy.transform.Field
def repositoryToPlatform = [
        tuucho: 'android'
]

@groovy.transform.Field
def agent = [
        android: 'android',
        ios    : 'ios',
        qa     : 'qa'
]

@groovy.transform.Field
def platform = [
        android: 'android',
        ios    : 'ios',
]

@groovy.transform.Field
def commitOption = [
        testE2E             : 'test_e2e',
        buildType           : 'build_type',
        flavorType          : 'flavor_type',
        language            : 'language',
        branchNameQA        : 'branch_name_qa',
        testE2EWaitToSucceed: 'test_e2e_wait_to_succeed'
]