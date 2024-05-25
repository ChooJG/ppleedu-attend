package child.ppleedulms.attend;

import child.ppleedulms.course.CourseSectionRepository;
import child.ppleedulms.course.CourseSectionService;
import child.ppleedulms.course.CourseService;
import child.ppleedulms.domain.*;
import child.ppleedulms.dto.GetAttendPageDto;
import child.ppleedulms.dto.SetAttend;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.exception.NurigoMessageNotReceivedException;
import net.nurigo.sdk.message.model.KakaoOption;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attend")
@RequiredArgsConstructor
public class AttendController {

    private final AttendService attendService;
    private final CourseService courseService;
    private final CourseSectionService courseSectionService;
    

    //db 변경 사항 초기화
    @PostMapping("/resetSetting")
    public ResponseEntity<?> resetSetting(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        // 세션이 없는 경우 처리
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었거나 유효하지 않습니다.");
        }

        Member member = (Member) session.getAttribute("member");
        if (member == null || member.getRole() != MemberRole.TEACHER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
        }
        Long courseId = (Long) session.getAttribute("courseId");

        //AttendStatus에서 courseSectionId가 7 이상이면 다 삭제
        //CourseSection에서 courseSectionId가 7 이상이면 attendCode를 0으로
        attendService.resetSetting();
        return ResponseEntity.ok().body(true);

    }


        //학생 출석 페이지 보여주기
    @GetMapping("/showAttendPage")
    public ResponseEntity<?> showAttendPage(
            //@RequestParam("courseId") Long courseId,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        // 세션이 없는 경우 처리
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었거나 유효하지 않습니다.");
        }

        Member member = (Member) session.getAttribute("member");
        if (member == null || member.getRole() != MemberRole.STUDENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
        }
        Long courseId = (Long) session.getAttribute("courseId");

        try {
            GetAttendPageDto getAttendPageDto = attendService.showAttendPage(courseId, session);
            return ResponseEntity.ok(getAttendPageDto);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    //학생 출석 페이지에서, 출석 코드 입력
    @PostMapping("/attend")
    public ResponseEntity<?> attend(
            @RequestBody SetAttend setAttend,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        // 세션이 없는 경우 처리
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었거나 유효하지 않습니다.");
        }
        Member member = (Member) session.getAttribute("member");
        if (member == null || member.getRole() != MemberRole.STUDENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
        }

        AttendResponse response = attendService.attend(
                setAttend.getAttendCode(),
                setAttend.getCourseSectionId(),
                member.getId());

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response.getMessage());
        }

        //출석 시 보호자에게 메시지 전송
        attendService.sendKakaoMessage(member.getId());

        return ResponseEntity.ok(response.getMessage());
    }


    //수업 종료 버튼 클릭시 로직
    @PostMapping("/endSection")
    public ResponseEntity<?> endSection(
            @RequestParam("courseSectionId") Long courseSectionId,
            HttpServletRequest request
    ) {
        HttpSession session = request.getSession(false);
        // 세션이 없는 경우 처리
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었거나 유효하지 않습니다.");
        }
        Member member = (Member) session.getAttribute("member");
        if (member == null || member.getRole() != MemberRole.TEACHER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
        }

        try {
            attendService.endSection(courseSectionId);

            //수업 종료 후 학생들 귀가 메시지 전송
            attendService.sendEndClassMessages(courseSectionId);

            return ResponseEntity.ok(true);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error ending section: " + e.getMessage());
        }
    }

    //강사 출석 페이지에서, 출석 타이머 시작시 요청
    @PostMapping("/setAttendTimer")
    public ResponseEntity<?> setAttendTimer(
            //@RequestBody SetAttend setAttend,
            @RequestParam("courseSectionId") Long courseSectionId,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        // 세션이 없는 경우 처리
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었거나 유효하지 않습니다.");
        }

        Member member = (Member) session.getAttribute("member");
        if (member == null || member.getRole() != MemberRole.TEACHER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
        }

        TimerResponse response = attendService.setTimer(courseSectionId);

        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.ok(response);
    }

/*
    //강사 출석 페이지에서, 출석 타이머 종료시 요청
    @PatchMapping("/setEndTimer")
    public ResponseEntity<?> setEndTimer(
            @RequestParam("courseSectionId") Long courseSectionId,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        // 세션이 없는 경우 처리
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었거나 유효하지 않습니다.");
        }

        Member member = (Member) session.getAttribute("member");
        if (member == null || member.getRole() != MemberRole.TEACHER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
        }

        TimerResponse response = attendService.endTimer(courseSectionId);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.ok(response);
    }

 */


    //강사 출석 페이지에서, 모든 차시 title, id만 가져오기
    @GetMapping("/getSectionTitle")
    public ResponseEntity<?> getSectionTitle(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        // 세션이 없는 경우 처리
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었거나 유효하지 않습니다.");
        }

        Member member = (Member) session.getAttribute("member");

        // 멤버가 null인지 확인
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 멤버의 역할이 TEACHER가 아닌 경우 처리
        if (member.getRole() != MemberRole.TEACHER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한 없음");
        }

        Long courseId = (Long) session.getAttribute("courseId");

        // courseId가 세션에 없을 경우 처리
        if (courseId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("CourseId가 세션에 없습니다.");
        }

        return ResponseEntity.ok(courseSectionService.findCourseSectionTitle(courseId));
    }

    //강사 출석 페이지에서, 각 차시별 학생 출석 정보가져오기
    @GetMapping("/getSectionAttend")
    public ResponseEntity<?> getSectionAttend(
            @RequestParam("courseSectionId") Long courseSectionId,
            HttpSession session) {
        // 세션이 없는 경우 처리
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었거나 유효하지 않습니다.");
        }

        Member member = (Member) session.getAttribute("member");

        // 멤버가 null인지 확인
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 멤버의 역할이 TEACHER가 아닌 경우 처리
        if (member.getRole() != MemberRole.TEACHER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한 없음");
        }

        // courseSectionId에 해당하는 학생 정보 모두 가져오기
        AttendResponse sectionAttend = attendService.getSectionAttend(courseSectionId);

        // AttendResponse 객체에서 데이터 가져오기
        if (!sectionAttend.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(sectionAttend.getMessage());
        }

        return ResponseEntity.ok(sectionAttend.getData());
    }












}





