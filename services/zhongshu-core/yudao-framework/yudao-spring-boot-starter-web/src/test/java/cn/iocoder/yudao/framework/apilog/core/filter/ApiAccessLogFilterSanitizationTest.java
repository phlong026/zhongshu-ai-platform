package cn.iocoder.yudao.framework.apilog.core.filter;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ApiAccessLogFilterSanitizationTest {
    private static String request(String value) throws Exception {
        Method method = ApiAccessLogFilter.class.getDeclaredMethod("sanitizeJson", String.class, String[].class);
        method.setAccessible(true);
        return (String) method.invoke(null, value, null);
    }
    @Test void malformedPayloadIsOmittedInsteadOfLoggedUnredacted() throws Exception {
        assertNull(request("{\"password\":\"disposable-test-secret\", broken"));
    }
    @Test void nestedLoginAndRefreshCredentialsAreRemoved() throws Exception {
        String sanitized = request("{\"username\":\"fixture\",\"password\":\"secret-a\",\"nested\":[{\"accessToken\":\"secret-b\",\"refreshToken\":\"secret-c\"}]}");
        assertNotNull(sanitized); assertTrue(sanitized.contains("fixture")); assertFalse(sanitized.contains("secret-"));
    }
    @Test void tokenResponseRetainsStatusButOmitsCredentials() throws Exception {
        Method method = ApiAccessLogFilter.class.getDeclaredMethod("sanitizeJson", CommonResult.class, String[].class);
        method.setAccessible(true);
        String sanitized = (String) method.invoke(null, CommonResult.success(Map.of("accessToken", "secret-d", "refreshToken", "secret-e", "userId", 1)), null);
        assertNotNull(sanitized); assertTrue(sanitized.contains("userId")); assertFalse(sanitized.contains("secret-"));
    }
    @Test void emptyRequestDoesNotCreateLogContent() throws Exception { assertNull(request(null)); assertNull(request("")); }
}
