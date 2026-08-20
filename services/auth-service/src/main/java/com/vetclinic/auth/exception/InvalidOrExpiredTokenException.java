package com.vetclinic.auth.exception;

public class InvalidOrExpiredTokenException extends RuntimeException {

    public InvalidOrExpiredTokenException() {
        super("Verification token is invalid or has expired");
    }
}
