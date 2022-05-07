package microservices.examples.tx.course;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import lombok.extern.slf4j.Slf4j;
import microservices.examples.tx.gateway.BoardGateway;
import microservices.examples.tx.gateway.MemberGateway;
import microservices.examples.tx.system.ExampleSecurityContext;
import microservices.examples.tx.system.UserDetails;

@Service
@Slf4j
public class CourseService {

	CourseMyBatisDAO courseMapper;
	MemberGateway memberGateway;
	BoardGateway boardGateway;
	CourseJPARepository courseRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	public PlatformTransactionManager getTransactionManager() {
		return transactionManager;
	}

	public void setTransactionManager(
				PlatformTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	@Autowired
	public CourseService(CourseMyBatisDAO courseMapper,
				MemberGateway memberGateway, BoardGateway boardGateway,
				CourseJPARepository courseRepository) {
		this.courseMapper = courseMapper;
		this.memberGateway = memberGateway;
		this.boardGateway = boardGateway;
		this.courseRepository = courseRepository;
	}

	@Transactional
	public void createCourse(Course course) {
		log.error("-_-;;1-1");
		// 0. 입력값 Validation
		UserDetails loginUser = ExampleSecurityContext.getCurrentLoginUser();
		ensureUserCanCreateCourse(loginUser);
		validateNewCourse(course);

		// 1. 과정 정보 생성
		String courseId = course.getId();
		log.error("-_-;;1-2");
		courseMapper.create(course);
		log.error("-_-;;1-3");

		// 2. 멤버 서비스에 생성자를 관리자로 등록 w/ courseId
		memberGateway.addManager(courseId, loginUser);

		// 3. 과정 게시판 서비스에 게시판 생성 w/ courseId
		try {
			boardGateway.addBoard(courseId, loginUser);

		} catch (Exception originalException) {
			try {
				memberGateway.removeManager(courseId, loginUser);
			} catch (Exception compensationExcention) {
				log.error("COMPENSATION TRANSACTION ERROR! {}",
							compensationExcention);
			}
			throw originalException;
		}
		log.error("-_-;;1-10");
	}

public void createCourseOptimisticLocking_oneTryCatchBlock(Course course) {
	// 0. 입력값 Validation
	UserDetails loginUser = ExampleSecurityContext.getCurrentLoginUser();
	ensureUserCanCreateCourse(loginUser);
	validateNewCourse(course);

	TransactionDefinition txDefinition = new DefaultTransactionDefinition();
	TransactionStatus txStatus = transactionManager
				.getTransaction(txDefinition);

	Set<String> txLog = new HashSet<>();

	String courseId = null;
	try {
		// 1. 과정 정보 생성
		courseId = course.getId();
		courseRepository.save(course);
		txLog.add("courseSaved");

		// 2. 멤버 서비스에 생성자를 관리자로 등록 w/ courseId
		memberGateway.addManager(courseId, loginUser);
		txLog.add("managerAdded");

		// 3. 과정 게시판 서비스에 게시판 생성 w/ courseId
		boardGateway.addBoard(courseId, loginUser);
		txLog.add("boardAdded");

		// 4. 데이터베이스 커밋
		transactionManager.commit(txStatus);

	} catch (Exception originalException) {
		if (txLog.contains("boardAdded")) {
			compensateAddBoard(courseId, loginUser);
		}
		if (txLog.contains("managerAdded")) {
			compensateAddManager(courseId, loginUser);
		}
		if (txLog.contains("courseSaved")) {
			rollbackTransaction(txStatus);
		}
		String msg = "Failed to create a course:"+ txLog.toString();
		throw new RuntimeException(msg,originalException);
	}
	log.error("-_-5;;");
}

	public void createCourseOptimisticLocking_shortTransactionSpan(Course course) {
		// 0. 입력값 Validation
		UserDetails loginUser = ExampleSecurityContext.getCurrentLoginUser();
		ensureUserCanCreateCourse(loginUser);
		validateNewCourse(course);

		log.error("-_-;;01-1");
		TransactionDefinition txDefinition = new DefaultTransactionDefinition();
		log.error("-_-;;01-2");
		TransactionStatus txStatus = transactionManager
					.getTransaction(txDefinition);
		log.error("-_-;;01-3");

		Set<String> txLog = new HashSet<>();

		// 1. 과정 정보 생
		String courseId = null;
		courseId = course.getId();
		log.error("-_-;;01-4");
		courseRepository.save(course);
		log.error("-_-;;01-5");
		try {
			transactionManager.commit(txStatus);
			log.error("-_-;;01-1");
		} catch (Exception exception) {
			transactionManager.rollback(txStatus);
			throw new RuntimeException("Failed to create a course", exception);
		}

		try {
			// 2. 멤버 서비스에 생성자를 관리자로 등록 w/ courseId
			memberGateway.addManager(courseId, loginUser);
			txLog.add("managerAdded");

			// 3. 과정 게시판 서비스에 게시판 생성 w/ courseId
			boardGateway.addBoard(courseId, loginUser);
			txLog.add("boardAdded");

		} catch (Exception originalException) {
			if (txLog.contains("boardAdded")) {
				compensateAddBoard(courseId, loginUser);
			}
			if (txLog.contains("managerAdded")) {
				compensateAddManager(courseId, loginUser);
			}
			compensateSaveCourse(courseId, loginUser);
			throw new RuntimeException("Failed to create a course",
						originalException);
		}
	}

	private void rollbackTransaction(TransactionStatus tx) {
		try {
			transactionManager.rollback(tx);
		} catch (RuntimeException compensationExcention) {
			log.error("COMPENSATION TRANSACTION ERROR! {}", compensationExcention);
		}
	}

	private void compensateSaveCourse(String courseId, UserDetails loginUser) {
		try {
			courseRepository.deleteById(courseId);
		} catch (RuntimeException compensationExcention) {
			log.error("COMPENSATION TRANSACTION ERROR! {}", compensationExcention);
		}
	}

	private void compensateAddManager(String courseId, UserDetails loginUser) {
		try {
			memberGateway.removeManager(courseId, loginUser);
		} catch (RuntimeException compensationExcention) {
			log.error("COMPENSATION TRANSACTION ERROR! {}", compensationExcention);
		}
	}

	private void compensateAddBoard(String courseId, UserDetails loginUser) {
		try {
			boardGateway.removeBoard(courseId, loginUser);
		} catch (RuntimeException compensationExcention) {
			log.error("COMPENSATION TRANSACTION ERROR! {}", compensationExcention);
		}
	}

	// @Transactional
	public void createCourseOptimisticLocking_manyTryCatchBlocks(Course course) {
		// 0. 입력값 Validation
		UserDetails loginUser = ExampleSecurityContext.getCurrentLoginUser();
		ensureUserCanCreateCourse(loginUser);
		validateNewCourse(course);

		TransactionDefinition txDefinition = new DefaultTransactionDefinition();
		TransactionStatus txStatus = transactionManager
					.getTransaction(txDefinition);

		// 1. 과정 정보 생성
		String courseId = course.getId();
		courseRepository.save(course);

		// 2. 멤버 서비스에 생성자를 관리자로 등록 w/ courseId
		try {
			memberGateway.addManager(courseId, loginUser);
		} catch (Exception originalException) {
			try {
				transactionManager.rollback(txStatus);
			} catch (RuntimeException compensationExcention) {
				log.error("COMPENSATION TRANSACTION ERROR! {}",
							compensationExcention);
			}
			throw new RuntimeException("Failed to create a course",
						originalException);
		}

		// 3. 과정 게시판 서비스에 게시판 생성 w/ courseId
		try {
			boardGateway.addBoard(courseId, loginUser);

		} catch (Exception originalException) {
			try {
				memberGateway.removeManager(courseId, loginUser);
			} catch (RuntimeException compensationExcention1) {
				log.error("COMPENSATION TRANSACTION ERROR! {}",
							compensationExcention1);
			}
			try {
				transactionManager.rollback(txStatus);
			} catch (RuntimeException compensationExcention) {
				log.error("COMPENSATION TRANSACTION ERROR! {}",
							compensationExcention);
			}
			throw new RuntimeException("Failed to create a course",
						originalException);
		}

		// 4. 데이터베이스 커밋
		try {
			transactionManager.commit(txStatus);
		} catch (Exception originalException) {
			try {
				boardGateway.removeBoard(courseId, loginUser);
			} catch (RuntimeException compensationExcention) {
				log.error("COMPENSATION TRANSACTION ERROR! {}",
							compensationExcention);
			}
			try {
				memberGateway.removeManager(courseId, loginUser);
			} catch (RuntimeException compensationExcention1) {
				log.error("COMPENSATION TRANSACTION ERROR! {}",
							compensationExcention1);
			}
			try {
				transactionManager.rollback(txStatus);
			} catch (RuntimeException compensationExcention) {
				log.error("COMPENSATION TRANSACTION ERROR! {}",
							compensationExcention);
			}
			throw new RuntimeException("Failed to create a course",
						originalException);
		}

	}

//	@Transactional
//	public void createCourseOptimisticLocking3(Course course) {
//		//0. 입력값 Validation
//		UserDetails loginUser = ExampleSecurityContext.getCurrentLoginUser();
//		ensureUserCanCreateCourse(loginUser);
//		validateNewCourse(course);
//		
//		TransactionDefinition txDefinition = new DefaultTransactionDefinition();
//		TransactionStatus txStatus = transactionManager.getTransaction(txDefinition);
//		
//		//1. 과정 정보 생
//		String courseId = course.getId();
//		courseRepository.save(course);
//		
//		//2. 멤버 서비스에 생성자를 관리자로 등록 w/ courseId
//		memberGateway.addManager(courseId, loginUser);
//		
//		//3. 과정 게시판 서비스에 게시판 생성 w/ courseId
//		try {
//			boardGateway.addBoard(courseId, loginUser);
//			
//		} catch (Exception originalException) {
//			try {
//				memberGateway.removeManager(courseId, loginUser);
//			} catch  (Exception compensationExcention) {
//				log.error("COMPENSATION TRANSACTION ERROR! {}", compensationExcention);
//			}
//			throw originalException;
//		}
//	}
//	

	private void ensureUserCanCreateCourse(UserDetails loginUser) {
	}

	private void validateNewCourse(Course course) {
	}

	public Course get(String id) {
		return courseMapper.select(id);

	}

}
