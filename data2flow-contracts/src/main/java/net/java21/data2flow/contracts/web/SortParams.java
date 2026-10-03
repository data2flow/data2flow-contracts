package net.java21.data2flow.contracts.web;

import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.error.FieldErrorDetail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 정렬 파라미터 {@code sort=필드,asc|desc}(여러 개는 반복, api-rules §5, BR-OPS-19). 허용 필드는 API 명세에 나열한 것만 받는다.
 * 허용 목록 밖 필드나 잘못된 방향은 400 {@code INVALID_REQUEST}(errors[0].field=sort)다. 방향을 생략하면 asc.
 */
public record SortParams(List<Order> orders) {

    public record Order(String field, boolean ascending) {
    }

    public SortParams {
        orders = List.copyOf(orders);
    }

    /**
     * @param sort          요청의 sort 값들(null이면 기본 정렬)
     * @param allowedFields 허용 필드
     * @param defaults      sort가 없을 때 쓸 정렬
     */
    public static SortParams parse(Collection<String> sort, Collection<String> allowedFields, Order... defaults) {
        if (sort == null || sort.stream().allMatch(s -> s == null || s.isBlank())) {
            return new SortParams(List.of(defaults));
        }
        List<Order> orders = new ArrayList<>();
        for (String raw : sort) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split(",", -1);
            String field = parts[0].strip();
            String direction = parts.length > 1 ? parts[1].strip().toLowerCase(Locale.ROOT) : "asc";
            if (!allowedFields.contains(field) || parts.length > 2 || !(direction.equals("asc") || direction.equals("desc"))) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST,
                        List.of(new FieldErrorDetail("sort", "SORT_NOT_ALLOWED", raw)));
            }
            orders.add(new Order(field, direction.equals("asc")));
        }
        return new SortParams(orders);
    }

    /**
     * SQL ORDER BY 본문(예: {@code name DESC, id ASC}). 필드 → 열 이름 표에 있는 값만 쓰므로 SQL 주입이 생기지 않는다.
     * 정렬이 없으면 빈 문자열.
     */
    public String toOrderBy(Map<String, String> fieldToColumn) {
        return orders.stream()
                .map(o -> {
                    String column = fieldToColumn.get(o.field());
                    if (column == null) {
                        throw new IllegalArgumentException("열 이름 표에 없는 정렬 필드: " + o.field());
                    }
                    return column + (o.ascending() ? " ASC" : " DESC");
                })
                .collect(Collectors.joining(", "));
    }
}
