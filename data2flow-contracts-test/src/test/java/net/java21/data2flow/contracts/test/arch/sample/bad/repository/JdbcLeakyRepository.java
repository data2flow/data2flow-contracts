package net.java21.data2flow.contracts.test.arch.sample.bad.repository;

import java.util.List;

/** 어노테이션 없이 이름·패키지로 리포지토리를 알아보는 경우 */
public class JdbcLeakyRepository {

    public List<String> listNames(String keyword) {
        return List.of(keyword);
    }
}
