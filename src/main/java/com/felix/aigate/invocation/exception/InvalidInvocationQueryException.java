package com.felix.aigate.invocation.exception;

public class InvalidInvocationQueryException extends RuntimeException {

    public InvalidInvocationQueryException() {
        super("Invalid invocation query limit");
    }
}