package com.datecs.pinpaddemo.processor;

import com.datecs.crypto.DUKPT;
import com.datecs.crypto.SHA256;
import com.datecs.crypto.TR31;
import com.datecs.crypto.TripleDES;
import com.datecs.emv.EmvTags;
import com.datecs.pinpad.Pinpad;
import com.datecs.tlv.BerTlv;
import com.datecs.tlv.Tag;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Transaction server emulator
 */
public class Backend {

    // TMK - Terminal Master Key
    // Note: This key is valid only for terminals with debug firmware.
    private static final byte[] TMK = {
            (byte)0x1A, (byte)0xC4, (byte)0xF2, (byte)0x34,
            (byte)0x79, (byte)0xCD, (byte)0x8F, (byte)0x23,
            (byte)0x0B, (byte)0xC4, (byte)0x9D, (byte)0x2C,
            (byte)0x98, (byte)0xC8, (byte)0x91, (byte)0xEA
    };

    // Key custodian 1 (Part 1 of 2)
    // TYPE: TR31 Master KEK
    // KEY: 4E32 F4F7 1D81 7A21 1971 5806 F5A2 0998
    // KCV: 1BD69F
    private static final byte[] TR31_MASTER_KEK_PART1 = {
            (byte)0x4E, (byte)0x32, (byte)0xF4, (byte)0xF7,
            (byte)0x1D, (byte)0x81, (byte)0x7A, (byte)0x21,
            (byte)0x19, (byte)0x71, (byte)0x58, (byte)0x06,
            (byte)0xF5, (byte)0xA2, (byte)0x09, (byte)0x98
    };

    // Key custodian 2 (Part 2 of 2)
    // TYPE: TR31 Master KEK
    // KEY: 4E32 F4F7 1D81 7A21 1971 5806 F5A2 0998
    // KCV: 43A352
    private static final byte[] TR31_MASTER_KEK_PART2 = {
            (byte)0x4B, (byte)0xD6, (byte)0x69, (byte)0x96,
            (byte)0x97, (byte)0xBB, (byte)0xFA, (byte)0x34,
            (byte)0x7F, (byte)0x75, (byte)0x90, (byte)0x4D,
            (byte)0xBE, (byte)0x79, (byte)0x6B, (byte)0x7B
    };

    // Derived TR31 Master Key
    // KEY: 05E4 9D61 8A3A 8015 6604 C84B 4BDB 62E3
    // KCV: 3136DD
    private static final byte[] TR31_MASTER_KEK = TR31.deriveKey(
            TR31_MASTER_KEK_PART1,
            TR31_MASTER_KEK_PART2);

    // Key for data encryption.
    private static final byte[] KEY_DATA = {
            (byte)0xB0, (byte)0xB1, (byte)0xB2, (byte)0xB3,
            (byte)0xB4, (byte)0xB5, (byte)0xB6, (byte)0xB7,
            (byte)0xB8, (byte)0xB9, (byte)0xBA, (byte)0xBB,
            (byte)0xBC, (byte)0xBD, (byte)0xBE, (byte)0xBF
    };

    // Key for DUKPT encryption.
    public static final byte[] IPEK = {
            (byte)0xA0, (byte)0xA1, (byte)0xA2, (byte)0xA3,
            (byte)0xA4, (byte)0xA5, (byte)0xA6, (byte)0xA7,
            (byte)0xA8, (byte)0xA9, (byte)0xAA, (byte)0xAB,
            (byte)0xAC, (byte)0xAD, (byte)0xAE, (byte)0xAF
    };
    // Initial DUKPT key serial number (KSN)
    private static final byte[] KSN = {
            (byte)0xFF, (byte)0xFF, (byte)0x00, (byte)0x00, (byte)0x04, (byte)0x00, (byte)0x0B,
            (byte)0x20, (byte)0x00, (byte)0x00
    };

