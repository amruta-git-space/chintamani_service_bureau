package in.chintamani.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseStatusService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isDatabaseReachable() {
        try {
            return Integer.valueOf(1).equals(jdbcTemplate.queryForObject("SELECT 1", Integer.class));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
