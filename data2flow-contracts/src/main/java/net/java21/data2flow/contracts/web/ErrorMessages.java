package net.java21.data2flow.contracts.web;

import net.java21.data2flow.contracts.error.ErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;
import java.util.Set;

/**
 * 오류 문구를 요청 언어(Accept-Language: ko·en·ja·zh, 기본 ko)로 찾는다(ADR-037).
 * 서비스의 메시지 번들({@code error.<코드>})을 먼저 보고, 없으면 이 라이브러리의 공통 번들을 본다.
 * 지원하지 않는 언어는 한국어로 답한다. resultCode는 언어와 관계없이 같다.
 */
public class ErrorMessages {

    public static final Set<String> SUPPORTED_LANGUAGES = Set.of("ko", "en", "ja", "zh");
    public static final Locale DEFAULT_LOCALE = Locale.KOREAN;
    static final String BASENAME = "data2flow/contracts/messages";

    private final MessageSource serviceMessages;
    private final ResourceBundleMessageSource commonMessages;

    public ErrorMessages(MessageSource serviceMessages) {
        this.serviceMessages = serviceMessages;
        this.commonMessages = new ResourceBundleMessageSource();
        this.commonMessages.setBasename(BASENAME);
        this.commonMessages.setDefaultEncoding("UTF-8");
        this.commonMessages.setFallbackToSystemLocale(false);
    }

    public String resolve(ErrorCode code, Object... args) {
        return resolve(code, LocaleContextHolder.getLocale(), args);
    }

    /** 요청 언어를 직접 넘긴다. DispatcherServlet 앞의 필터처럼 LocaleContextHolder가 아직 없을 때 쓴다 */
    public String resolve(ErrorCode code, Locale requested, Object... args) {
        Locale locale = supported(requested);
        if (serviceMessages != null) {
            try {
                return serviceMessages.getMessage(code.messageKey(), args, locale);
            } catch (NoSuchMessageException ignored) {
                // 서비스 번들에 없으면 공통 번들로
            }
        }
        return commonMessages.getMessage(code.messageKey(), args, code.code(), locale);
    }

    static Locale supported(Locale locale) {
        if (locale == null || !SUPPORTED_LANGUAGES.contains(locale.getLanguage())) {
            return DEFAULT_LOCALE;
        }
        return Locale.of(locale.getLanguage());
    }
}
