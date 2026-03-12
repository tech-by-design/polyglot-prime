package org.techbd.csv.model;

public record FileDetail(String filename, FileType fileType, String content,String filePath,boolean utf8Encoded,String reason) {
}