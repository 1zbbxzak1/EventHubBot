package ru.unithack.bot.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {

    private static final Logger logger = LoggerFactory.getLogger(QrCodeService.class);
    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;

    /**
     * Генерирует QR-код с заданным содержимым и возвращает его в виде массива байтов
     *
     * @param content содержимое QR-кода
     * @return массив байтов с изображением QR-кода в формате PNG
     */
    public byte[] generateQrCode(String content) {
        return generateQrCode(content, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Генерирует QR-код с заданным содержимым и указанными размерами
     *
     * @param content содержимое QR-кода
     * @param width ширина QR-кода
     * @param height высота QR-кода
     * @return массив байтов с изображением QR-кода в формате PNG
     */
    public byte[] generateQrCode(String content, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            logger.error("Error generating QR code", e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
} 