    /**
     * Tags required for ONLINE request.
     */
    public static int[] ONLINE_REQUEST_TAGS = {
      /*      EmvTags.TAG_5F2A_TRANSACTION_CURRENCY_CODE,
            EmvTags.TAG_5F34_APPLICATION_PRIMARY_ACCOUNT_NUMBER_SEQUENCE_NUMBER,
            EmvTags.TAG_82_APPLICATION_INTERCHANGE_PROFILE,
            EmvTags.TAG_95_TERMINAL_VERIFICATION_RESULTS,
            EmvTags.TAG_9A_TRANSACTION_DATE,
            EmvTags.TAG_9C_TRANSACTION_TYPE,
            EmvTags.TAG_9F02_AMOUNT_AUTHORISED,
            EmvTags.TAG_9F03_AMOUNT_OTHER,
            EmvTags.TAG_9F10_ISSUER_APPLICATION_DATA,
            EmvTags.TAG_9F1A_TERMINAL_COUNTRY_CODE,
            EmvTags.TAG_9F26_APPLICATION_CRYPTOGRAM,
            EmvTags.TAG_9F27_CRYPTOGRAM_INFORMATION_DATA,
            EmvTags.TAG_9F36_APPLICATION_TRANSACTION_COUNTER,
            EmvTags.TAG_9F37_UNPREDICTABLE_NUMBER,
            EmvTags.TAG_57_TRACK_2_EQUIVALENT_DATA,
            EmvTags.TAG_5F20_CARDHOLDER_NAME,
            EmvTags.TAG_9F1F_TRACK_1_DISCRETIONARY_DATA,
            EmvTags.TAG_9F07_APPLICATION_USAGE_CONTROL,
            EmvTags.TAG_8E_CARDHOLDER_VERIFICATION_METHOD_LIST,
            EmvTags.TAG_9F0D_ISSUER_ACTION_CODE_DEFAULT,
            EmvTags.TAG_9F0E_ISSUER_ACTION_CODE_DENIAL,
            EmvTags.TAG_9F0F_ISSUER_ACTION_CODE_ONLINE,
            EmvTags.TAG_91_ISSUER_AUTHENTICATION_DATA,
            EmvTags.TAG_9F33_TERMINAL_CAPABILITIES,
            EmvTags.TAG_9F34_CARDHOLDER_VERIFICATION_METHOD_RESULTS,
            EmvTags.TAG_5A_APPLICATION_PRIMARY_ACCOUNT_NUMBER,
            EmvTags.TAG_5F20_CARDHOLDER_NAME,*/
            EmvTags.TAG_57_TRACK_2_EQUIVALENT_DATA,
            EmvTags.TAG_5A_APPLICATION_PRIMARY_ACCOUNT_NUMBER,
            EmvTags.TAG_9F6B_CARD_CVM_LIMIT
    };

    /**
     * Get TR31 master key block.
     *
     * @return key block.
     */
    public static byte[] getMasterKeyBlockTR31() {
        byte[] masterKEK = TR31_MASTER_KEK;
        return TR31.create(TMK, "K1", "T", "X", "01", "E", masterKEK);
    }

    /**
     * Get TR31 Data key block.
     *
     * @return key block.
     */
    public static byte[] getDataKeyBlockTR31() {
        byte[] masterKEK = TR31_MASTER_KEK;
        return TR31.create(masterKEK, "D0", "T", "N", "00", "E", KEY_DATA);
    }

    /**
     * Get TR31 DUKPT key block.
     *
     * @return key block.
     */
    public static byte[] getDUKPTKeyBlockTR31() {
        byte[] masterKEK = TR31_MASTER_KEK;
        return TR31.create(masterKEK, "B1", "T", "X", "00", "E", IPEK, KSN);
    }

    /**
     * Decrypt data.
     * <p>
     * This method is provide for demonstration purpose only.
     *
     * @param data encrypted data.
     *
     * @return decrypted data.
     */

    static public void main2(String[] args) {

        byte[] keydata =  hexStringToByteArray("");

        //byte[] dec = hexStringToByteArray("");
        byte[] dec = hexStringToByteArray("");

        byte[] result = TripleDES.decryptCBC(keydata, dec);

        System.out.println("Decrypted : " + byteArrayToHexString(result));

    }


    static public void main(String[] args) {

        byte[] key1 =  hexStringToByteArray("37b68375fe2c1398f7b3b65dd304fefd");

        //byte[] dec = hexStringToByteArray("");
        byte[] key2 = hexStringToByteArray("153716a1c498525d76cde31f4564f873");

        byte[] result = TR31.deriveKey(key1, key2);

        System.out.println("Decrypted : " + byteArrayToHexString(result));

    }


