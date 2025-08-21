package com.tezov.jenkins.webhook.enums

import com.tezov.jenkins.webhook.protocol.HasValue

enum RepositoryName implements HasValue<String> {
    android('android-demo-app'), ios('ios-demo-app')

    private final String value
    RepositoryName(String value) { this.value = value }
    @Override String getValue() { return value }

    static RepositoryName from(String value) {
        for (RepositoryName repo : values()) {
            if (repo.value == value) { return repo }
        }
        throw new IllegalArgumentException("No enum constant for value: $value")
    }
}


