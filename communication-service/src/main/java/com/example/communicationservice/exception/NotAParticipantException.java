package com.example.communicationservice.exception;

/** L'appelant n'est pas l'un des deux participants de la conversation → HTTP 403. */
public class NotAParticipantException extends RuntimeException {

    public NotAParticipantException() {
        super("Vous ne participez pas à cette conversation");
    }
}