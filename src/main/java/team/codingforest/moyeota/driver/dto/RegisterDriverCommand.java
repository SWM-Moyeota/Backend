package team.codingforest.moyeota.driver.dto;

public record RegisterDriverCommand(Long userId, String qualificationNumber, String bankName, String accountNumber,
                                    Integer seats, String plateNumber, String type) {
}
