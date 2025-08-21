@groovy.transform.Field
def agent = [
        android: "android",
        ios    : "ios",
        qa     : "qa"
]

@groovy.transform.Field
def platform = [
        android: "android",
        ios    : "ios",
]

@groovy.transform.Field
def buildType = [
        debug  : "debug",
        release: "release"
]

@groovy.transform.Field
def flavorType = [
        mock: "mock",
        prod: "prod"
]