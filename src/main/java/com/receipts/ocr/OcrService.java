package com.receipts.ocr;

import java.io.InputStream;

public interface OcrService {

    String extractText(String originalFilename, String contentType, InputStream content);
}
