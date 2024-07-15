package com.example.imapidle.util;

public class EmailAttachmentNameUtil {
    public static String getFileNameFromContentType(String contentType) {
        return contentType.split(";")[1].split("=")[1];
    }
}