    public static byte[] decryptData(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        try {
            if (buffer.getInt() == Pinpad.TAGS_FORMAT_DATECS) {
                byte[] tmp = new byte[data.length - 4];
                buffer.get(tmp);

                byte[] dec = TripleDES.decryptCBC(KEY_DATA, tmp);
                buffer = ByteBuffer.wrap(dec);
                buffer.order(ByteOrder.BIG_ENDIAN);

                // Read format identifier
                buffer.getInt();

                // Read data length;
                int dataLength = buffer.getShort() & 0xff;

                // read packet identifier
                buffer.getInt(); // Packet identifier

                // Read random data
                byte[] random = new byte[4];
                buffer.get(random);

                // Read device serial number
                byte[] serial = new byte[16];
                buffer.get(serial);

                // Read tags
                byte[] tags = new byte[dataLength - 24];
                buffer.get(tags);

                // Read SHA256
                byte[] hash = new byte[32];
                buffer.get(hash);

                // Verify packet integrity
                boolean ok = SHA256.verify(hash, dec, 6, dataLength);
                if (ok) {
                    return tags;
                }
            } else {
                throw new IllegalArgumentException("Invalid encryption format");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    private static String byteArrayToHexString(byte[] buffer) {
        char[] tmp = new char[buffer.length * 3];

        for (int i = 0, j = 0; i < buffer.length; i++) {
            int a = (buffer[i] & 0xff) >> 4;
            int b = (buffer[i] & 0x0f);
            tmp[j++] = (char)(a < 10 ? '0' + a : 'A' + a - 10);
            tmp[j++] = (char)(b < 10 ? '0' + b : 'A' + b - 10);
            tmp[j++] = ' ';
        }

        return new String(tmp);
    }

    public static byte[] decryptData(byte[] data,byte[] key) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        try {
            if (buffer.getInt() == Pinpad.TAGS_FORMAT_DATECS) {
                byte[] tmp = new byte[data.length - 4];
                buffer.get(tmp);

                byte[] dec = TripleDES.decryptCBC(key, tmp);
                System.out.println("Decrypted : " + byteArrayToHexString(dec));
                buffer = ByteBuffer.wrap(dec);
                buffer.order(ByteOrder.BIG_ENDIAN);

                // Read format identifier
                buffer.getInt();

                // Read data length;
                int dataLength = buffer.getShort() & 0xff;

                // read packet identifier
                buffer.getInt(); // Packet identifier

                // Read random data
                byte[] random = new byte[4];
                buffer.get(random);

                // Read device serial number
                byte[] serial = new byte[16];
                buffer.get(serial);

                // Read tags
                byte[] tags = new byte[dataLength - 24];
                buffer.get(tags);

                // Read SHA256
                byte[] hash = new byte[32];
                buffer.get(hash);

                // Verify packet integrity
                boolean ok = SHA256.verify(hash, dec, 6, dataLength);
                if (ok) {
                    return tags;
                }
            } else {
                throw new IllegalArgumentException("Invalid encryption format");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Decrypt PIN.
     *
     * @param ksn key serial number.
     * @param pinBlock encrypted PIN block.
     *
     * @return decrypted PIN.
     */
    private static void memcpy(byte[] dst, int dstOffset, byte[] src, int srcOffset, int length) {
        System.arraycopy(src, srcOffset, dst, dstOffset, length);
    }
    private static void memset(byte[] dst, int dstOffset, int value, int length) {
        for (int i = 0; i < length; i++) {
            dst[dstOffset + i] = (byte)value;
        }
    }
    private static void memxor(byte[] output, int outPos, byte[] a, int aPos, byte[] b, int bPos, int len) {
        for (int i = 0; i < len; i++) {
            output[outPos + i] = (byte)((a[aPos + i] & 0xff) ^ (b[bPos + i] & 0xff));
        }
    }
    public static final void encryptDES(byte[] output, int outputOffset, byte[] input, int inputOffset, int length, byte[] desKey, int desKeyOffset) {
        try {
            final SecretKey key = new SecretKeySpec(desKey, desKeyOffset, 8, "DES");
            final IvParameterSpec iv = new IvParameterSpec(new byte[8]);
            final Cipher cipher = Cipher.getInstance("DES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            cipher.doFinal(input, inputOffset, length, output, outputOffset);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static byte[] calculateDerivedKey(byte[] ksn, byte[] ipek) {
        byte[] r8 = new byte[8];
        byte[] r8a = new byte[8];
        byte[] r8b = new byte[8];
        byte[] key = new byte[16];

        memcpy(key, 0, ipek, 0, 16);
        memcpy(r8, 0, ksn, 2, 8 - 2);
        r8[5] &= ~0x1F;

        int ec = ((ksn[ksn.length - 3] & 0x1F) << 16) | ((ksn[ksn.length - 2] & 0xFF) << 8) | (ksn[ksn.length - 1] & 0xFF);
        int sr = 0x100000;

        byte[] pattern = new byte[] { (byte)0xC0, (byte)0xC0, (byte)0xC0, (byte)0xC0, 0x00, 0x00, 0x00, 0x00, (byte)0xC0, (byte)0xC0, (byte)0xC0, (byte)0xC0, 0x00, 0x00, 0x00, 0x00 };

        while (sr != 0) {
            if ((sr & ec) != 0) {
                r8[5] |= sr >> 16;
                r8[6] |= sr >> 8;
                r8[7] |= sr;

                memxor(r8a, 0, key, 8, r8, 0, 8);
                encryptDES(r8a, 0, r8a, 0, 8, key, 0);
                memxor(r8a, 0, r8a, 0, key, 8, 8);
                memxor(key, 0, key, 0, pattern, 0, 16);
                memxor(r8b, 0, key, 8, r8, 0, 8);
                encryptDES(r8b, 0, r8b, 0, 8, key, 0);
                memxor(r8b, 0, r8b, 0, key, 8, 8);
                memcpy(key, 8, r8a, 0, 8);
                memcpy(key, 0, r8b, 0, 8);
            }

            sr>>= 1;
        }

        memset(r8, 0, 0, r8.length);
        memset(r8a, 0, 0, r8a.length);
        memset(r8b, 0, 0, r8b.length);

        return key;
    }
    public static byte[] encrypt3DESECB(byte[] keyBytes, byte[] dataBytes) {
        try {
            SecretKeySpec newKey = new SecretKeySpec(keyBytes, "DESede");
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, newKey);
            return cipher.doFinal(dataBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    public static final void encrypt3DESECB(byte[] output, int outputOffset, byte[] input, int inputOffset, int length, byte[] desKey, int desKeyOffset) {
        final byte[] keyValue = new byte[24];
        System.arraycopy(desKey, desKeyOffset, keyValue, 0, 16);
        System.arraycopy(desKey, desKeyOffset, keyValue, 16, 8);

        try {
            final SecretKey key = new SecretKeySpec(keyValue, "DESede");
            final Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.doFinal(input, inputOffset, length, output, outputOffset);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static byte[] calculateDataKey(byte[] ksn, byte[] ipek) {
        byte[] dataKey = calculateDerivedKey(ksn, ipek);

        dataKey[5]^= 0xFF;
        dataKey[13]^= 0xFF;
        encrypt3DESECB(dataKey, 0, dataKey, 0, dataKey.length, dataKey, 0);
        return dataKey;
    }
    public static byte[] decryptPin(byte[] ksn, byte[] pinBlock) {
        byte[] buffer = DUKPT.derivePinKey(ksn, IPEK);

        // TODO: Implement PIN decryption

        return buffer;
    }

    /**
     * Process online transaction request.
     *
     * @param pinKSN Pin block key serial number (DUKPT).
     * @param pinBlock PIN block in format ISO0, ISO2, and etc.
     * @param data Additional transaction data which can be EMV tags byte stream.
     *
     * @return server transaction response data.
     */
    public static byte[] processOnline(byte[] pinKSN, byte[] pinBlock,  byte[] data) {
        // TODO: Connect to real transaction processor
        // ...

        // Return emulated server response data
        List<BerTlv> response = new ArrayList<>();
        // Tag 8A - Authorisation Response Code ("00" - OK)
        response.add(new BerTlv(EmvTags.TAG_8A_AUTHORISATION_RESPONSE_CODE, "3030"));

        return BerTlv.listToByteArray(response);
    }

    // Convert HEX string to byte array.
    public static byte[] hexStringToByteArray(String s) {
        char[] array = s.replaceAll(" ", "").toUpperCase().toCharArray();
        byte[] buffer = new byte[array.length / 2];
        for (int i = 0, j = 0; i < buffer.length; i++, j+=2) {
            int a = array[j + 0] < 'A' ? (array[j + 0] - '0') : (array[j + 0] - 'A' + 10);
            int b = array[j + 1] < 'A' ? (array[j + 1] - '0') : (array[j + 1] - 'A' + 10);
            buffer[i] = (byte)((a << 4) | b);
        }
        return buffer;
    }

    // Read XML element as BerTLV byte array.
    private static byte[] getTLV(Element e) throws IOException {
        String attribute = e.getAttribute("id");
        Tag tag = new Tag(attribute);
        NodeList nodeList = e.getChildNodes();
        ByteArrayOutputStream value = new ByteArrayOutputStream();

        if (nodeList.getLength() > 1) {
            for (int i = 0; i < nodeList.getLength(); i++) {
                if (nodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    value.write(getTLV((Element)nodeList.item(i)));
                }
            }
        } else if (nodeList.getLength() == 1) {
            value.write(hexStringToByteArray(nodeList.item(0).getTextContent()));
        }

        BerTlv tlv = new BerTlv(tag, value.toByteArray());
        return tlv.toByteArray();
    }

    /**
     * Read and parse XML configuration from stream and convert it to byte array.
     *
     * @param input input stream.
     *
     * @return configuration data.
     */
    public static byte[] readConfig(InputStream input) throws IOException, SAXException,
            ParserConfigurationException {
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(input);
            Element rootElement = doc.getDocumentElement();
            NodeList nodeList = rootElement.getChildNodes();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            for (int i = 0; i < nodeList.getLength(); i++) {
                if (nodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    buffer.write(getTLV((Element) nodeList.item(i)));
                }
            }

            return buffer.toByteArray();
        } finally {
             input.close();
        }
    }

}