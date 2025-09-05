package com.tezov.jenkins.webhook.enums

import com.tezov.jenkins.webhook.protocol.HasValue

enum PullRequestAction implements HasValue<String> {
    opened('opened'), reopened('reopened'), labeled('labeled'), update('update'), edited('edited')

    private final String value
    PullRequestAction(String value) { this.value = value }
    @Override String getValue() { return value }
}


