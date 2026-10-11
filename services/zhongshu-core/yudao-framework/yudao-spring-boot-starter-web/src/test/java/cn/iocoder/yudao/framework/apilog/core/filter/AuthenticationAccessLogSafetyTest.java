package cn.iocoder.yudao.framework.apilog.core.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthenticationAccessLogSafetyTest {
    private static final String SECRET = "disposable-access-log-secret";

    @BeforeEach void initializeWebProperties() { new WebFrameworkUtils(new WebProperties()); }

    private ApiAccessLogCreateReqDTO build(String uri, Exception error, CommonResult<?> result) throws Exception {
        ApiAccessLogFilter filter = new ApiAccessLogFilter(new WebProperties(), "test", mock(ApiAccessLogCommonApi.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setServletPath(uri);
        if (result != null) WebFrameworkUtils.setCommonResult(request, result);
        ApiAccessLogCreateReqDTO dto = new ApiAccessLogCreateReqDTO();
        Method method = ApiAccessLogFilter.class.getDeclaredMethod("buildApiAccessLog", ApiAccessLogCreateReqDTO.class,
                jakarta.servlet.http.HttpServletRequest.class, LocalDateTime.class, Map.class, String.class, Exception.class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(filter, dto, request, LocalDateTime.now(),
                new HashMap<>(Map.of("refreshToken", SECRET)), "{\"password\":\"" + SECRET + "\"}", error));
        return dto;
    }

    @Test void failedAuthenticationOmitsRawThrowableAndPayloads() throws Exception {
        ApiAccessLogCreateReqDTO dto = build("/admin-api/system/auth/login", new IllegalStateException(SECRET), null);
        assertEquals(500, dto.getResultCode());
        assertNull(dto.getRequestParams());
        assertNull(dto.getResponseBody());
        assertFalse(dto.getResultMsg().contains(SECRET));
    }

    @Test void refreshAndOauthResultsKeepCodesWithoutSensitiveMessages() throws Exception {
        for (String uri : new String[]{"/admin-api/system/auth/refresh-token", "/app-api/member/auth/login",
                "/admin-api/system/oauth2/token"}) {
            ApiAccessLogCreateReqDTO dto = build(uri, null, CommonResult.error(400, SECRET));
            assertEquals(400, dto.getResultCode());
            assertNull(dto.getRequestParams());
            assertNull(dto.getResponseBody());
            assertFalse(dto.getResultMsg().contains(SECRET));
        }
    }

    @Test void persistenceFailureNeverPrintsAuthenticationDtoOrThrowable() throws Exception {
        ApiAccessLogCommonApi api = mock(ApiAccessLogCommonApi.class);
        doThrow(new IllegalStateException(SECRET)).when(api).createApiAccessLogAsync(any());
        ApiAccessLogFilter filter = new ApiAccessLogFilter(new WebProperties(), "test", api);
        Logger logger = (Logger) LoggerFactory.getLogger(ApiAccessLogFilter.class);
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start(); logger.addAppender(capture);
        try {
            Method method = ApiAccessLogFilter.class.getDeclaredMethod("createApiAccessLog",
                    jakarta.servlet.http.HttpServletRequest.class, LocalDateTime.class, Map.class, String.class, Exception.class);
            method.setAccessible(true);
            method.invoke(filter, new MockHttpServletRequest("POST", "/admin-api/system/auth/login"), LocalDateTime.now(),
                    Map.of("refreshToken", SECRET), "{\"password\":\"" + SECRET + "\"}", new IllegalStateException(SECRET));
            verify(api).createApiAccessLogAsync(any());
            assertEquals(1, capture.list.size());
            assertFalse(capture.list.get(0).getFormattedMessage().contains(SECRET));
            assertNull(capture.list.get(0).getThrowableProxy());
        } finally { logger.detachAppender(capture); capture.stop(); }
    }

    @Test void ordinaryNonAuthenticationDiagnosticsRemainAvailable() throws Exception {
        ApiAccessLogCreateReqDTO dto = build("/admin-api/system/user/page", null, CommonResult.error(400, "page size invalid"));
        assertEquals("page size invalid", dto.getResultMsg());
        assertNotNull(dto.getRequestParams());
        assertFalse(dto.getRequestParams().contains(SECRET));
    }
}
