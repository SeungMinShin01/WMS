package com.wms.audit;

import com.wms.security.LoginInterceptor;
import com.wms.security.LoginUser;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditWriter auditWriter;

    // @AuditLog 가 붙은 메서드를 감싼다
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        LocalDateTime time = LocalDateTime.now();
        long start = System.currentTimeMillis();

        // 1. 실행 전: 누가, 어디서, 무엇을
        HttpServletRequest request = currentRequest();
        LoginUser user = currentUser(request);
        String ip = clientIp(request);
        String uri = request == null ? "" : request.getMethod() + " " + request.getRequestURI();

        Object[] args = joinPoint.getArgs();
        String target = auditLog.target().isEmpty() ? ""
                : auditLog.target() + "=" + lookup(request, args, auditLog.target());
        String detail = makeDetail(request, args, auditLog.fields());

        // 2. 실제 컨트롤러 실행
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            // 실패도 기록, 예외는 GlobalExceptionHandler에게 throw , 핸들러가 응답
            record(time, user, auditLog.action(), target, detail, false, reason(e), ip, uri, start);
            throw e;
        }

        // 3. 정상 반환 = 커밋 완료
        record(time, user, auditLog.action(), target, detail, isSuccess(result), "", ip, uri, start);
        return result;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private LoginUser currentUser(HttpServletRequest request) {
        if (request != null
                && request.getAttribute(LoginInterceptor.LOGIN_USER) instanceof LoginUser user) {
            return user;
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null)
            return "";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // fields에 적은 이름들을 "locationId=3 qty=10" 형태로 만든다.
    private String makeDetail(HttpServletRequest request, Object[] args, String[] fields) {
        StringJoiner joiner = new StringJoiner(" ");
        for (String field : fields) {
            joiner.add(field + "=" + lookup(request, args, field));
        }
        return joiner.toString();
    }

    // 이름으로 값 찾기: 경로변수 -> 쿼리파라미터 -> 요청 DTO 순서
    private String lookup(HttpServletRequest request, Object[] args, String name) {
        // 1. 경로 변수
        try {
            if (request != null) {
                Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (vars instanceof Map<?, ?> map && map.containsKey(name)) {
                    return String.valueOf(map.get(name));
                }
                String param = request.getParameter(name);
                if (param != null)
                    return param;
            }
            for (Object arg : args) {
                if (arg == null || BeanUtils.isSimpleValueType(arg.getClass()))
                    continue;
                if (arg.getClass().isRecord()) {
                    for (RecordComponent component : arg.getClass().getRecordComponents()) {
                        if (component.getName().equals(name)) {
                            return String.valueOf(component.getAccessor().invoke(args));
                        }
                    }
                    continue;
                }

                BeanWrapperImpl wrapper = new BeanWrapperImpl(arg);
                if (wrapper.isReadableProperty(name)) {
                    return String.valueOf((wrapper.getPropertyValue(name)));
                }
            }
        } catch (Exception e) {
            log.warn("감사 로그 값 조회 실패: {} ({})", name, e.getMessage());
        }
        return "";
    }

    private boolean isSuccess(Object result) {
        if (result instanceof ResponseEntity<?> response) {
            return response.getStatusCode().is2xxSuccessful() && !Boolean.FALSE.equals(response.getBody());
        }
        if (result instanceof Boolean value)
            return value;
        return true;
    }

    private String reason(Throwable e) {
        if (e instanceof IllegalStateException
                || e instanceof IllegalArgumentException
                || e instanceof EntityNotFoundException) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    // 기록 중 에러가 나도 업무 결과에는 영향 없게 막는다.
    private void record(LocalDateTime time, LoginUser user, AuditAction action, String target,
            String detail, boolean success, String reason, String ip, String uri, long start) {
        try {
            auditWriter.write(time, user, action, target, detail, success, reason, ip, uri,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("감사 로그 기록 실패: {}", e.getMessage());
        }
    }

}