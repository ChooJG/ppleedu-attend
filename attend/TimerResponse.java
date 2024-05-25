package child.ppleedulms.attend;

import lombok.Data;

@Data
public class TimerResponse {

    private boolean success;
    private String message;
    private int attendCode;
    private Object data; // 필요에 따라 자세한 데이터 포함

    public TimerResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public TimerResponse(boolean success, String message, int attendCode) {
        this.success = success;
        this.message = message;
        this.attendCode = attendCode;
    }

    public TimerResponse(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
}
