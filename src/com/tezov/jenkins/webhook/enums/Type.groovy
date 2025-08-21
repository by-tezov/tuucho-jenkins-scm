package com.tezov.jenkins.webhook.enums

import com.tezov.jenkins.webhook.protocol.HasValue

enum Type implements HasValue<String> {
    push('Push Hook'), pull('Merge Request Hook')

    private final String value
    Type(String value) { this.value = value }
    @Override String getValue() { return value }
}


