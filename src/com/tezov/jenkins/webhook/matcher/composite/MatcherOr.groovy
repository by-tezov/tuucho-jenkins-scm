package com.tezov.jenkins.webhook.matcher.composite

import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.protocol.Matcher

class MatcherOr extends MatcherComposite {

    @Override
    boolean match(WebhookContent content, StringBuilder log) {
        log.append("<or>\n")
        for (Matcher matcher : matchers) {
            if (matcher.match(content, log)) {
                log.append("</or>\n")
                return true
            }
        }
        log.append("!! Failed !!</or>\n")
        return false
    }
}