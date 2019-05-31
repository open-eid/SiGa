package ee.openeid.siga.common.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.binary.Binarylizable;
import org.digidoc4j.Container;

@Data
@AllArgsConstructor
public class ContainerHolder implements Binarylizable {

    private Container container;

    @Override
    @SneakyThrows
    public void writeBinary(BinaryWriter writer) throws BinaryObjectException {
        writer.writeByteArray("containerSerialized", SerializationUtils.serialize(container));
    }

    @Override
    @SneakyThrows
    public void readBinary(BinaryReader reader) throws BinaryObjectException {
        container = SerializationUtils.deserialize(reader.readByteArray("containerSerialized"));
    }
}
