package com.tezov.jenkins.webhook

import com.tezov.jenkins.webhook.protocol.IsKey
import com.tezov.jenkins.webhook.enums.Key
import com.tezov.jenkins.webhook.enums.KeyPullRequest

class WebhookContent {
    private Map<IsKey, Object> content = [:]

    Object get(IsKey key) {
        // this hack is not the best design pattern, but it allow me to access
        // nested value as if the map was flat.
        if (key instanceof Key) {
            return content[key]
        } else if (key instanceof KeyPullRequest) {
            return content[Key.pullRequest]?.get(key)
        } else {
            throw new IllegalArgumentException("Unknown key type: ${key?.class?.name}")
        }
    }

    void put(IsKey key, Object value) {
        content[key] = value
    }

    Object getAt(IsKey key) {
        get(key)
    }

    void putAt(IsKey key, Object value) {
        put(key, value)
    }

    Map<Key, Object> getContent() {
        content
    }

    @NonCPS
    @Override
    String toString() {
        content.toString()
    }
}
