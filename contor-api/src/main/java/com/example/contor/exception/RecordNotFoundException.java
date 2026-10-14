package com.example.contor.exception;

/** Records are addressed as (collection id, record id): record ids are only unique per collection. */
public class RecordNotFoundException extends ResourceNotFoundException {
    public RecordNotFoundException(Long collectionId, Long recordId) {
        super("Record " + recordId + " not found in collection " + collectionId);
    }
}
