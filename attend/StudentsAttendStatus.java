package child.ppleedulms.attend;

import child.ppleedulms.domain.AttendStatus;
import child.ppleedulms.domain.AttendType;
import lombok.Data;

@Data
public class StudentsAttendStatus {

    private Long attendStatusId;
    private String name;
    private AttendType attendType;

}
