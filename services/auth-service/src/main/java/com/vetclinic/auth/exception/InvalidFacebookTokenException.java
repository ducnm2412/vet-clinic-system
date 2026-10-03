package com.vetclinic.auth.exception;

public class InvalidFacebookTokenException extends RuntimeException {

    public InvalidFacebookTokenException() {
        super("Invalid Facebook access token");
    }
}
