package ee.openeid.siga.common.session;

import ee.openeid.siga.common.model.RelyingPartyInfo;
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
public class SignatureSession implements Binarylizable {
    private String sessionCode;
    private byte[] signature;
    @Builder.Default
    private SessionStatus sessionStatus = SessionStatus.builder().build();
    private DataToSign dataToSign;
    private SigningType signingType;
    private String dataFilesHash;
    private RelyingPartyInfo relyingPartyInfo;

    @Override
    @SneakyThrows
    public void writeBinary(BinaryWriter writer) {
        writer.writeString("sessionCode", sessionCode);
        writer.writeByteArray("signature", signature);
        writer.writeObject("sessionStatus", sessionStatus);
        writer.writeByteArray("dataToSignSerialized", SerializationUtils.serialize(dataToSign));
        writer.writeObject("signingType", signingType);
        writer.writeString("dataFilesHash", dataFilesHash);
        writer.writeObject("relyingPartyInfo", relyingPartyInfo);
    }

    @Override
    @SneakyThrows
    public void readBinary(BinaryReader reader) {
        sessionCode = reader.readString("sessionCode");
        signature = reader.readByteArray("signature");
        sessionStatus = reader.readObject("sessionStatus");
        dataToSign = SerializationUtils.deserialize(reader.readByteArray("dataToSignSerialized"));
        signingType = reader.readObject("signingType");
        dataFilesHash = reader.readString("dataFilesHash");
        relyingPartyInfo = reader.readObject("relyingPartyInfo");
    }
    public void setPollingStatus(SessionStatus.ProcessingStatus status) {
        sessionStatus.setProcessingStatus(status);
    }
}
