package com.tezov.jenkins.webhook.matcher

import com.tezov.jenkins.webhook.protocol.HasValue
import com.tezov.jenkins.webhook.protocol.IsKey
import com.tezov.jenkins.webhook.WebhookContent

class MatcherRegex extends MatcherKeyBase {
    private final String pattern

    static MatcherRegex with(IsKey key, String pattern) {
        return new MatcherRegex(key, pattern)
    }

    static MatcherRegex withOr(IsKey key, List<HasValue<String>> hasValues) {
        String pattern = "(?:" + hasValues.collect { it.value }.join(")|(") + ")"
        return new MatcherRegex(key, pattern)
    }

    MatcherRegex(IsKey key, String pattern) {
        super(key)
        this.pattern = pattern
    }

    @Override
    boolean match(WebhookContent content, StringBuilder log) {
        def value = content[this.key]
        boolean result
        if (value instanceof Collection) {
            result = value.all { it ==~ pattern }
        } else {
            result = value ==~ pattern
        }
        log.append("${this.key}: ${value} :REGEX: ${pattern} -> ${result}\n")
        return result
    }
}
