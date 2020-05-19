package com.validyne.blutransducer;

import org.apache.http.util.ByteArrayBuffer;

@SuppressWarnings("all")
public class HexAsciiHelper {
    public static int PRINTABLE_ASCII_MIN = 0x20; // ' '
    public static int PRINTABLE_ASCII_MAX = 0x7E; // '~'

    public static boolean isPrintableAscii(int c) {
        return c >= PRINTABLE_ASCII_MIN && c <= PRINTABLE_ASCII_MAX;
    }

    public static String bytesToHex(byte[] data) {
        return bytesToHex(data, 0, data.length);
    }

    public static String bytesToHex(byte[] data, int offset, int length) {
        if (length <= 0) {
            return "";
        }

        StringBuilder hex = new StringBuilder();
        for (int i = offset; i < offset + length; i++) {
            hex.append(String.format(" %02X", data[i] % 0xFF));
        }
        hex.deleteCharAt(0);
        return hex.toString();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static String hexToString(byte[] raw) {
        String data = bytesToHex(raw);
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < data.length(); i += 2) {
            String str = data.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    public static String bytesToAsciiMaybe(byte[] data) {
        return bytesToAsciiMaybe(data, 0, data.length);
    }

    public static String bytesToAsciiMaybe(byte[] data, int offset, int length) {
        StringBuilder ascii = new StringBuilder();
        boolean zeros = false;
        for (int i = offset; i < offset + length; i++) {
            int c = data[i] & 0xFF;
            if (isPrintableAscii(c)) {
                if (zeros) {
//                    return null;
                }
                ascii.append((char) c);
            } else if (c == 0) {
                zeros = true;
            } else {
//                return null;
            }
        }
        return ascii.toString();
    }

    public static byte[] hexToBytes(String hex) {
        ByteArrayBuffer bytes = new ByteArrayBuffer(hex.length() / 2);
        for (int i = 0; i < hex.length(); i++) {
            if (hex.charAt(i) == ' ') {
                continue;
            }

            String hexByte;
            if (i + 1 < hex.length()) {
                hexByte = hex.substring(i, i + 2).trim();
                i++;
            } else {
                hexByte = hex.substring(i, i + 1);
            }

            bytes.append(Integer.parseInt(hexByte, 16));
        }
        return bytes.buffer();
    }
}
