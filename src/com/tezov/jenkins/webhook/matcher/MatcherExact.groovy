package com.tezov.jenkins.webhook.matcher

import com.tezov.jenkins.webhook.protocol.HasValue
import com.tezov.jenkins.webhook.protocol.BooleanValue
import com.tezov.jenkins.webhook.protocol.StringValue
import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.protocol.IsKey

class MatcherExact extends MatcherKeyBase {
    private final HasValue<?> hasValue

    static MatcherExact with(IsKey key, HasValue<?> hasValue) {
        return new MatcherExact(key, hasValue)
    }

    static MatcherExact with(IsKey key, String value) {
        return new MatcherExact(key, new StringValue(value))
    }

    static MatcherExact with(IsKey key, Boolean value) {
        return new MatcherExact(key, new BooleanValue(value))
    }

    MatcherExact(IsKey key, HasValue<?> hasValue) {
        super(key)
        this.hasValue = hasValue
    }

    @Override
    boolean match(WebhookContent content, StringBuilder log) {
        def value = content[this.key]
        boolean result
        if (value instanceof Collection) {
            result = value.all { it == hasValue.value }
        } else {
            result = value == hasValue.value
        }
        log.append("${this.key}: ${value} :EXACT: ${hasValue.value} -> ${result}\n")
        return result
    }
}
