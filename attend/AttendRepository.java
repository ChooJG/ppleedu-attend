package child.ppleedulms.attend;

import child.ppleedulms.course.CourseSectionRepository;
import child.ppleedulms.course.EnrollCourseRepository;
import child.ppleedulms.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class AttendRepository {

    @PersistenceContext
    private EntityManager em;

    public void save(AttendStatus attendStatus) {
        if (attendStatus != null) {
            em.persist(attendStatus);
        }
    }



    public AttendStatus findOne(Long id) {
        return em.find(AttendStatus.class, id);
    }

    public List<AttendStatus> findAll(){
        return em.createQuery("select c from AttendStatus c", AttendStatus.class)
                .getResultList();
    }

    //학생이 해당 수업 출결 내용 확인
    public List<AttendStatus> findAttendByStudent(Long courseId, Long memberId) {
        String jpql = "SELECT a FROM AttendStatus a JOIN a.courseSection s JOIN s.course c WHERE c.id = :courseId AND a.member.id = :memberId";
        TypedQuery<AttendStatus> query = em.createQuery(jpql, AttendStatus.class);
        query.setParameter("courseId", courseId);
        query.setParameter("memberId", memberId);
        return query.getResultList();
    }

    //강사가 강의 각 차시에서 학생들 출결 내용 확인
    public List<AttendStatus> findAttendByTeacher(Long courseSectionId) {
        String jpql = "SELECT a FROM AttendStatus a WHERE a.courseSection.id = :sectionId";
        TypedQuery<AttendStatus> query = em.createQuery(jpql, AttendStatus.class);
        query.setParameter("sectionId", courseSectionId);
        return query.getResultList();
    }

    //출석 코드가 맞는지 검증하는 로직
    public boolean isAttendCode(int attendCode, Long courseSectionId) {
        CourseSection courseSection = em.find(CourseSection.class, courseSectionId);
        if (courseSection == null) {
            throw new IllegalStateException("강의 섹션이 없습니다 : " + courseSectionId);
        }
        return courseSection.getAttendCode() == attendCode;
    }

    //가장 최근 객체 가져오는 (임시)
    public CourseSection isAttendCodeTmp() {
        TypedQuery<CourseSection> query = em.createQuery(
                "SELECT cs FROM CourseSection cs ORDER BY cs.startAt DESC", CourseSection.class);
        query.setMaxResults(1); // 결과를 하나만 받기 위해 설정
        CourseSection courseSection = query.getSingleResult(); // 가장 최근 startAt을 가진 객체를 가져옴

        return courseSection;
    }

    //타이머 상태 파악. 이거에 따라 지각인지 아닌지 확인
    public AttendTimerStatus timerStatus(Long courseSectionId) {
        CourseSection courseSection = em.find(CourseSection.class, courseSectionId);
        if (courseSection == null) {
            throw new IllegalStateException("강의 섹션이 없습니다 : " + courseSectionId);
        }
        return courseSection.getAttendTimerStatus();
    }



    public void setAttendState(Long courseSectionId, Long memberId, AttendType attendType) {
        TypedQuery<AttendStatus> query = em.createQuery(
                "SELECT a FROM AttendStatus a WHERE a.courseSection.id = :courseSectionId AND a.member.id = :memberId",
                AttendStatus.class);
        query.setParameter("courseSectionId", courseSectionId);
        query.setParameter("memberId", memberId);

        AttendStatus attendStatus = query.getSingleResult();
        if (attendStatus != null) {
            attendStatus.setAttendType(attendType);
            em.merge(attendStatus);
        } else {
            throw new IllegalStateException("No matching attendance record found");
        }

    }

    //타이머 시작시 courseSectionId 의 attendCode 설정
    public int startTimer(int attendCode, Long courseSectionId){

        CourseSection courseSection = em.find(CourseSection.class, courseSectionId);
        if (courseSection != null) {
            courseSection.setAttendCode(attendCode);
            courseSection.setAttendTimerStatus(AttendTimerStatus.IN_PROGRESS);
            //타이머 시작한 시간 행에 추가
            courseSection.setStartTimerAt(LocalDateTime.now());
            em.merge(courseSection);
        }

        return attendCode;
    }

    //타이머 시작시 courseSectionId 의 attendCode 설정
    public int startTimerTmp(int attendCode) {
        // 가장 최근에 시작하는 CourseSection 찾기
        TypedQuery<CourseSection> query = em.createQuery(
                "SELECT c FROM CourseSection c ORDER BY c.startAt DESC",
                CourseSection.class);
        query.setMaxResults(1);
        CourseSection courseSection = query.getSingleResult();

        if (courseSection != null) {
            courseSection.setAttendCode(attendCode);
            courseSection.setAttendTimerStatus(AttendTimerStatus.IN_PROGRESS);
            //타이머 시작한 시간 행에 추가
            courseSection.setStartTimerAt(LocalDateTime.now());
            em.merge(courseSection);
        }

        return attendCode;
    }

    //타이머 종료시 courseSectionId의 attendStatus 설정
    public Long endTimer(Long courseSectionId) {

        CourseSection courseSection = em.find(CourseSection.class, courseSectionId);
        courseSection.setAttendTimerStatus(AttendTimerStatus.FINISHED);
        em.merge(courseSection);

        return courseSectionId;
    }


    //타이머 종료시 courseSectionId의 attendStatus 설정
    public Long endTimerTmp() {
        // 가장 최근에 시작하는 CourseSection 찾기
        TypedQuery<CourseSection> query = em.createQuery(
                "SELECT c FROM CourseSection c ORDER BY c.startAt DESC",
                CourseSection.class);
        query.setMaxResults(1);
        CourseSection courseSection = query.getSingleResult();

        if (courseSection != null) {
            courseSection.setAttendTimerStatus(AttendTimerStatus.FINISHED);
            em.merge(courseSection);
            return courseSection.getId();  // 성공적으로 업데이트한 courseSection의 ID를 반환
        }

        return null; // 적절한 CourseSection이 없는 경우 null을 반환
    }

    //각 차시당 전체 학생 출석 정보 가져오기
    public List<AttendStatus> findAttendByCourseSectionId(Long courseSectionId) {
        String jpql = "SELECT a FROM AttendStatus a JOIN a.member m WHERE a.courseSection.id = :courseSectionId AND m.role = :memberRole";
        return em.createQuery(jpql, AttendStatus.class)
                .setParameter("courseSectionId", courseSectionId)
                .setParameter("memberRole", MemberRole.STUDENT)
                .getResultList();
    }


    /*
    public List<AttendStatus> findAttendByCourseSectionId(Long courseSectionId) {
        String jpql = "SELECT a FROM AttendStatus a WHERE a.courseSection.id = :courseSectionId";
        return em.createQuery(jpql, AttendStatus.class)
                .setParameter("courseSectionId", courseSectionId)
                .getResultList();
    }

     */

    public List<Member> getMembersNotAbsent(Long courseSectionId) {
        String jpql = "SELECT a.member FROM AttendStatus a WHERE a.attendType != :absentType AND a.courseSection.id = :sectionId";
        TypedQuery<Member> query = em.createQuery(jpql, Member.class);
        query.setParameter("absentType", AttendType.ABSENT);
        query.setParameter("sectionId", courseSectionId);
        return query.getResultList();
    }


    public void resetSetting() {
        em.createQuery("DELETE FROM AttendStatus a WHERE a.courseSection.id >= :courseSectionId")
                .setParameter("courseSectionId", 7L)
                .executeUpdate();
    }

    public void resetSetting2(){
        em.createQuery("UPDATE CourseSection c SET c.attendCode = 0 WHERE c.id >= :courseSectionId")
                .setParameter("courseSectionId", 7L)
                .executeUpdate();

        // Update AttendTimerStatus to BEFORE_START
        em.createQuery("UPDATE CourseSection c SET c.attendTimerStatus = :status WHERE c.id >= :courseSectionId")
                .setParameter("status", AttendTimerStatus.BEFORE_START)
                .setParameter("courseSectionId", 7L)
                .executeUpdate();

        // Update startTimerAt to null
        em.createQuery("UPDATE CourseSection c SET c.startTimerAt = NULL WHERE c.id >= :courseSectionId")
                .setParameter("courseSectionId", 7L)
                .executeUpdate();
    }



}
