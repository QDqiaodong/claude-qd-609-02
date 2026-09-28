package com.archery.range.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archery.range.common.BizException;
import com.archery.range.domain.Course;
import com.archery.range.domain.CourseEnroll;
import com.archery.range.domain.Member;
import com.archery.range.domain.RangeDict;
import com.archery.range.dto.CourseDtos;
import com.archery.range.repository.CourseEnrollRepository;
import com.archery.range.repository.CourseRepository;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseEnrollRepository enrollRepository;
    private final MemberService memberService;
    private final CertService certService;

    public CourseService(CourseRepository courseRepository, CourseEnrollRepository enrollRepository,
            MemberService memberService, CertService certService) {
        this.courseRepository = courseRepository;
        this.enrollRepository = enrollRepository;
        this.memberService = memberService;
        this.certService = certService;
    }

    @Transactional(readOnly = true)
    public List<CourseDtos.CourseView> list() {
        return courseRepository.findAllByOrderByClassTimeAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseDtos.CourseView> listByLevel(String level) {
        return courseRepository.findByLevelOrderByClassTimeAsc(level).stream().map(this::toView).toList();
    }

    @Transactional
    public CourseDtos.CourseView create(CourseDtos.CourseSaveReq req) {
        if (!RangeDict.isValidCourseLevel(req.level())) {
            throw new BizException("课程等级只能是 初级 / 进阶 / 竞技");
        }
        if (req.capacity() == null || req.capacity() < 1 || req.capacity() > 30) {
            throw new BizException("人数上限需在 1 ~ 30 人之间");
        }
        String reqBowType = req.reqBowType() == null || req.reqBowType().isBlank()
                ? null : req.reqBowType().trim();
        Integer reqDistance = req.reqDistance();
        if (reqBowType != null) {
            if (!RangeDict.isValidBowType(reqBowType)) {
                throw new BizException("入课认证弓种只能是：反曲弓 / 复合弓 / 传统弓");
            }
            if (!RangeDict.isValidDistance(reqDistance)) {
                throw new BizException("入课认证射距只能是 10 / 18 / 30 / 50 米");
            }
        } else {
            reqDistance = null;
        }
        Course course = new Course();
        course.setCourseName(req.courseName());
        course.setCoach(req.coach());
        course.setLevel(req.level());
        course.setClassTime(req.classTime());
        course.setCapacity(req.capacity());
        course.setEnrolled(0);
        course.setVenue(req.venue());
        course.setReqBowType(reqBowType);
        course.setReqDistance(reqDistance);
        return toView(courseRepository.save(course));
    }

    /**
     * 报名：满员拦、重复报名拦、竞技课要求金卡、进阶课要求银卡及以上；
     * 课程标注了弓种射距认证要求时，还必须持有覆盖该适用范围的当前有效认证
     * （过期 / 撤回 / 驳回 / 待复核都会被拦下）。
     */
    @Transactional
    public CourseDtos.CourseView enroll(Long id, CourseDtos.EnrollReq req) {
        Course course = require(id);
        Member member = memberService.require(req.memberId());
        if (course.getEnrolled() >= course.getCapacity()) {
            throw new BizException("课程 [" + course.getCourseName() + "] 已报满，无法报名");
        }
        if (enrollRepository.existsByCourseIdAndMemberId(id, member.getId())) {
            throw new BizException("会员 [" + member.getName() + "] 已报名该课程，请勿重复报名");
        }
        String needLevel = RangeDict.courseMinMemberLevel(course.getLevel());
        if (RangeDict.memberLevelRank(member.getLevel()) < RangeDict.memberLevelRank(needLevel)) {
            throw new BizException(RangeDict.courseLevelName(course.getLevel()) + "课程仅面向"
                    + RangeDict.memberLevelName(needLevel) + "及以上，会员 [" + member.getName() + "] 等级不足");
        }
        if (course.getReqBowType() != null && course.getReqDistance() != null
                && !certService.covers(member.getId(), course.getReqBowType(), course.getReqDistance())) {
            throw new BizException("课程 [" + course.getCourseName() + "] 要求持有「"
                    + RangeDict.bowTypeName(course.getReqBowType()) + " · " + course.getReqDistance()
                    + " 米」有效认证（高射距认证可覆盖低射距），会员 [" + member.getName()
                    + "] 当前认证不满足适用范围");
        }
        CourseEnroll enroll = new CourseEnroll();
        enroll.setCourse(course);
        enroll.setMember(member);
        enroll.setEnrollTime(LocalDateTime.now());
        enrollRepository.save(enroll);

        course.setEnrolled((int) enrollRepository.countByCourseId(id));
        return toView(courseRepository.save(course));
    }

    @Transactional
    public CourseDtos.CourseView cancel(Long id, Long memberId) {
        Course course = require(id);
        CourseEnroll enroll = enrollRepository.findByCourseIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BizException("该会员未报名此课程"));
        enrollRepository.delete(enroll);
        course.setEnrolled((int) enrollRepository.countByCourseId(id));
        return toView(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public Course require(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new BizException("课程不存在：id=" + id));
    }

    private CourseDtos.CourseView toView(Course course) {
        List<CourseDtos.EnrollView> enrolls = enrollRepository.findByCourseIdOrderByIdAsc(course.getId()).stream()
                .map(item -> new CourseDtos.EnrollView(
                        item.getId(),
                        item.getMember() == null ? null : item.getMember().getId(),
                        item.getMember() == null ? "" : item.getMember().getName(),
                        item.getMember() == null ? "" : item.getMember().getCardNo(),
                        item.getEnrollTime()))
                .toList();
        int enrolled = enrolls.size();
        return new CourseDtos.CourseView(
                course.getId(),
                course.getCourseName(),
                course.getCoach(),
                course.getLevel(),
                RangeDict.courseLevelName(course.getLevel()),
                course.getClassTime(),
                course.getCapacity(),
                enrolled,
                course.getVenue(),
                enrolled >= course.getCapacity(),
                course.getReqBowType(),
                course.getReqBowType() == null ? "" : RangeDict.bowTypeName(course.getReqBowType()),
                course.getReqDistance(),
                enrolls);
    }
}
