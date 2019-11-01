package ee.openeid.siga.common.session;

import ee.openeid.siga.common.model.SigningType;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.binary.Binarylizable;
import org.digidoc4j.DataToSign;

@Data
@Builder
public class DataToSignHolder implements Binarylizable {

    private String sessionCode;
    private DataToSign dataToSign;
    private SigningType signingType;

    @Override
    @SneakyThrows
    public void writeBinary(BinaryWriter writer) {
        writer.writeString("sessionCode", sessionCode);
        writer.writeByteArray("dataToSignSerialized", SerializationUtils.serialize(dataToSign));
        writer.writeObject("signingType", signingType);
    }

    @Override
    @SneakyThrows
    public void readBinary(BinaryReader reader) {
        sessionCode = reader.readString("sessionCode");
        dataToSign = SerializationUtils.deserialize(reader.readByteArray("dataToSignSerialized"));
        signingType = reader.readObject("signingType");
    }
}
