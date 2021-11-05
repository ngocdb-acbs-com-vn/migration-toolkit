package vn.com.acbs.digital.migration.toolkit;

import io.vertx.mutiny.sqlclient.Pool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ToolKitTest {
    @Test
    public void toolkitConstructorTest() {
        Assertions.assertThrows(NullPointerException.class, () -> new Toolkit(null, null));
        Assertions.assertThrows(NullPointerException.class, () -> new Toolkit(new Pool(null), null));
        Assertions.assertThrows(IllegalStateException.class, () -> new Toolkit(new Pool(null), ToolkitConfig.builder().build()));
    }
}
