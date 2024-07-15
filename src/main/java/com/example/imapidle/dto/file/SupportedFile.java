package com.example.imapidle.dto.file;

public enum SupportedFile {
    PDF_INVOICE("application/pdf");

    private final String contentType;

    SupportedFile(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public String toString() {
        return contentType;
    }
}
