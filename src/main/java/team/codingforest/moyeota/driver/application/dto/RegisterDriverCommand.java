package team.codingforest.moyeota.driver.application.dto;

public record RegisterDriverCommand(Long userId, String qualificationNumber, String bankName, String accountNumber,
                                    Integer seats, String plateNumber, String type) {
}
