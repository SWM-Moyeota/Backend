package team.codingforest.moyeota.report.domain;

import java.util.Optional;

public interface Reports {
    Report save(Report report);
    Optional<Report> findById(Long id);
}