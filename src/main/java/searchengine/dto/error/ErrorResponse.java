package searchengine.dto.error;

import lombok.Data;

@Data
public class ErrorResponse {
    boolean result = false;
    String error;
}
