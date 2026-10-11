package cn.iocoder.yudao.framework.web.core.handler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.util.SensitiveRequestLogUtils;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ValidationLogSafetyTest {

    private static final String SECRET = "disposable-validation-secret";
    private static final List<String> AUTH_PATHS = List.of(
            "/admin-api/system/auth/login", "/admin-api/system/auth/refresh-token",
            "/app-api/system/auth/login", "/app-api/member/auth/login",
            "/app-api/member/auth/refresh-token", "/admin-api/system/oauth2/token",
            "/admin-api/system/oauth2/authorize", "/admin-api/system/oauth2/check-token");
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler("test", null);
    private Logger logger;
    private ListAppender<ILoggingEvent> capture;

    @BeforeEach
    void setUp() {
        new WebFrameworkUtils(new WebProperties());
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        capture = new ListAppender<>();
        capture.start();
        logger.addAppender(capture);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(capture);
        capture.stop();
        RequestContextHolder.resetRequestAttributes();
    }

    public void login(Object body) { }

    @Test
    void validationAndBindErrorsNeverLogRejectedPasswordOrThrowable() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(Map.of("password", SECRET), "login");
        binding.addError(new FieldError("login", "password", SECRET, false,
                new String[]{"Size"}, null, "Password length is invalid"));
        MethodParameter parameter = new MethodParameter(getClass().getMethod("login", Object.class), 0);
        assertEquals(400, handler.methodArgumentNotValidExceptionExceptionHandler(
                new MethodArgumentNotValidException(parameter, binding)).getCode());
        assertEquals(400, handler.bindExceptionHandler(new BindException(binding)).getCode());
        assertEquals(2, capture.list.size());
        assertSafeLogger();
    }

    @Test
    void malformedBodyDoesNotEnterUnexpectedErrorLogger() {
        var result = handler.methodArgumentTypeInvalidFormatExceptionHandler(new HttpMessageNotReadableException(
                "Malformed input " + SECRET, new MockHttpInputMessage(new byte[0])));
        assertEquals(400, result.getCode());
        assertFalse(result.getMsg().contains(SECRET));
        assertSafeLogger();
    }

    @Test
    void authenticationUnexpectedErrorsOmitPayloadAndAllThrowableMessagesInBothSinks() {
        ApiErrorLogCommonApi sink = mock(ApiErrorLogCommonApi.class);
        GlobalExceptionHandler errorHandler = new GlobalExceptionHandler("test", sink);
        for (String path : AUTH_PATHS) {
            Throwable error = new IllegalStateException("outer " + SECRET, new RuntimeException("cause " + SECRET));
            error.addSuppressed(new RuntimeException("suppressed " + SECRET));
            var result = errorHandler.defaultExceptionHandler(request(path), error);
            assertEquals(500, result.getCode());
            assertFalse(result.getMsg().contains(SECRET));
        }
        ArgumentCaptor<ApiErrorLogCreateReqDTO> logs = ArgumentCaptor.forClass(ApiErrorLogCreateReqDTO.class);
        verify(sink, times(AUTH_PATHS.size())).createApiErrorLogAsync(logs.capture());
        for (ApiErrorLogCreateReqDTO log : logs.getAllValues()) {
            assertSafeErrorLog(log);
            assertEquals(IllegalStateException.class.getName(), log.getExceptionName());
            assertTrue(log.getExceptionStackTrace().contains("ValidationLogSafetyTest"));
        }
        assertSafeLogger();
    }

    @Test
    void authenticationErrorLogSinkFailureDoesNotDumpDtoOrSinkThrowable() {
        ApiErrorLogCommonApi sink = mock(ApiErrorLogCommonApi.class);
        doThrow(new IllegalStateException("SQL parameters " + SECRET)).when(sink).createApiErrorLogAsync(any());
        GlobalExceptionHandler errorHandler = new GlobalExceptionHandler("test", sink);
        assertEquals(500, errorHandler.defaultExceptionHandler(request(AUTH_PATHS.get(0)),
                new RuntimeException("request failed " + SECRET)).getCode());
        ArgumentCaptor<ApiErrorLogCreateReqDTO> log = ArgumentCaptor.forClass(ApiErrorLogCreateReqDTO.class);
        verify(sink).createApiErrorLogAsync(log.capture());
        assertSafeErrorLog(log.getValue());
        assertEquals(2, capture.list.size());
        assertSafeLogger();
    }

    @Test
    void authenticationErrorWithNoStackStillPersistsSafeDiagnostics() {
        ApiErrorLogCommonApi sink = mock(ApiErrorLogCommonApi.class);
        GlobalExceptionHandler errorHandler = new GlobalExceptionHandler("test", sink);
        RuntimeException error = new RuntimeException(SECRET);
        error.setStackTrace(new StackTraceElement[0]);
        assertEquals(500, errorHandler.defaultExceptionHandler(request(AUTH_PATHS.get(1)), error).getCode());
        ArgumentCaptor<ApiErrorLogCreateReqDTO> log = ArgumentCaptor.forClass(ApiErrorLogCreateReqDTO.class);
        verify(sink).createApiErrorLogAsync(log.capture());
        assertSafeErrorLog(log.getValue());
        assertNotNull(log.getValue().getExceptionMethodName());
        assertSafeLogger();
    }

    @Test
    void ordinaryEndpointDiagnosticsAreNotGloballySuppressed() {
        ApiErrorLogCommonApi sink = mock(ApiErrorLogCommonApi.class);
        GlobalExceptionHandler errorHandler = new GlobalExceptionHandler("test", sink);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin-api/infra/config/test");
        request.setContentType("application/json");
        request.setContent("{\"ordinary\":\"fixture\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(500, errorHandler.defaultExceptionHandler(request, new RuntimeException("ordinary failure")).getCode());
        ArgumentCaptor<ApiErrorLogCreateReqDTO> log = ArgumentCaptor.forClass(ApiErrorLogCreateReqDTO.class);
        verify(sink).createApiErrorLogAsync(log.capture());
        assertTrue(log.getValue().getRequestParams().contains("fixture"));
        assertTrue(log.getValue().getExceptionMessage().contains("ordinary failure"));
        assertNotNull(capture.list.get(0).getThrowableProxy());
    }

    @Test
    void authenticationPathMatchingSupportsContextsAndPrefixesWithoutMatchingUnrelatedRoutes() {
        for (String path : AUTH_PATHS) {
            assertTrue(SensitiveRequestLogUtils.isSensitiveAuthenticationRequest(request(path)), path);
        }
        MockHttpServletRequest nested = request("/deployment/service/v3/system/%61uth/login;matrix=value");
        nested.setContextPath("/deployment");
        assertTrue(SensitiveRequestLogUtils.isSensitiveAuthenticationRequest(nested));
        for (String path : List.of("/admin-api/system/authoring", "/admin-api/system/oauth2-client",
                "/admin-api/infra/file/upload", "/admin-api/xsystem/auth/login", "/admin-api/customer/authentication")) {
            assertFalse(SensitiveRequestLogUtils.isSensitiveAuthenticationRequest(request(path)), path);
        }
        assertFalse(SensitiveRequestLogUtils.isSensitiveAuthenticationRequest(null));
    }

    @Test
    void authenticationWarningHandlersAlsoAvoidThrowableMessages() {
        MockHttpServletRequest request = request(AUTH_PATHS.get(0));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals(403, handler.accessDeniedExceptionHandler(request, new AccessDeniedException(SECRET)).getCode());
        assertEquals(400, handler.httpMediaTypeNotSupportedExceptionHandler(
                new HttpMediaTypeNotSupportedException(SECRET)).getCode());
        assertSafeLogger();
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setContentType("application/json");
        request.setContent(("{\"password\":\"" + SECRET + "\"}").getBytes(StandardCharsets.UTF_8));
        request.addParameter("refreshToken", SECRET);
        request.addHeader("User-Agent", SECRET);
        return request;
    }

    private static void assertSafeErrorLog(ApiErrorLogCreateReqDTO log) {
        assertFalse(JsonUtils.toJsonString(log).contains(SECRET));
        assertEquals(SensitiveRequestLogUtils.OMITTED, log.getExceptionMessage());
        assertEquals(SensitiveRequestLogUtils.OMITTED, log.getExceptionRootCauseMessage());
        assertTrue(log.getRequestParams().contains("omitted"));
        assertFalse(log.getRequestParams().contains("password"));
        assertFalse(log.getRequestParams().contains("refreshToken"));
    }

    private void assertSafeLogger() {
        assertFalse(capture.list.isEmpty());
        for (ILoggingEvent event : capture.list) {
            assertFalse(event.getFormattedMessage().contains(SECRET));
            assertNull(event.getThrowableProxy());
        }
    }

}
