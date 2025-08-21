package com.tezov.jenkins.webhook.matcher

import com.tezov.jenkins.webhook.protocol.HasValue
import com.tezov.jenkins.webhook.protocol.StringValue
import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.protocol.IsKey

class MatcherContains extends MatcherKeyBase {
    private final HasValue<?> hasValue

    static MatcherContains with(IsKey key, HasValue<?> hasValue) {
        return new MatcherContains(key, hasValue)
    }

    static MatcherContains with(IsKey key, String value) {
        return new MatcherContains(key, new StringValue(value))
    }

    MatcherContains(IsKey key, HasValue<?> hasValue) {
        super(key)
        this.hasValue = hasValue
    }

    @Override
    boolean match(WebhookContent content, StringBuilder log) {
        def value = content[this.key]
        boolean result
        if (value instanceof Collection || value instanceof String) {
            result = value.contains(hasValue.value)
            log.append("${this.key}: ${value} :CONTAINS: ${hasValue.value} -> ${result}\n")
        }
        else {
            result = false
            log.append("${this.key}: ${value} :CONTAINS: ${hasValue.value} -> error type of value received not managed, accept List or String\n")
        }
        return result
    }
}
