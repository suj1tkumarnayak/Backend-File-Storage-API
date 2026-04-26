package com.filestorageapi.exception;

public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException(String message){
        super(message);
    }
    public FileNotFoundException(String userName,String filename){
        super(String.format("File '%s' not found for user '%s'",filename, userName));
    }
}
