package com.validyne.blutransducer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts F to C and pressure output units
 */
@SuppressWarnings("all")
public class UnitConversionHelper {
    public static String fahrenheitToCelsius(char temperatureUnit, double temperature) {
        double convertedValue;

        if (temperatureUnit == 'C') {
            convertedValue = (temperature - 32) * 5 / 9;
        } else {
            convertedValue = temperature;
        }
        return String.valueOf(BigDecimal.valueOf(convertedValue).setScale(1, RoundingMode.HALF_UP).doubleValue());
    }

    public static String convertPressure(String factoryUnits, String convertToUnits, double realPressure) {
        double convertedValue;
        double convertToPSI;

        if (factoryUnits.equals("psi")) {
            convertToPSI = 1.0;
        } else if (factoryUnits.equals("inHG")) {
            convertToPSI = 0.4897707;
        } else if (factoryUnits.equals("inH2O")) {
            convertToPSI = 0.03606233;
        } else if (factoryUnits.equals("ftH2O")) {
            convertToPSI = 0.4327480;
        } else if (factoryUnits.equals("mmH2O")) {
            convertToPSI = 0.001419777;
        } else if (factoryUnits.equals("cmH2O")) {
            convertToPSI = 0.01419777;
        } else if (factoryUnits.equals("mH2O")) {
            convertToPSI = 1.419777;
        } else if (factoryUnits.equals("mTorr")) {
            convertToPSI =  0.00001933672;
        } else if (factoryUnits.equals("Torr")) {
            convertToPSI = 0.01933672;
        } else if (factoryUnits.equals("atm")) {
            convertToPSI = 14.69595;
        } else if (factoryUnits.equals("mbar")) {
            convertToPSI = 0.01450377;
        } else if (factoryUnits.equals("bar")) {
            convertToPSI = 14.50377;
        } else if (factoryUnits.equals("Pa")) {
            convertToPSI = 0.0001450377;
        } else if (factoryUnits.equals("kPa")) {
            convertToPSI = 0.1450377;
        } else if (factoryUnits.equals("MPa")) {
            convertToPSI = 145.0377;
        } else {
            return "";
        }

        if (convertToUnits.equals("psi")) {
            convertedValue = convertToPSI * realPressure * 1.0;
        } else if (convertToUnits.equals("inHG")) {
            convertedValue = convertToPSI * realPressure * 2.041772;
        } else if (convertToUnits.equals("inH2O")) {
            convertedValue = convertToPSI * realPressure * 27.72977;
        } else if (convertToUnits.equals("ftH2O")) {
            convertedValue = convertToPSI * realPressure * 2.310814;
        } else if (convertToUnits.equals("mmH2O")) {
            convertedValue = convertToPSI * realPressure * 704.336;
        } else if (convertToUnits.equals("cmH2O")) {
            convertedValue = convertToPSI * realPressure * 70.4336;
        } else if (convertToUnits.equals("mH2O")) {
            convertedValue = convertToPSI * realPressure * 0.704336;
        } else if (convertToUnits.equals("mTorr")) {
            convertedValue =  convertToPSI * realPressure * 51715.08;
        } else if (convertToUnits.equals("Torr")) {
            convertedValue = convertToPSI * realPressure * 51.71508;
        } else if (convertToUnits.equals("atm")) {
            convertedValue = convertToPSI * realPressure * 0.06804596;
        } else if (convertToUnits.equals("mbar")) {
            convertedValue = convertToPSI * realPressure * 68.94757;
        } else if (convertToUnits.equals("bar")) {
            convertedValue = convertToPSI * realPressure * 0.06894757;
        } else if (convertToUnits.equals("Pa")) {
            convertedValue = convertToPSI * realPressure * 6894.757;
        } else if (convertToUnits.equals("kPa")) {
            convertedValue = convertToPSI * realPressure * 6.894757;
        } else if (convertToUnits.equals("MPa")) {
            convertedValue = convertToPSI * realPressure * 0.006894757;
        } else {
            return "";
        }
        return scientificNotation(convertedValue);
    }

    private static String scientificNotation(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        String result;

        if (String.valueOf(value).length() > 7) {
            result = String.format("%.4G", bd);
        } else {
            result = String.valueOf(bd);
        }

        return result;
    }
}
