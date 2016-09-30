package com.datecs.printerdemo;

import com.datecs.crypto.AES;
import com.datecs.crypto.CRC;
import com.datecs.crypto.DUKPT;
import com.datecs.crypto.RSA;
import com.datecs.crypto.SHA1;
import com.datecs.crypto.TripleDES;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

public class CryptographyHelper {

    /**
     *  Default RSA private key modulus. 
     */
    public static final byte[] PRIV_KEY_MOD_BYTES = {
            (byte)0x77, (byte)0x6C, (byte)0xD1, (byte)0xEF, (byte)0x62, (byte)0xE9,
            (byte)0x8D, (byte)0x8F, (byte)0x19, (byte)0xB3, (byte)0x4F, (byte)0xDA,
            (byte)0xD2, (byte)0x41, (byte)0x1C, (byte)0x7A, (byte)0xC7, (byte)0xE8,
            (byte)0xD9, (byte)0x5D, (byte)0x10, (byte)0xD4, (byte)0xF4, (byte)0xA5,
            (byte)0x68, (byte)0x26, (byte)0xAA, (byte)0x2C, (byte)0xCE, (byte)0x8E,
            (byte)0xA3, (byte)0x3C, (byte)0x05, (byte)0xEA, (byte)0x1B, (byte)0x81,
            (byte)0x6A, (byte)0x39, (byte)0xDE, (byte)0x34, (byte)0x7B, (byte)0x23,
            (byte)0xC5, (byte)0xE6, (byte)0x25, (byte)0x50, (byte)0x73, (byte)0x55,
            (byte)0xD8, (byte)0x3F, (byte)0x3F, (byte)0x33, (byte)0x2E, (byte)0x5B,
            (byte)0x28, (byte)0xB9, (byte)0xFE, (byte)0x4C, (byte)0x40, (byte)0xAA,
            (byte)0xD2, (byte)0x40, (byte)0x1A, (byte)0xE4, (byte)0x37, (byte)0x85,
            (byte)0x33, (byte)0x87, (byte)0x32, (byte)0x46, (byte)0xAE, (byte)0xCE,
            (byte)0x54, (byte)0x03, (byte)0xD2, (byte)0xAD, (byte)0x8A, (byte)0xE0,
            (byte)0xAF, (byte)0x27, (byte)0xC6, (byte)0x03, (byte)0x7C, (byte)0xCF,
            (byte)0x78, (byte)0x96, (byte)0x17, (byte)0xF5, (byte)0x5A, (byte)0x2D,
            (byte)0x38, (byte)0x94, (byte)0x28, (byte)0x2B, (byte)0x6F, (byte)0xD9,
            (byte)0xEC, (byte)0xA0, (byte)0x7C, (byte)0x5F, (byte)0xDE, (byte)0x20,
            (byte)0xE8, (byte)0x2F, (byte)0xA6, (byte)0x51, (byte)0x07, (byte)0xCD,
            (byte)0xD5, (byte)0xB0, (byte)0xC8, (byte)0xB0, (byte)0x44, (byte)0xB0,
            (byte)0x4A, (byte)0xE2, (byte)0x5B, (byte)0xD7, (byte)0xC4, (byte)0x99,
            (byte)0x22, (byte)0x98, (byte)0xAC, (byte)0x95, (byte)0x75, (byte)0x99,
            (byte)0x5D, (byte)0xEB, (byte)0xBB, (byte)0x97, (byte)0x22, (byte)0x82,
            (byte)0xC0, (byte)0xF4, (byte)0x6A, (byte)0x4E, (byte)0x0E, (byte)0x74,
            (byte)0xE3, (byte)0xA8, (byte)0x11, (byte)0x17, (byte)0xBA, (byte)0x0F,
            (byte)0xD1, (byte)0x47, (byte)0x7E, (byte)0x38, (byte)0x96, (byte)0xA0,
            (byte)0xDA, (byte)0x5F, (byte)0x99, (byte)0x1B, (byte)0x6B, (byte)0x68,
            (byte)0x76, (byte)0x46, (byte)0x9C, (byte)0xED, (byte)0x6A, (byte)0x5F,
            (byte)0xE3, (byte)0x3A, (byte)0xA0, (byte)0x03, (byte)0x5D, (byte)0xBC,
            (byte)0x27, (byte)0x2B, (byte)0x45, (byte)0xC1, (byte)0x29, (byte)0xBA,
            (byte)0x6D, (byte)0x6B, (byte)0xF0, (byte)0xBF, (byte)0x8A, (byte)0x93,
            (byte)0xBB, (byte)0x9C, (byte)0x34, (byte)0xB6, (byte)0xB1, (byte)0xC9,
            (byte)0x33, (byte)0xC8, (byte)0x3B, (byte)0x53, (byte)0xE2, (byte)0xE7,
            (byte)0x40, (byte)0xF7, (byte)0x30, (byte)0x74, (byte)0x98, (byte)0xF1,
            (byte)0x7D, (byte)0xB5, (byte)0x60, (byte)0x7C, (byte)0x55, (byte)0x28,
            (byte)0x73, (byte)0x19, (byte)0x5C, (byte)0x74, (byte)0x22, (byte)0xB7,
            (byte)0xB8, (byte)0x65, (byte)0xFC, (byte)0xA1, (byte)0xBA, (byte)0x3A,
            (byte)0xC3, (byte)0x4D, (byte)0x70, (byte)0x75, (byte)0xFE, (byte)0x95,
            (byte)0x6A, (byte)0x96, (byte)0x0F, (byte)0xC2, (byte)0x75, (byte)0x86,
            (byte)0xB1, (byte)0x26, (byte)0x00, (byte)0x07, (byte)0x20, (byte)0x02,
            (byte)0x35, (byte)0x50, (byte)0x23, (byte)0xA0, (byte)0x94, (byte)0x47,
            (byte)0xC7, (byte)0x1D, (byte)0x4F, (byte)0x72, (byte)0x77, (byte)0xBE,
            (byte)0xAA, (byte)0x6B, (byte)0xAA, (byte)0xFB, (byte)0xDC, (byte)0x28,
            (byte)0xB6, (byte)0x48, (byte)0xE1, (byte)0xC7
        };

