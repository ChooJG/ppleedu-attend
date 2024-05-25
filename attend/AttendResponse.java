package child.ppleedulms.attend;

import lombok.Data;

@Data
public class AttendResponse {
    private boolean success;
    private String message;
    private Long courseId;
    private Object data; // 필요에 따라 자세한 데이터 포함

    public AttendResponse(boolean success, String message, Long courseId) {
        this.success = success;
        this.message = message;
        this.courseId = courseId;
    }

    public AttendResponse(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
}
