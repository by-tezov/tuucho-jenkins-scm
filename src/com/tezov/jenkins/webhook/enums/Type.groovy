package com.tezov.jenkins.webhook.enums

import com.tezov.jenkins.webhook.protocol.HasValue

enum Type implements HasValue<String> {
    push('push'), pull('pull_request')

    private final String value
    Type(String value) { this.value = value }
    @Override String getValue() { return value }
}


