/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.megad.i2c;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class I2C {

    private Logger logger = LoggerFactory.getLogger(I2C.class);
    String host;
    String SDA;
    String SCL;
    String password;
    int LOW = 0;
    int HIGH = 1;

    HashMap<String, int[]> symbol = new HashMap<String, int[]>();

    public I2C(String host, String password, String port, String scl) {
        this.host = host;
        this.SDA = port;
        this.SCL = scl;
        this.password = password;

    }

    private void i2c_stop() {
        logger.debug("stopping...");
        String request = "http://" + this.host + "/" + this.password + "/?" + "cmd=" + this.SDA + ":" + LOW + ";"
                + this.SCL + ":" + HIGH + ";" + this.SDA + ":" + HIGH;
        sendCommand(request);

    }

    private void i2c_init() {
        String request = "http://" + this.host + "/" + this.password + "/?" + "cmd=" + this.SCL + ":" + HIGH + ";"
                + this.SDA + ":" + HIGH;
        sendCommand(request);
    }

    private void i2c_send(String data) {
        logger.debug("sending...");
        String request = "http://" + this.host + "/" + this.password + "/?" + "pt=" + this.SDA + "&i2c="
                + Integer.parseInt(data, 16) + "&scl=" + this.SCL + ":1;" + this.SCL + ":0;";
        sendCommand(request);

        // file_get_contents(MD."pt=".SDA."&i2c=".hexdec($data)."&scl=".SCL.":1;".SCL.":0;");

    }

    private void i2c_start() {
        logger.debug("start...");
        String request = "http://" + this.host + "/" + this.password + "/?" + "cmd=" + this.SDA + ":" + LOW + ";"
                + this.SCL + ":" + LOW;
        sendCommand(request);
    }

    private void sendCommand(String Result) {

        // logger.debug(Result);
        HttpURLConnection con;

        URL MegaURL;

        try {
            MegaURL = new URL(Result);
            con = (HttpURLConnection) MegaURL.openConnection();
            // optional default is GET
            // con.setReadTimeout(500);
            // con.setConnectTimeout(500);
            con.setRequestMethod("GET");

            // add request header
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (con.getResponseCode() == 200) {
                logger.debug("OK");
            } else {
                logger.debug(con.getResponseMessage());
            }
            con.disconnect();
        } catch (MalformedURLException e) {
            logger.error("1" + e, e);
        } catch (ProtocolException e) {
            logger.error("2" + e, e);
        } catch (IOException e) {
            logger.error("Connect to megadevice " + host + " error: " + e.getLocalizedMessage());
        }

    }

    private void display_init() {
        logger.debug("init...");
        i2c_stop();
        i2c_init();
        i2c_start();

        i2c_send("78");
        i2c_send("00");

        i2c_send("AF"); // Display ON

        i2c_send("D5"); // Display Clock ?
        i2c_send("80"); // Default 80

        i2c_send("81"); // Contrast
        i2c_send("EE");

        i2c_send("8D"); // Charge Pump (иначе не включится!)
        i2c_send("14");
        i2c_send("AF"); // Display ON

        i2c_send("A1"); // Set Segment Re-map // Default A0 слева направо или справа на лево
        i2c_send("C8"); // Set COM Output // Default C0 сверху вниз или снизу вверх

        i2c_send("A6");

        i2c_stop();

    }

    private void clear_display() {
        logger.debug("clear...");
        i2c_start();
        i2c_send("78");
        i2c_send("00");
        i2c_send("20");
        i2c_send("00");
        i2c_send("21");
        i2c_send("00");
        i2c_send("7F");
        i2c_send("22");
        i2c_send("00");
        i2c_send("07");

        i2c_stop();
        i2c_start();

        i2c_send("78");
        i2c_send("40");

        logger.debug("Clearing screen");

        for (int i = 0; i < 1024; i++) {
            i2c_send("00");
        }

        logger.debug("Stopping...");
        i2c_stop();
        logger.debug("Stopped...");

    }

    public void prepare_display() {
        logger.debug("preparing...");
        display_init();
        clear_display();
        write_text("test", "default", 0, 0);

    }

    public void write_text(String string, String font, int column, int page) {
        logger.debug("writing...");
        generateFonts(font);
        String splitByWordsData[] = string.split(" ");

        i2c_start();
        i2c_send("78");
        i2c_send("00");
        i2c_send("20");
        i2c_send("41");
        i2c_send("21");
        logger.debug("column...");
        i2c_send(Integer.toHexString(column));
        i2c_send("7F");
        i2c_send("22");
        logger.debug("page...");
        i2c_send(Integer.toHexString(page));
        i2c_send(Integer.toHexString(page + 1));

        i2c_stop();
        i2c_start();
        logger.debug("continue writing...");
        i2c_send("78");
        i2c_send("40");
        logger.debug("Sending text...");

        for (int j = 0; j < splitByWordsData.length; j++) {
            int flag = 1;

            int[] words = printString(splitByWordsData[j]);
            if (words != null) {
                int wordsLenghthArray = words.length;
                for (int i = 0; i < wordsLenghthArray; i++) {
                    logger.debug("extract..." + words[i + flag]);
                    i2c_send(Integer.toHexString(words[i + flag]));
                    flag = flag * -1;
                }
            }

            i2c_send("00");
            i2c_send("00");
        }

        logger.debug("Sendet text...");

    }

    private int @Nullable [] printString(String string) {

        int result[] = null;

        logger.debug("printing " + string);

        String[] caracter = string.split("");

        for (int i = 0; i < string.length(); i++) {

            logger.debug("printing chars..." + Hex.encodeHexString(StringUtils.getBytesUtf8(caracter[i])).toString());

            result = ArrayUtils.addAll(result,
                    symbol.get(Hex.encodeHexString(StringUtils.getBytesUtf8(caracter[i])).toString()));
            logger.debug("found chars..." + result.length);

        }

        return result;
    }

    private void generateFonts(String font) {

        switch (font) {
            case "verdana_8":

                break;

            default:
                symbol.put(Integer.toHexString('!').toString(), new int[] { 0x2F, 0xC0 });

                symbol.put(Integer.toHexString('"').toString(), new int[] { 0x00, 0xE0, 0x00, 0x00, 0x00, 0xE0 });
                /*
                 * '#' => array ( 0x08,0x00,0x39,0x00,0x0F,0x00,0x39,0xC0,0x0F,0x00,0x09,0xC0,0x01,0x00),
                 * '$' => array ( 0x23,0x00,0x24,0x80,0xFF,0xE0,0x24,0x80,0x18,0x80),
                 * '%' => array (
                 * 0x01,0x80,0x02,0x40,0x02,0x40,0x31,0x80,0x0C,0x00,0x03,0x00,0x18,0xC0,0x24,0x00,0x24,0x00,0x18,0x00),
                 * '&' => array ( 0x1D,0x80,0x22,0x40,0x22,0x40,0x25,0x80,0x18,0x00,0x16,0x00,0x20,0x00),
                 * '\'' => array ( 0x00,0xE0),
                 * '(' => array ( 0x1F,0x00,0x60,0xC0,0x80,0x20),
                 * ')' => array ( 0x80,0x20,0x60,0xC0,0x1F,0x00),
                 * '*' => array ( 0x01,0x40,0x00,0x80,0x03,0xE0,0x00,0x80,0x01,0x40),
                 * '+' => array ( 0x04,0x00,0x04,0x00,0x04,0x00,0x3F,0x80,0x04,0x00,0x04,0x00,0x04,0x00),
                 * ',' => array ( 0x80,0x00,0x70,0x00),
                 * '-' => array ( 0x04,0x00,0x04,0x00,0x04,0x00),
                 * '.' => array ( 0x30,0x00),
                 * '/' => array ( 0x60,0x00,0x18,0x00,0x06,0x00,0x01,0x80,0x00,0x60),
                 * '1' => array ( 0x20,0x80,0x20,0x80,0x3F,0xC0,0x20,0x00,0x20,0x00),
                 * '2' => array ( 0x30,0x80,0x28,0x40,0x24,0x40,0x22,0x40,0x21,0x80),
                 * '3' => array ( 0x10,0x80,0x20,0x40,0x22,0x40,0x22,0x40,0x1D,0x80),
                 * '4' => array ( 0x0C,0x00,0x0A,0x00,0x09,0x00,0x08,0x80,0x3F,0xC0,0x08,0x00),
                 * '5' => array ( 0x13,0xC0,0x22,0x40,0x22,0x40,0x22,0x40,0x1C,0x40),
                 * '6' => array ( 0x1F,0x00,0x22,0x80,0x22,0x40,0x22,0x40,0x1C,0x00),
                 * '7' => array ( 0x00,0x40,0x30,0x40,0x0C,0x40,0x03,0x40,0x00,0xC0),
                 * '8' => array ( 0x1D,0x80,0x22,0x40,0x22,0x40,0x22,0x40,0x1D,0x80),
                 * '9' => array ( 0x03,0x80,0x24,0x40,0x24,0x40,0x14,0x40,0x0F,0x80),
                 * '0' => array ( 0x1F,0x80,0x20,0x40,0x20,0x40,0x20,0x40,0x1F,0x80),
                 * ':' => array ( 0x33,0x00),
                 * ';' => array ( 0x80,0x00,0x73,0x00),
                 * '<' => array ( 0x04,0x00,0x04,0x00,0x0A,0x00,0x0A,0x00,0x11,0x00,0x11,0x00),
                 * '>' => array ( 0x11,0x00,0x11,0x00,0x0A,0x00,0x0A,0x00,0x04,0x00,0x04,0x00),
                 * '@' => array ( 0x1F,0x00,0x20,0x80,0x4E,0x40,0x51,0x40,0x51,0x40,0x4F,0x40,0x10,0x40,0x0F,0x80),
                 * 'A' => array ( 0x38,0x00,0x0F,0x00,0x08,0xC0,0x08,0xC0,0x0F,0x00,0x38,0x00),
                 * 'B' => array ( 0x3F,0xC0,0x22,0x40,0x22,0x40,0x22,0x40,0x23,0x80,0x1C,0x00),
                 * 'C' => array ( 0x0F,0x00,0x10,0x80,0x20,0x40,0x20,0x40,0x20,0x40,0x20,0x40,0x10,0x80),
                 * 'D' => array ( 0x3F,0xC0,0x20,0x40,0x20,0x40,0x20,0x40,0x20,0x40,0x10,0x80,0x0F,0x00),
                 * 'E' => array ( 0x3F,0xC0,0x22,0x40,0x22,0x40,0x22,0x40,0x22,0x40),
                 * 'F' => array ( 0x3F,0xC0,0x02,0x40,0x02,0x40,0x02,0x40,0x00,0x40),
                 * 'G' => array ( 0x0F,0x00,0x10,0x80,0x20,0x40,0x20,0x40,0x24,0x40,0x24,0x40,0x1C,0x80),
                 * 'H' => array ( 0x3F,0xC0,0x02,0x00,0x02,0x00,0x02,0x00,0x02,0x00,0x3F,0xC0),
                 * 'I' => array ( 0x20,0x40,0x3F,0xC0,0x20,0x40),
                 * 'J' => array ( 0x20,0x00,0x20,0x40,0x20,0x40,0x1F,0xC0),
                 * 'K' => array ( 0x3F,0xC0,0x04,0x00,0x06,0x00,0x09,0x00,0x10,0x80,0x20,0x40),
                 * 'L' => array ( 0x3F,0xC0,0x20,0x00,0x20,0x00,0x20,0x00,0x20,0x00),
                 * 'M' => array ( 0x3F,0xC0,0x00,0xC0,0x03,0x00,0x0C,0x00,0x03,0x00,0x00,0xC0,0x3F,0xC0),
                 * 'N' => array ( 0x3F,0xC0,0x00,0xC0,0x03,0x00,0x0C,0x00,0x30,0x00,0x3F,0xC0),
                 * 'O' => array ( 0x0F,0x00,0x10,0x80,0x20,0x40,0x20,0x40,0x20,0x40,0x10,0x80,0x0F,0x00),
                 * 'P' => array ( 0x3F,0xC0,0x04,0x40,0x04,0x40,0x04,0x40,0x03,0x80),
                 * 'Q' => array ( 0x0F,0x00,0x10,0x80,0x20,0x40,0x20,0x40,0x60,0x40,0x90,0x80,0x8F,0x00),
                 * 'R' => array ( 0x3F,0xC0,0x04,0x40,0x04,0x40,0x0C,0x40,0x13,0x80,0x20,0x00),
                 * 'S' => array ( 0x11,0x80,0x22,0x40,0x22,0x40,0x24,0x40,0x24,0x40,0x18,0x80),
                 */
                symbol.put(Integer.toHexString('T').toString(), new int[] { 0x00, 0x40, 0x00, 0x40, 0x00, 0x40, 0x3F,
                        0xC0, 0x00, 0x40, 0x00, 0x40, 0x00, 0x40 });

                /*
                 * 'U' => array ( 0x1F,0xC0,0x20,0x00,0x20,0x00,0x20,0x00,0x20,0x00,0x1F,0xC0),
                 * 'V' => array ( 0x01,0xC0,0x0E,0x00,0x30,0x00,0x30,0x00,0x0E,0x00,0x01,0xC0),
                 * 'W' => array (
                 * 0x00,0xC0,0x0F,0x00,0x30,0x00,0x0F,0x00,0x00,0xC0,0x0F,0x00,0x30,0x00,0x0F,0x00,0x00,0xC0),
                 * 'X' => array ( 0x30,0xC0,0x09,0x00,0x06,0x00,0x06,0x00,0x09,0x00,0x30,0xC0),
                 * 'Y' => array ( 0x00,0x40,0x00,0x80,0x01,0x00,0x3E,0x00,0x01,0x00,0x00,0x80,0x00,0x40),
                 * 'Z' => array ( 0x30,0x40,0x28,0x40,0x24,0x40,0x22,0x40,0x21,0x40,0x20,0xC0),
                 * '[' => array ( 0xFF,0xE0,0x80,0x20,0x80,0x20),
                 * '\\' => array ( 0x00,0x60,0x01,0x80,0x06,0x00,0x18,0x00,0x60,0x00),
                 * ']' => array ( 0x80,0x20,0x80,0x20,0xFF,0xE0),
                 * '^' => array ( 0x02,0x00,0x01,0x00,0x00,0x80,0x00,0x40,0x00,0x80,0x01,0x00,0x02,0x00),
                 * '_' => array ( 0x80,0x00,0x80,0x00,0x80,0x00,0x80,0x00,0x80,0x00,0x80,0x00,0x80,0x00),
                 * '`' => array ( 0x00,0x20,0x00,0x40),
                 * 'a' => array ( 0x18,0x00,0x25,0x00,0x25,0x00,0x25,0x00,0x3E,0x00),
                 * 'b' => array ( 0x3F,0xE0,0x21,0x00,0x21,0x00,0x21,0x00,0x1E,0x00),
                 * 'c' => array ( 0x1E,0x00,0x21,0x00,0x21,0x00,0x21,0x00,0x12,0x00),
                 * 'd' => array ( 0x1E,0x00,0x21,0x00,0x21,0x00,0x21,0x00,0x3F,0xE0),
                 */
                symbol.put(Integer.toHexString('e').toString(),
                        new int[] { 0x1E, 0x00, 0x25, 0x00, 0x25, 0x00, 0x25, 0x00, 0x16, 0x00 });
                /*
                 * *
                 * 'f' => array ( 0x01,0x00,0x3F,0xC0,0x01,0x20,0x01,0x20),
                 * 'g' => array ( 0x1E,0x00,0xA1,0x00,0xA1,0x00,0xA1,0x00,0x7F,0x00),
                 * 'h' => array ( 0x3F,0xE0,0x01,0x00,0x01,0x00,0x01,0x00,0x3E,0x00),
                 * 'i' => array ( 0x3F,0x20),
                 * 'j' => array ( 0x80,0x00,0x81,0x00,0x7F,0x20),
                 * 'k' => array ( 0x3F,0xE0,0x08,0x00,0x0C,0x00,0x12,0x00,0x21,0x00),
                 * 'l' => array ( 0x3F,0xE0),
                 * 'm' => array (
                 * 0x3F,0x00,0x01,0x00,0x01,0x00,0x01,0x00,0x3E,0x00,0x01,0x00,0x01,0x00,0x01,0x00,0x3E,0x00),
                 * 'n' => array ( 0x3F,0x00,0x01,0x00,0x01,0x00,0x01,0x00,0x3E,0x00),
                 * 'o' => array ( 0x1E,0x00,0x21,0x00,0x21,0x00,0x21,0x00,0x1E,0x00),
                 * 'p' => array ( 0xFF,0x00,0x21,0x00,0x21,0x00,0x21,0x00,0x1E,0x00),
                 * 'q' => array ( 0x1E,0x00,0x21,0x00,0x21,0x00,0x21,0x00,0xFF,0x00),
                 * 'r' => array ( 0x3F,0x00,0x02,0x00,0x01,0x00,0x01,0x00),
                 */
                symbol.put(Integer.toHexString('s').toString(),
                        new int[] { 0x26, 0x00, 0x25, 0x00, 0x29, 0x00, 0x19, 0x00 });
                symbol.put(Integer.toHexString('t').toString(),
                        new int[] { 0x01, 0x00, 0x1F, 0xC0, 0x21, 0x00, 0x21, 0x00 });
                /*
                 * 'u' => array ( 0x1F,0x00,0x20,0x00,0x20,0x00,0x20,0x00,0x3F,0x00),
                 * 'v' => array ( 0x03,0x00,0x0C,0x00,0x30,0x00,0x0C,0x00,0x03,0x00),
                 * 'w' => array ( 0x0F,0x00,0x30,0x00,0x0C,0x00,0x03,0x00,0x0C,0x00,0x30,0x00,0x0F,0x00),
                 * 'x' => array ( 0x21,0x00,0x12,0x00,0x0C,0x00,0x12,0x00,0x21,0x00),
                 * 'y' => array ( 0x01,0x00,0x8E,0x00,0x70,0x00,0x0E,0x00,0x01,0x00),
                 * 'z' => array ( 0x31,0x00,0x29,0x00,0x25,0x00,0x23,0x00),
                 * '|' => array ( 0xFF,0xE0),
                 * '~' => array ( 0x0C,0x00,0x02,0x00,0x02,0x00,0x04,0x00,0x08,0x00,0x08,0x00,0x06,0x00),
                 * 'Ё' => array ( 0x3F,0xC0,0x22,0x50,0x22,0x40,0x22,0x50,0x22,0x40),
                 * 'А' => array ( 0x38,0x00,0x0F,0x00,0x08,0xC0,0x08,0xC0,0x0F,0x00,0x38,0x00),
                 * 'Б' => array ( 0x3F,0xC0,0x22,0x40,0x22,0x40,0x22,0x40,0x22,0x40,0x1C,0x00),
                 * 'В' => array ( 0x3F,0xC0,0x22,0x40,0x22,0x40,0x22,0x40,0x23,0x80,0x1C,0x00),
                 * 'Г' => array ( 0x3F,0xC0,0x00,0x40,0x00,0x40,0x00,0x40,0x00,0x40),
                 * 'Д' => array ( 0xE0,0x00,0x38,0x00,0x27,0xC0,0x20,0x40,0x20,0x40,0x3F,0xC0,0xE0,0x00),
                 */
                symbol.put(Hex.encodeHexString("Е".getBytes()).toString(),
                        new int[] { 0x3F, 0xC0, 0x22, 0x40, 0x22, 0x40, 0x22, 0x40, 0x22, 0x40 });
                /*
                 * 'Ж' => array (
                 * 0x20,0x40,0x10,0x40,0x09,0x80,0x06,0x00,0x3F,0xC0,0x06,0x00,0x09,0x80,0x10,0x40,0x20,0x40),
                 * 'З' => array ( 0x10,0x80,0x20,0x40,0x22,0x40,0x22,0x40,0x22,0x40,0x1D,0x80),
                 * 'И' => array ( 0x3F,0xC0,0x10,0x00,0x0C,0x00,0x03,0x00,0x00,0x80,0x3F,0xC0),
                 * 'Й' => array ( 0x3F,0xC0,0x10,0x08,0x0C,0x10,0x03,0x10,0x00,0x88,0x3F,0xC0),
                 * 'К' => array ( 0x3F,0xC0,0x02,0x00,0x06,0x00,0x09,0x80,0x10,0x40,0x20,0x40),
                 * 'Л' => array ( 0x20,0x00,0x20,0x00,0x1F,0xC0,0x00,0x40,0x00,0x40,0x00,0x40,0x3F,0xC0),
                 * 'М' => array ( 0x3F,0xC0,0x00,0xC0,0x03,0x00,0x0C,0x00,0x03,0x00,0x00,0xC0,0x3F,0xC0),
                 * 'Н' => array ( 0x3F,0xC0,0x02,0x00,0x02,0x00,0x02,0x00,0x02,0x00,0x3F,0xC0),
                 * 'О' => array ( 0x0F,0x00,0x10,0x80,0x20,0x40,0x20,0x40,0x20,0x40,0x10,0x80,0x0F,0x00),
                 * 'П' => array ( 0x3F,0xC0,0x00,0x40,0x00,0x40,0x00,0x40,0x00,0x40,0x3F,0xC0),
                 * 'Р' => array ( 0x3F,0xC0,0x04,0x40,0x04,0x40,0x04,0x40,0x03,0x80),
                 * 'С' => array ( 0x0F,0x00,0x10,0x80,0x20,0x40,0x20,0x40,0x20,0x40,0x20,0x40,0x10,0x80),
                 */
                symbol.put(Hex.encodeHexString("Т".getBytes()).toString(), new int[] { 0x00, 0x40, 0x00, 0x40, 0x00,
                        0x40, 0x3F, 0xC0, 0x00, 0x40, 0x00, 0x40, 0x00, 0x40 });

                /*
                 * 'У' => array ( 0x20,0xC0,0x23,0x00,0x1C,0x00,0x0C,0x00,0x03,0x00,0x00,0xC0),
                 * 'Ф' => array ( 0x0F,0x00,0x10,0x80,0x10,0x80,0x3F,0xC0,0x10,0x80,0x10,0x80,0x0F,0x00),
                 * 'Х' => array ( 0x30,0xC0,0x09,0x00,0x06,0x00,0x06,0x00,0x09,0x00,0x30,0xC0),
                 * 'Ц' => array ( 0x3F,0xC0,0x20,0x00,0x20,0x00,0x20,0x00,0x20,0x00,0x3F,0xC0,0xE0,0x00),
                 * 'Ч' => array ( 0x03,0xC0,0x04,0x00,0x04,0x00,0x04,0x00,0x04,0x00,0x3F,0xC0),
                 * 'Ш' => array (
                 * 0x3F,0xC0,0x20,0x00,0x20,0x00,0x20,0x00,0x3F,0xC0,0x20,0x00,0x20,0x00,0x20,0x00,0x3F,0xC0),
                 * 'Щ' => array (
                 * 0x3F,0xC0,0x20,0x00,0x20,0x00,0x20,0x00,0x3F,0xC0,0x20,0x00,0x20,0x00,0x20,0x00,0x3F,0xC0,0xE0,0x00),
                 * 'Ъ' => array ( 0x00,0x40,0x00,0x40,0x3F,0xC0,0x22,0x00,0x22,0x00,0x22,0x00,0x22,0x00,0x1C,0x00),
                 * 'Ы' => array ( 0x3F,0xC0,0x22,0x00,0x22,0x00,0x22,0x00,0x22,0x00,0x1C,0x00,0x00,0x00,0x3F,0xC0),
                 * 'Ь' => array ( 0x3F,0xC0,0x22,0x00,0x22,0x00,0x22,0x00,0x1C,0x00),
                 * 'Э' => array ( 0x10,0x80,0x22,0x40,0x22,0x40,0x22,0x40,0x22,0x40,0x12,0x80,0x0F,0x00),
                 * 'Ю' => array (
                 * 0x3F,0xC0,0x02,0x00,0x0F,0x00,0x10,0x80,0x20,0x40,0x20,0x40,0x20,0x40,0x10,0x80,0x0F,0x00),
                 * 'Я' => array ( 0x20,0x00,0x13,0x80,0x0C,0x40,0x04,0x40,0x04,0x40,0x04,0x40,0x3F,0xC0),
                 * 'а' => array ( 0x18,0x00,0x25,0x00,0x25,0x00,0x25,0x00,0x3E,0x00),
                 * 'б' => array ( 0x1F,0x80,0x21,0x40,0x21,0x20,0x21,0x20,0x1E,0x20),
                 * 'в' => array ( 0x3F,0x00,0x25,0x00,0x25,0x00,0x25,0x00,0x1A,0x00),
                 * 'г' => array ( 0x3F,0x00,0x01,0x00,0x01,0x00,0x01,0x00),
                 * 'д' => array ( 0x60,0x00,0x30,0x00,0x2F,0x00,0x21,0x00,0x21,0x00,0x3F,0x00,0x60,0x00),
                 */

                symbol.put(Hex.encodeHexString("е".getBytes()).toString(),
                        new int[] { 0x1E, 0x00, 0x25, 0x00, 0x25, 0x00, 0x25, 0x00, 0x16, 0x00 });
                /*
                 * 'ж' => array ( 0x21,0x00,0x12,0x00,0x0C,0x00,0x3F,0x00,0x0C,0x00,0x12,0x00,0x21,0x00),
                 * 'з' => array ( 0x21,0x00,0x25,0x00,0x25,0x00,0x1A,0x00),
                 * 'и' => array ( 0x3F,0x00,0x08,0x00,0x04,0x00,0x02,0x00,0x3F,0x00),
                 * 'й' => array ( 0x3F,0x20,0x08,0x40,0x04,0x40,0x02,0x40,0x3F,0x20),
                 * 'к' => array ( 0x3F,0x00,0x04,0x00,0x0A,0x00,0x11,0x00,0x21,0x00),
                 * 'л' => array ( 0x20,0x00,0x1F,0x00,0x01,0x00,0x01,0x00,0x01,0x00,0x3F,0x00),
                 * 'м' => array ( 0x3F,0x00,0x02,0x00,0x0C,0x00,0x02,0x00,0x3F,0x00),
                 * 'н' => array ( 0x3F,0x00,0x04,0x00,0x04,0x00,0x04,0x00,0x3F,0x00),
                 * 'о' => array ( 0x1E,0x00,0x21,0x00,0x21,0x00,0x21,0x00,0x1E,0x00),
                 * 'п' => array ( 0x3F,0x00,0x01,0x00,0x01,0x00,0x01,0x00,0x3F,0x00),
                 * 'р' => array ( 0xFF,0x00,0x21,0x00,0x21,0x00,0x21,0x00,0x1E,0x00),
                 */
                symbol.put(Hex.encodeHexString("с".getBytes()).toString(),
                        new int[] { 0x1E, 0x00, 0x21, 0x00, 0x21, 0x00, 0x21, 0x00, 0x12, 0x00 });
                symbol.put(Hex.encodeHexString("т".getBytes()).toString(),
                        new int[] { 0x01, 0x00, 0x01, 0x00, 0x3F, 0x00, 0x01, 0x00, 0x01, 0x00 });

                /*
                 * 'у' => array ( 0x01,0x00,0x8E,0x00,0x70,0x00,0x0E,0x00,0x01,0x00),
                 * 'ф' => array ( 0x1E,0x00,0x21,0x00,0x21,0x00,0xFF,0xE0,0x21,0x00,0x21,0x00,0x1E,0x00),
                 * 'х' => array ( 0x21,0x00,0x12,0x00,0x0C,0x00,0x12,0x00,0x21,0x00),
                 * 'ц' => array ( 0x3F,0x00,0x20,0x00,0x20,0x00,0x20,0x00,0x3F,0x00,0x60,0x00),
                 * 'ч' => array ( 0x07,0x00,0x08,0x00,0x08,0x00,0x08,0x00,0x3F,0x00),
                 * 'ш' => array ( 0x3F,0x00,0x20,0x00,0x20,0x00,0x3F,0x00,0x20,0x00,0x20,0x00,0x3F,0x00),
                 * 'щ' => array ( 0x3F,0x00,0x20,0x00,0x20,0x00,0x3F,0x00,0x20,0x00,0x20,0x00,0x3F,0x00,0x60,0x00),
                 * 'ъ' => array ( 0x01,0x00,0x01,0x00,0x3F,0x00,0x24,0x00,0x24,0x00,0x24,0x00,0x18,0x00),
                 * 'ы' => array ( 0x3F,0x00,0x24,0x00,0x24,0x00,0x24,0x00,0x18,0x00,0x00,0x00,0x3F,0x00),
                 * 'ь' => array ( 0x3F,0x00,0x24,0x00,0x24,0x00,0x24,0x00,0x18,0x00),
                 * 'э' => array ( 0x12,0x00,0x21,0x00,0x25,0x00,0x25,0x00,0x1E,0x00),
                 * 'ю' => array ( 0x3F,0x00,0x04,0x00,0x04,0x00,0x1E,0x00,0x21,0x00,0x21,0x00,0x21,0x00,0x1E,0x00),
                 * 'я' => array ( 0x26,0x00,0x19,0x00,0x09,0x00,0x09,0x00,0x3F,0x00),
                 * 'ё' => array ( 0x1E,0x00,0x25,0x40,0x25,0x00,0x25,0x40,0x16,0x00),
                 * ' ' => array ( 0x00,0x00,0x00,0x00,0x00,0x00)
                 *
                 */

                break;
        }

    }

}