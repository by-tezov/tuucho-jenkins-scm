package com.tezov.jenkins.webhook.protocol

interface HasValue<T> {
    T getValue()
}

class StringValue implements HasValue<String> {
    private final String value

    StringValue(String value) {
        this.value = value
    }

    @Override String getValue() { return value }
}

class BooleanValue implements HasValue<Boolean> {
    private final Boolean value

    BooleanValue(Boolean value) {
        this.value = value
    }

    @Override Boolean getValue() { return value }
}