package com.tezov.jenkins.webhook.matcher

import com.tezov.jenkins.webhook.protocol.HasValue
import com.tezov.jenkins.webhook.protocol.BooleanValue
import com.tezov.jenkins.webhook.protocol.StringValue
import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.protocol.IsKey

class MatcherIsFalse extends MatcherKeyBase {

    static MatcherIsFalse with(IsKey key) {
        return new MatcherIsFalse(key)
    }

    MatcherIsFalse(IsKey key) {
        super(key)
    }

    @Override
    boolean match(WebhookContent content, StringBuilder log) {
        def value = content[this.key]
        boolean result
        if (value instanceof Collection) {
            result = value.all { it == false }
        } else {
            result = value == false
        }
        log.append("${this.key}: ${value} :IS_FALSE: -> ${result}\n")
        return result
    }
}
