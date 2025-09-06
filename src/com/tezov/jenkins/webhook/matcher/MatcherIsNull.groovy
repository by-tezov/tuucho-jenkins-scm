package com.tezov.jenkins.webhook.matcher

import com.tezov.jenkins.webhook.protocol.HasValue
import com.tezov.jenkins.webhook.protocol.BooleanValue
import com.tezov.jenkins.webhook.protocol.StringValue
import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.protocol.IsKey

class MatcherIsNull extends MatcherKeyBase {

    static MatcherIsNull with(IsKey key) {
        return new MatcherIsNull(key)
    }

    MatcherIsNull(IsKey key) {
        super(key)
    }

    @Override
    boolean match(WebhookContent content, StringBuilder log) {
        def value = content[this.key]
        boolean result
        if (value instanceof Collection) {
            result = value.all { it == null }
        } else {
            result = value == null
        }
        log.append("${this.key}: ${value} :IS_NULL: -> ${result}\n")
        return result
    }
}
