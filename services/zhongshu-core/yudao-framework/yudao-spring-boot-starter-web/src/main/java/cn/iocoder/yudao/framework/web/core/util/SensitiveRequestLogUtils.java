package cn.iocoder.yudao.framework.web.core.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Narrow logging policy for the existing System/Member authentication and OAuth endpoints. */
public final class SensitiveRequestLogUtils {

    public static final String OMITTED = "[omitted for authentication request]";
    private static final Pattern AUTHENTICATION_PATH = Pattern.compile(
            "(?:^|/)(?:system/(?:auth|oauth2)|member/auth)(?:/|$)");

    private SensitiveRequestLogUtils() { }

    public static boolean isSensitiveAuthenticationRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        // Decode the route and remove context/matrix parameters just as Spring MVC does.
        // Prefix-independent: /admin-api, /app-api and configured API prefixes all work.
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return AUTHENTICATION_PATH.matcher(path).find();
    }

    /** Never calls Throwable.toString/getMessage or formats a cause/suppressed Throwable. */
    public static String safeStackTrace(Throwable error) {
        if (error == null) {
            return OMITTED;
        }
        return error.getClass().getName() + "\n" + Arrays.stream(error.getStackTrace())
                .map(frame -> "\tat " + frame).collect(Collectors.joining("\n"));
    }

}
