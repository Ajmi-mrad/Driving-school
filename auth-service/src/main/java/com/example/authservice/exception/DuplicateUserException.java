package com.example.authservice.exception;

/** Levée lorsqu'un username ou un email existe déjà. */
public class DuplicateUserException extends RuntimeException {

    public DuplicateUserException(String message) {
        super(message);
    }
}