package com.tezov.jenkins.webhook.matcher

import com.tezov.jenkins.webhook.protocol.HasValue
import com.tezov.jenkins.webhook.protocol.BooleanValue
import com.tezov.jenkins.webhook.protocol.StringValue
import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.protocol.IsKey

class MatcherIsTrue extends MatcherKeyBase {

    static MatcherIsTrue with(IsKey key) {
        return new MatcherIsTrue(key)
    }

    MatcherIsTrue(IsKey key) {
        super(key)
    }

    @Override
    boolean match(WebhookContent content, StringBuilder log) {
        def value = content[this.key]
        boolean result
        if (value instanceof Collection) {
            result = value.all { it == true }
        } else {
            result = value == true
        }
        log.append("${this.key}: ${value} :IS_TRUE: -> ${result}\n")
        return result
    }
}
