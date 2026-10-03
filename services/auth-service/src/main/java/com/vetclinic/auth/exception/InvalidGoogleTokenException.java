package com.vetclinic.auth.exception;

public class InvalidGoogleTokenException extends RuntimeException {

    public InvalidGoogleTokenException() {
        super("Invalid Google ID token");
    }
}
