package child.ppleedulms.attend;

import child.ppleedulms.course.CourseRepository;
import child.ppleedulms.course.CourseSectionRepository;
import child.ppleedulms.course.CourseService;
import child.ppleedulms.course.EnrollCourseRepository;
import child.ppleedulms.domain.*;
import child.ppleedulms.dto.GetAttendPageDto;
import child.ppleedulms.member.MemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.exception.NurigoMessageNotReceivedException;
import net.nurigo.sdk.message.model.KakaoOption;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class AttendService {

    private final AttendRepository attendRepository;
    private final EnrollCourseRepository enrollCourseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final MemberRepository memberRepository;
    private final CourseService courseService;

    @Value("${solapi.api.key}")
    private String apiKey;

    @Value("${solapi.api.secret}")
    private String apiSecret;

    @Value("${solapi.api.url}")
    private String apiUrl;

    @Value("${solapi.api.fromTel}")
    private String fromTel;


    //강의 개설 시 각 CourseSection에 학생들 정보 등록
    //기본 출석 정보는 before로 설정
    public void saveMemberAttendInCourse(Long courseId) {

        List<Member> members = enrollCourseRepository.findAllMembersByCourseId(courseId);
        members = members.stream()
                .filter(member -> member.getRole() == MemberRole.STUDENT)
                .collect(Collectors.toList());

        for (Member member : members) {
            System.out.println("member.getName() = " + member.getName());
        }

        List<CourseSection> courseSectionList = courseSectionRepository.findOneByCourseId(courseId);

        for (CourseSection courseSection : courseSectionList) {
            System.out.println("courseSection.getTitle() = " + courseSection.getTitle());
        }

        for (CourseSection courseSection : courseSectionList) {
            for (Member member : members) {

                AttendStatus attendStatus = new AttendStatus();
                attendStatus.setMember(member);
                attendStatus.setCourseSection(courseSection);
                attendStatus.setAttendType(AttendType.BEFORE);
                attendRepository.save(attendStatus);

            }


        }

    }

    public GetAttendPageDto showAttendPage(Long courseId, HttpSession session) {
        if (session == null || courseId == null) {
            System.out.println("courseId = " + courseId);
            throw new RuntimeException("유효하지 않은 세션 키");
        }

        Member member = (Member) session.getAttribute("member");
        if (member == null) {
            System.out.println("member = " + member);
            throw new RuntimeException("세션에 저장된 멤버를 찾을 수 없습니다");
        }

        Course course = courseService.showCourseDetail(courseId);
        if (course == null) {
            System.out.println("course = " + course);
            throw new RuntimeException("강의가 존재하지 않습니다.");
        }

        List<AttendStatus> attendStatuses = showAttend(courseId, member.getId());
        List<GetAttendPageDto.AttendStatusDto> attendStatusDtos = attendStatuses.stream()
                .map(attendStatus -> {
                    GetAttendPageDto.AttendStatusDto dto = new GetAttendPageDto.AttendStatusDto();
                    dto.setAttend_status_id(attendStatus.getId());
                    dto.setAttendAt(Optional.ofNullable(attendStatus.getAttendAt()).orElse(null));
                    dto.setAttendType(attendStatus.getAttendType());
                    dto.setStartAt(getStartAt(attendStatus.getCourseSection().getId()));
                    return dto;
                }).collect(Collectors.toList());

        GetAttendPageDto getAttendPageDto = new GetAttendPageDto();
        getAttendPageDto.setCourse_id(courseId);
        getAttendPageDto.setCourseName(course.getTitle());
        getAttendPageDto.setAttendStatusDto(attendStatusDtos);

        return getAttendPageDto;
    }


    //특정 학생 특정 강의에서의 출석 정보 가져오기
    @Transactional(readOnly = true)
    public List<AttendStatus> showAttend(Long courseId, Long memberId) {
        List<AttendStatus> attendByStudent = attendRepository.findAttendByStudent(courseId, memberId);
        if (attendByStudent == null) {
            throw new RuntimeException("출석정보가 존재하지 않습니다");
        }
        return attendByStudent.stream()
                .filter(attend -> !"BEFORE_START".equals(attend.getAttendType())) // "before" 타입 제외
                .sorted(Comparator.comparing((AttendStatus attend) -> attend.getCourseSection()
                        .getStartAt(), Comparator.nullsLast(Comparator.reverseOrder()))) // CourseSection의 startAt 기준으로 내림차순 정렬
                .collect(Collectors.toList());
    }

    //CourseSectionId 주고 시작 시간 가져오기
    //임시 메서드, 섹션에 대한 정보가 없을 경우 직접 넣어준다
    public LocalDateTime getStartAtTmp() {
        CourseSection courseSection = courseSectionRepository.findLateOneByCourseId(1L);
        return courseSection.getStartAt();
    }

    //CourseSectionId 주고 종료 시간 가져오기
    //임시 메서드, 섹션에 대한 정보가 없을 경우 직접 넣어준다
    public LocalDateTime getEndAtTmp() {
        CourseSection courseSection = courseSectionRepository.findLateOneByCourseId(1L);
        return courseSection.getEndAt();
    }

    //CourseSectionId 주고 시작 시간 가져오기
    public LocalDateTime getStartAt(Long courseSectionId) {
        CourseSection courseSection = courseSectionRepository.findOne(courseSectionId);
        return courseSection.getStartAt();
    }

    //CourseSectionId 주고 종료 시간 가져오기
    private LocalDateTime getEndAt(Long courseSectionId) {
        CourseSection courseSection = courseSectionRepository.findOne(courseSectionId);
        return courseSection.getEndAt();
    }

    //출석 코드 입력
    public AttendResponse attend(int attendCode, Long courseSectionId, Long memberId) {
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime classEndTime = getEndAt(courseSectionId);
        LocalDateTime startTimerAt = courseSectionRepository.findOne(courseSectionId).getStartTimerAt();

        if (currentTime.isAfter(classEndTime)) {
            return new AttendResponse(false, "The class has ended", null);
        }

        if(courseSectionRepository.findOne(courseSectionId).getAttendTimerStatus() == AttendTimerStatus.FINISHED) {
            return new AttendResponse(false, "The class has ended", null);
        }

        if (!attendRepository.isAttendCode(attendCode, courseSectionId)) {
            return new AttendResponse(false, "Invalid attend code.", null);
        }

        AttendTimerStatus attendTimerStatus = attendRepository.timerStatus(courseSectionId);
        if (attendTimerStatus == AttendTimerStatus.BEFORE_START) {
            return new AttendResponse(false, "Attendance not started yet.", null);
        }

        // 현재 시간이 타이머 시작 시간 10분 이전인지 이후인지에 따라 출석 상태 결정
        AttendType attendType;
        if (currentTime.isBefore(startTimerAt.plusMinutes(10))) {
            attendType = AttendType.PRESENT;
        } else {
            attendType = AttendType.LATE;
        }

        attendRepository.setAttendState(courseSectionId, memberId, attendType);
        //sendKakaoMessage(memberId);

        return new AttendResponse(true, "Attendance recorded successfully.", courseSectionId);
    }

    public void sendKakaoMessage(Long memberId) {
        Member member = memberRepository.findOne(memberId);
        if (member == null) {
            throw new IllegalStateException("member가 존재하지 않습니다.");
        }

        DefaultMessageService messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, apiUrl);

        KakaoOption kakaoOption = new KakaoOption();
        kakaoOption.setPfId("KA01PF230731062735941N7onS5jD4HX");
        kakaoOption.setTemplateId("KA01TP240501082035709JLsmDzYVAFg");

        kakaoOption.setDisableSms(true);
        HashMap<String, String> variables = new HashMap<>();
        variables.put("#{NAME}", member.getName());
        kakaoOption.setVariables(variables);

        Message message = new Message();
        message.setFrom(fromTel);
        message.setTo(member.getParent_tel());
        message.setKakaoOptions(kakaoOption);

        try {
            messageService.send(message);
        } catch (NurigoMessageNotReceivedException exception) {
            System.out.println(exception.getFailedMessageList());
            System.out.println(exception.getMessage());
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
    }


    //수업 종료
    public void endSection(Long courseSectionId) {
        CourseSection courseSection = courseSectionRepository.findOne(courseSectionId);
        courseSection.setAttendTimerStatus(AttendTimerStatus.FINISHED);
        courseSectionRepository.save(courseSection);

        //수업 종료 후 학생들 귀가 메시지 전송
        //sendEndClassMessages(courseSectionId);
    }

    public void sendEndClassMessages(Long courseSectionId) {
        DefaultMessageService messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, apiUrl);

        KakaoOption kakaoOption = new KakaoOption();
        kakaoOption.setPfId("KA01PF230731062735941N7onS5jD4HX");
        kakaoOption.setTemplateId("KA01TP240501082206689FVrFBPvuJy8");

        kakaoOption.setDisableSms(true);

        //List<Member> members = memberRepository.findAll();  // 모든 멤버를 가져오는 레포지토리 호출
        
        //해당 수업을 듣고, courseSectionId에 해당하는 AttendType이 Absent가 아닌 학생
        List<Member> members = attendRepository.getMembersNotAbsent(courseSectionId);

        for (Member member : members) {
            HashMap<String, String> variables = new HashMap<>();
            variables.put("#{NAME}", member.getName());
            kakaoOption.setVariables(variables);

            Message message = new Message();
            message.setFrom(fromTel);
            message.setTo(member.getParent_tel());
            message.setKakaoOptions(kakaoOption);

            try {
                messageService.send(message);
            } catch (NurigoMessageNotReceivedException exception) {
                System.out.println("Failed to send message to: " + member.getParent_tel());
                System.out.println(exception.getFailedMessageList());
                System.out.println(exception.getMessage());
            } catch (Exception exception) {
                System.out.println("Error sending message to: " + member.getParent_tel());
                System.out.println(exception.getMessage());
            }
        }
    }


    //출석 코드 입력
    public AttendResponse attendTmp(int attendCode, Long memberId) {

        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime classEndTime = getEndAtTmp();

        CourseSection courseSection = attendRepository.isAttendCodeTmp();

        if (currentTime.isAfter(classEndTime) || courseSection.getAttendTimerStatus() == AttendTimerStatus.FINISHED) {
            return new AttendResponse(false, "수업이 종료되었습니다.", null);
        }


        
        if (courseSection.getAttendCode() != attendCode) {
            return new AttendResponse(false, "출석 코드가 잘못되었습니다.", null);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTimerAt = courseSection.getStartTimerAt();
        LocalDateTime startTimerAtPlusTenMinutes = startTimerAt.plusMinutes(10);

        AttendType attendType = (startTimerAtPlusTenMinutes.isAfter(now)) ?
                AttendType.PRESENT : AttendType.LATE;

        attendRepository.setAttendState(courseSection.getId(), memberId, attendType);
        return new AttendResponse(true, "출석되었습니다.", courseSection.getId());
    }

    //타이머 시작 버튼 눌리면
    //1. 출석 코드 db 에 기록
    //2. 타이머 셋팅 변경
    public TimerResponse setTimer(Long courseSectionId){
        try {
            CourseSection courseSection = courseSectionRepository.findOne(courseSectionId);

            if (courseSection == null) {
                return new TimerResponse(false, "강의 섹션을 찾을 수 없습니다.");
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = courseSection.getStartAt();

            // 수업 시작 시간보다 빠르게 누르지 않도록 함
            //테스트 환경 위해서 잠깐 지워놓자
            //if (now.isBefore(startAt)) {
            //    return new TimerResponse(false, "수업 시작 시간 이전에는 타이머를 시작할 수 없습니다.");
            //}

            // 이미 수업 시작 버튼을 눌렀거나, 종료된 수업은 타이머 세팅 x
            // 출석 코드가 0이 아닌 경우 타이머 셋팅 x
            if (courseSection.getAttendCode() != 0) {
                return new TimerResponse(false, "타이머가 이미 시작되었습니다.", courseSection.getAttendCode());
            }

            //타이머 시작 시 해당 차시 학생들 출석정보 업데이트
            //디폴트 ABSENT(결석) 으로 설정
            // 1. 해당 수업을 듣는 학생들의 ID를 모두 가져옴
            List<Member> allMembers = enrollCourseRepository.findAllMembersByCourseId(courseSection.getCourse().getId());

            // 2. 각 학생의 출석 상태를 디폴트 ABSENT(결석)으로 설정하고 저장
            for (Member member : allMembers) {
                AttendStatus attendStatus = new AttendStatus();
                attendStatus.setCourseSection(courseSection);
                attendStatus.setMember(member);
                attendStatus.setAttendType(AttendType.ABSENT); // 기본값 설정

                attendRepository.save(attendStatus);
            }


            SecureRandom random = new SecureRandom();
            int attendCode = 1000 + random.nextInt(9000);
            attendRepository.startTimer(attendCode, courseSectionId);
            return new TimerResponse(true, "타이머가 성공적으로 시작되었습니다.", attendCode);
        } catch (Exception e) {
            return new TimerResponse(false, "타이머 시작 중 오류 발생: " + e.getMessage());
        }
    }


    public TimerResponse setTimerTmp(int attendCode){
        try {
            attendRepository.startTimerTmp(attendCode);
            return new TimerResponse(true, "Timer started successfully");
        } catch (Exception e) {
            return new TimerResponse(false, "Error starting timer: " + e.getMessage());
        }
    }

    public TimerResponse endTimer(Long courseSectionId) {
        try {
            attendRepository.endTimer(courseSectionId);
            return new TimerResponse(true, "Timer ended successfully");
//        } catch (SpecificException e) {  // SpecificException을 실제 예외 유형으로 변경
//            return new TimerResponse(false, "Specific error ending timer: " + e.getMessage());
        } catch (Exception e) {
            return new TimerResponse(false, "General error ending timer: " + e.getMessage());
        }
    }


    public TimerResponse endTimerTmp() {
        try {
            attendRepository.endTimerTmp();
            return new TimerResponse(true, "Timer ended successfully");
        } catch (Exception e) {
            return new TimerResponse(false, "Error end timer: " + e.getMessage());
        }
    }


    //각 차시당 학생 출석 정보 가져오기
    public AttendResponse getSectionAttend(Long courseSectionId) {
        try {
            List<AttendStatus> attends = attendRepository.findAttendByCourseSectionId(courseSectionId);
            if (attends == null) {
                return new AttendResponse(false, "No attendance data found", new ArrayList<>());
            }

            List<StudentsAttendStatus> attendStatusList = new ArrayList<>();
            for (AttendStatus attendStatus : attends) {
                if (attendStatus.getMember() != null) {
                    StudentsAttendStatus studentsAttendStatus = new StudentsAttendStatus();
                    studentsAttendStatus.setAttendStatusId(attendStatus.getId());
                    studentsAttendStatus.setAttendType(attendStatus.getAttendType());
                    studentsAttendStatus.setName(attendStatus.getMember().getName());
                    attendStatusList.add(studentsAttendStatus);
                }
            }
            return new AttendResponse(true, "Load data successfully", attendStatusList);
        } catch (Exception e) {
            return new AttendResponse(false, "Error loading data: " + e.getMessage(), new ArrayList<>());
        }
    }

    public AttendResponse getSectionAttendTmp() {
        try {
            CourseSection attendCodeTmp = attendRepository.isAttendCodeTmp();
            List<AttendStatus> attends = attendRepository.findAttendByCourseSectionId(attendCodeTmp.getId());
            return new AttendResponse(true, "Load data Succesfully", attends);
        } catch (Exception e) {
            return new AttendResponse(false, "Error load data: " + e.getMessage(), 0L);
        }
    }


    public void genAttendTmp(Long memberId) {
        AttendStatus attendStatus1 = new AttendStatus();
        attendStatus1.setAttendType(AttendType.PRESENT);
        attendStatus1.setMember(memberRepository.findOne(memberId));
        attendStatus1.setCourseSection(courseSectionRepository.findOne(1L));
        attendRepository.save(attendStatus1);

        AttendStatus attendStatus2 = new AttendStatus();
        attendStatus2.setAttendType(AttendType.PRESENT);
        attendStatus2.setMember(memberRepository.findOne(memberId));
        attendStatus2.setCourseSection(courseSectionRepository.findOne(2L));
        attendRepository.save(attendStatus2);

        AttendStatus attendStatus3 = new AttendStatus();
        attendStatus3.setAttendType(AttendType.PRESENT);
        attendStatus3.setMember(memberRepository.findOne(memberId));
        attendStatus3.setCourseSection(courseSectionRepository.findOne(3L));
        attendRepository.save(attendStatus3);

        AttendStatus attendStatus4 = new AttendStatus();
        attendStatus4.setAttendType(AttendType.PRESENT);
        attendStatus4.setMember(memberRepository.findOne(memberId));
        attendStatus4.setCourseSection(courseSectionRepository.findOne(4L));
        attendRepository.save(attendStatus4);

        AttendStatus attendStatus5 = new AttendStatus();
        attendStatus5.setAttendType(AttendType.PRESENT);
        attendStatus5.setMember(memberRepository.findOne(memberId));
        attendStatus5.setCourseSection(courseSectionRepository.findOne(5L));
        attendRepository.save(attendStatus5);

        AttendStatus attendStatus6 = new AttendStatus();
        attendStatus6.setAttendType(AttendType.PRESENT);
        attendStatus6.setMember(memberRepository.findOne(memberId));
        attendStatus6.setCourseSection(courseSectionRepository.findOne(6L));
        attendRepository.save(attendStatus6);


    }


    public int isCode() {
        CourseSection attendCodeTmp = attendRepository.isAttendCodeTmp();
        return attendCodeTmp.getAttendCode();
    }


    public List<Member> allMember() {
        CourseSection attendCodeTmp = attendRepository.isAttendCodeTmp();
        return attendRepository.getMembersNotAbsent(attendCodeTmp.getId());
    }

    public CourseSection courseSection() {
        CourseSection attendCodeTmp = attendRepository.isAttendCodeTmp();
        return attendCodeTmp;
    }


    public void resetSetting() {
        attendRepository.resetSetting();
        attendRepository.resetSetting2();
    }


}