    /**
     *  Default RSA private key exponent. 
     */ 
    public static final byte[] PRIV_KEY_EXP_BYTES = {
        (byte)0x50, (byte)0x9B, (byte)0xF7, (byte)0x28, (byte)0x2A, (byte)0x0F,
        (byte)0x93, (byte)0x29, (byte)0x60, (byte)0x23, (byte)0x94, (byte)0x67,
        (byte)0x13, (byte)0x3C, (byte)0x37, (byte)0xC8, (byte)0xF8, (byte)0x5E,
        (byte)0xC7, (byte)0x38, (byte)0xF6, (byte)0x3F, (byte)0x87, (byte)0xD2,
        (byte)0x8D, (byte)0xF6, (byte)0x6B, (byte)0x2F, (byte)0x4B, (byte)0x4D,
        (byte)0x24, (byte)0x09, (byte)0x43, (byte)0xC4, (byte)0xBD, (byte)0x44,
        (byte)0x21, (byte)0x3B, (byte)0x66, (byte)0x2C, (byte)0xEE, (byte)0x61,
        (byte)0x3B, (byte)0x17, (byte)0x19, (byte)0x60, (byte)0xB0, (byte)0x38,
        (byte)0xE5, (byte)0x79, (byte)0xEB, (byte)0x62, (byte)0xD4, (byte)0x8B,
        (byte)0x5B, (byte)0x76, (byte)0x0F, (byte)0x9B, (byte)0xD0, (byte)0x9A,
        (byte)0x7C, (byte)0xC8, (byte)0x20, (byte)0x5E, (byte)0xA2, (byte)0xCB,
        (byte)0x19, (byte)0xF8, (byte)0xCB, (byte)0x8A, (byte)0xC2, (byte)0x3B,
        (byte)0x2A, (byte)0xA2, (byte)0x59, (byte)0xF6, (byte)0x21, (byte)0xA3,
        (byte)0x7F, (byte)0x16, (byte)0xCD, (byte)0xA5, (byte)0x54, (byte)0xFD,
        (byte)0x85, (byte)0x5B, (byte)0x6A, (byte)0x58, (byte)0x85, (byte)0xC1,
        (byte)0xB8, (byte)0x4A, (byte)0xE8, (byte)0xC2, (byte)0x49, (byte)0x01,
        (byte)0x43, (byte)0xA3, (byte)0x1F, (byte)0xD0, (byte)0x65, (byte)0xD2,
        (byte)0xB8, (byte)0x66, (byte)0x51, (byte)0x50, (byte)0xA8, (byte)0x7F,
        (byte)0xDB, (byte)0x19, (byte)0x34, (byte)0x9D, (byte)0x26, (byte)0x00,
        (byte)0x08, (byte)0xCB, (byte)0xB9, (byte)0x4A, (byte)0x6E, (byte)0xBD,
        (byte)0x1E, (byte)0x89, (byte)0x07, (byte)0x14, (byte)0xEB, (byte)0x07,
        (byte)0xD6, (byte)0x48, (byte)0x6D, (byte)0x11, (byte)0xA8, (byte)0x14,
        (byte)0xC3, (byte)0xB3, (byte)0x14, (byte)0x2A, (byte)0x63, (byte)0x63,
        (byte)0x51, (byte)0x0C, (byte)0xF5, (byte)0x93, (byte)0x1C, (byte)0x94,
        (byte)0x73, (byte)0xD3, (byte)0x7B, (byte)0x21, (byte)0xA3, (byte)0xE7,
        (byte)0x57, (byte)0x22, (byte)0xC1, (byte)0x60, (byte)0x62, (byte)0xB9,
        (byte)0xBA, (byte)0x2E, (byte)0x40, (byte)0xFC, (byte)0xF3, (byte)0x35,
        (byte)0x1F, (byte)0xEC, (byte)0x7C, (byte)0x10, (byte)0xE7, (byte)0x27,
        (byte)0x13, (byte)0x1C, (byte)0x32, (byte)0x4C, (byte)0xA1, (byte)0x58,
        (byte)0x85, (byte)0x5D, (byte)0xEB, (byte)0x0D, (byte)0xFC, (byte)0x91,
        (byte)0x5B, (byte)0xD4, (byte)0xFF, (byte)0x46, (byte)0xF4, (byte)0x8F,
        (byte)0x2A, (byte)0x98, (byte)0x05, (byte)0x1D, (byte)0xE6, (byte)0xAE,
        (byte)0xCC, (byte)0x24, (byte)0xFE, (byte)0xD7, (byte)0xCC, (byte)0x40,
        (byte)0xE7, (byte)0xF9, (byte)0x22, (byte)0xD0, (byte)0x02, (byte)0x29,
        (byte)0xA5, (byte)0x65, (byte)0x7A, (byte)0x54, (byte)0x9A, (byte)0xDB,
        (byte)0xCC, (byte)0x4C, (byte)0x83, (byte)0x59, (byte)0xD6, (byte)0xDB,
        (byte)0xE8, (byte)0x8C, (byte)0xD9, (byte)0xE1, (byte)0x75, (byte)0x57,
        (byte)0x43, (byte)0xAB, (byte)0xDC, (byte)0x66, (byte)0x80, (byte)0xA1,
        (byte)0x9D, (byte)0x9B, (byte)0x5B, (byte)0xBC, (byte)0xB1, (byte)0x0C,
        (byte)0x84, (byte)0x30, (byte)0xDF, (byte)0x91, (byte)0x48, (byte)0xA4,
        (byte)0xA3, (byte)0x5D, (byte)0xF9, (byte)0xEF, (byte)0x9F, (byte)0xFF,
        (byte)0x28, (byte)0xA5, (byte)0xA9, (byte)0x28, (byte)0x00, (byte)0xFE,
        (byte)0xDA, (byte)0xD0, (byte)0x4A, (byte)0xE1
    };
    
