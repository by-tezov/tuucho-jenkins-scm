package com.tezov.jenkins.webhook.protocol

import com.tezov.jenkins.webhook.WebhookContent

interface Matcher {

    abstract boolean match(WebhookContent content, StringBuilder log)

}
