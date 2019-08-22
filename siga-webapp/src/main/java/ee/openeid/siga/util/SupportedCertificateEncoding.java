package ee.openeid.siga.util;

import ee.openeid.siga.common.util.Base64Util;
import ee.openeid.siga.common.util.HexUtil;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.util.Base64;

public enum SupportedCertificateEncoding {

    BASE64 {

        @Override
        public boolean isDecodable(String encodedString) {
            return Base64Util.isValidBase64(encodedString);
        }

        @Override
        public byte[] decode(String encodedString) {
            return Base64.getDecoder().decode(encodedString.getBytes());
        }

    },

    HEX {

        @Override
        public boolean isDecodable(String encodedString) {
            return HexUtil.isValidHex(encodedString);
        }

        @Override
        public byte[] decode(String encodedString) {
            try {
                return Hex.decodeHex(encodedString);
            } catch (DecoderException e) {
                throw new IllegalArgumentException("Input is not a valid HEX string", e);
            }
        }

    },
    ;

    public abstract boolean isDecodable(String encodedString);
    public abstract byte[] decode(String encodedString);

}