    /**
     * Default AES data key
     */
    public static final byte[] AES_DATA_KEY_BYTES = {
        (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31,
        (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31,
        (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31,
        (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31,
        (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31, (byte)0x31,
        (byte)0x31, (byte)0x31,
    };
    
    /**
     * Default IDTECH data key
     */
    public static final byte[] IDTECH_DATA_KEY_BYTES = {
        (byte)0x82, (byte)0xDF, (byte)0x8A, (byte)0xC0, (byte)0x22, (byte)0x91,
        (byte)0x62, (byte)0xAF, (byte)0x04, (byte)0x0C, (byte)0xF4, (byte)0xD0,
        (byte)0x76, (byte)0x43, (byte)0x72, (byte)0x79,
    };


    private static String byteArrayToHexString(byte[] buffer, int offset, int length) {
        char[] tmp = new char[length * 3];

        for (int i = 0, j = 0; i < length; i++) {
            int a = (buffer[i + offset] & 0xff) >> 4;
            int b = (buffer[i + offset] & 0x0f);
            tmp[j++] = (char)(a < 10 ? '0' + a : 'A' + a - 10);
            tmp[j++] = (char)(b < 10 ? '0' + b : 'A' + b - 10);
            tmp[j++] = ' ';
        }

        return new String(tmp);
    }

    /**
     * Decrypt RSA block to decode track data information.
     * 
     * @param data the RSA block.
     * @return the String array that contains: 
     *      <ul>    
     *        <li>track 2</li> 
     *        <li>cardholder name</li>        
     *      </ul>
     * @throws Exception if an error occurs.
     */
    public static String[] decryptTrackDataRSA(byte[] data) throws Exception {
        byte[] rsaBlock = new byte[256];
        System.arraycopy(data, 0, rsaBlock, 0, rsaBlock.length);
        byte[] decrypted = RSA.decrypt(PRIV_KEY_MOD_BYTES, PRIV_KEY_EXP_BYTES, rsaBlock);
        
        byte[] key = new byte[16];
        System.arraycopy(decrypted, 207, key, 0, key.length);
        
        byte[] enc = new byte[data.length - 272];
        System.arraycopy(data, 272, enc, 0, enc.length);
             
        byte[] dec = AES.decryptCBC(key, enc);
        int offs = 0;
        
        String[] result = new String[2];
        
        result[0] = "";
        for (offs = 4; offs < dec.length && dec[offs] != 0; offs++) {
            result[0] += (char)dec[offs];
        }
        
        result[1] = "";
        for (offs+=1; offs < dec.length && dec[offs] != 0; offs++) {
            result[1] += (char)dec[offs];
        }   
        
        offs++;
        byte[] sha256 = new byte[32];
        System.arraycopy(dec, offs, sha256, 0, sha256.length);
        
        MessageDigest md = MessageDigest.getInstance("SHA-256");                        
        md.update(data, 256, 16);
        md.update(dec, 0, offs);
        byte[] calcSHA = md.digest(); 
        
        if (!MessageDigest.isEqual(calcSHA, sha256)) {
            throw new RuntimeException("Wrong key");
        }  
               
        return result;
    }
    
    /**
     * Decrypt AES encrypted block to decode track data information.
     * 
     * @param data the AES block.
     * @return the String array that contains: 
     *      <ul>    
     *        <li>random number</li> 
     *        <li>serial number</li>
     *        <li>track 1</li>
     *        <li>track 2</li>
     *        <li>track 3</li>
     *      </ul>
     * @throws Exception if an error occurs.
     */
    public static String[] decryptAESBlock(byte[] data) {
        byte[] decrypted = AES.decryptCBC(AES_DATA_KEY_BYTES, data);
        if (decrypted == null) {
            throw new RuntimeException("Failed to decrypt");
        }
        
        int offset = 0;
        String[] result = new String[5];
        result[0] = byteArrayToHexString(decrypted, offset, 4);
        offset += 4;
        result[1] = byteArrayToHexString(decrypted, offset, 16);
        offset += 16;
               
        int index = -1;        
        while (offset < decrypted.length && decrypted[offset] != 0) {
            int c = decrypted[offset] & 0xff;
            
            switch (c) {
                case 0xF1: {
                    index = 2;
                    result[index] = "";
                    break;
                }
                case 0xF2: {
                    index = 3;
                    result[index] = "";
                    break;
                }
                case 0xF3: {
                    index = 4;
                    result[index] = "";
                    break;
                }
                default: {
                    if (index != -1) {
                        result[index] += (char)c;
                    }
                }
            }   
            offset++;
        }
        
        if (offset >= (decrypted.length - 2) || decrypted[offset] != 0                
                || CRC.ccit16(decrypted, 0, offset + 1) != (((decrypted[offset + 1] & 0xff) << 8)) +
                (decrypted[offset + 2] & 0xff)) {
            throw new RuntimeException("Wrong key");
        }
                
        return result;           
    }   

    /**
     * Decrypt IDTECH encrypted block to decode track data information.
     * 
     * @param data the IDTECH block.
     * @return the String array that contains: 
     *      <ul>    
     *        <li>card type</li> 
     *        <li>track 1</li>
     *        <li>track 2</li>
     *        <li>track 3</li>
     *      </ul>
     * @throws Exception if an error occurs.
     */
    public static String[] decryptIDTECHBlock(byte[] data) {
        /*
         * DATA[0]: CARD TYPE: 0x0 - payment card
         * DATA[1]: TRACK FLAGS
         * DATA[2]: TRACK 1 LENGTH
         * DATA[3]: TRACK 2 LENGTH
         * DATA[4]: TRACK 3 LENGTH
         * DATA[??]: TRACK 1 DATA MASKED
         * DATA[??]: TRACK 2 DATA MASKED
         * DATA[??]: TRACK 3 DATA
         * DATA[??]: TRACK 1 AND TRACK 2 TDES ENCRYPTED
         * DATA[??]: TRACK 1 SHA1 (0x14 BYTES)
         * DATA[??]: TRACK 2 SHA1 (0x14 BYTES)
         * DATA[??]: DUKPT SERIAL AND COUNTER (0x0A BYTES)
         */        
        int cardType = data[0] & 0xff;
        int track1Len = data[2] & 0xff;
        int track2Len = data[3] & 0xff;
        int track3Len = data[4] & 0xff;
        int offset = 5; 
            
        String[] result = new String[4];
        result[0] = (cardType == 0) ? "PAYMENT" : "UNKNOWN";
        
        if (track1Len > 0) {           
            offset += track1Len; 
        }
        
        if (track2Len > 0) {            
            offset += track2Len; 
        }
        
        if (track3Len > 0) {
            result[3] = new String(data, offset, track3Len);
            offset += track3Len; 
        }
        
        if ((track1Len + track2Len) > 0) {
            int blockSize = (track1Len + track2Len + 7) & 0xFFFFFF8;
            byte[] encrypted = new byte[blockSize];
            System.arraycopy(data, offset, encrypted, 0, encrypted.length);
            offset+= blockSize;
            
            byte[] track1Hash = new byte[20];
            System.arraycopy(data, offset, track1Hash, 0, track1Hash.length);
            offset+= track1Hash.length;
            
            byte[] track2Hash = new byte[20];
            System.arraycopy(data, offset, track2Hash, 0, track2Hash.length);
            offset+= track2Hash.length;
            
            byte[] ipek = IDTECH_DATA_KEY_BYTES;
            byte[] ksn = new byte[10];
            System.arraycopy(data, offset, ksn, 0, ksn.length);
            offset+= ksn.length;
                            
            byte[] dataKey = DUKPT.deriveDataKey(ksn, ipek);
            byte[] decrypted = TripleDES.decryptCBC(dataKey, encrypted);
            
            if (decrypted == null) throw new RuntimeException("Failed to decrypt");
                          
            if (track1Len > 0) {
                byte[] calcHash = SHA1.calculate(decrypted, 0, track1Len);
                if (!Arrays.equals(track1Hash, calcHash)) {
                    throw new RuntimeException("Wrong key");
                }
            }
            
            if (track2Len > 0) {
                byte[] calcHash = SHA1.calculate(decrypted, track1Len, track2Len);
                if (!Arrays.equals(track2Hash, calcHash)) {
                    throw new RuntimeException("Wrong key");
                }
            }
            
            if (track1Len > 0) {
                result[1] = new String(decrypted, 0, track1Len);
            }

            if (track2Len > 0) {
                result[2] = new String(decrypted, track1Len, track2Len);
            } 
        }
        
        return result;
    }
    
    public static byte[] createKeyExchangeBlock(int decKeyId, int dstKeyId, int keyVersion, byte[] key, byte[] enckey) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x2B); 
        out.write(decKeyId);
        out.write(dstKeyId);
        out.write(keyVersion >> 24);
        out.write(keyVersion >> 16);
        out.write(keyVersion >> 8);
        out.write(keyVersion);
        
        // Pad the key
        if (key.length != 32) {
            byte[] buffer = new byte[32];
            System.arraycopy(key, 0, buffer, 0, key.length);
            key = buffer;
        }
            
        byte[] hash = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(out.toByteArray());
            digest.update(key);
            hash = digest.digest();
        } catch (Exception e) {
            hash = new byte[32];
        }
        
        byte[] block = new byte[key.length + hash.length];      
        System.arraycopy(key, 0, block, 0, key.length);
        System.arraycopy(hash, 0, block, key.length, hash.length);          
        
        if (enckey != null) {            
            block = AES.encryptCBC(enckey, block);
        }
        
        for (byte b: block) {
            out.write(b);
        }
        
        return out.toByteArray();
    }
}
