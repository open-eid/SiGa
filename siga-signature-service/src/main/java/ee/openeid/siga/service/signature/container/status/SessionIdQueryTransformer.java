package ee.openeid.siga.service.signature.container.status;

import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.lang.IgniteClosure;

import javax.cache.Cache;
import java.util.Map;

/**
 * NB: This class is loaded into Ignite server nodes via peer class loading. 
 * If possible, avoid making changes in this class and in its dependencies!
 */
public class SessionIdQueryTransformer implements IgniteClosure<Cache.Entry<String, Map<String, BinaryObject>>, String> {

    @Override
    public String apply(Cache.Entry<String, Map<String, BinaryObject>> entry) {
        return entry.getKey();
    }
}