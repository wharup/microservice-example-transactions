package microservices.examples;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;


import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

import com.github.javafaker.Book;
import com.github.javafaker.Faker;

import lombok.extern.slf4j.Slf4j;
import microservices.examples.tx.Application;
import microservices.examples.tx.course.Course;
import microservices.examples.tx.course.CourseJPARepository;
import microservices.examples.tx.course.CourseMyBatisDAO;
import microservices.examples.tx.course.CourseService;
import microservices.examples.tx.gateway.BoardGateway;
import microservices.examples.tx.gateway.MemberGateway;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Slf4j
class OptimisticLockTransactionTest2 {

	@Autowired
	CourseMyBatisDAO courseMapper;
	
	@Autowired
	CourseJPARepository courseRepository;
	
	@MockBean
	MemberGateway memberGateway;

	@MockBean
	BoardGateway boardGateway;

	@Autowired
	PlatformTransactionManager transactionManager;
	
	@Autowired
	CourseService service;

	Faker f = new Faker(new Locale("ko"));

	@BeforeEach
	void setup() {
		Mockito.reset(memberGateway);
		Mockito.reset(boardGateway);
		service.setTransactionManager(transactionManager);
	}
	
	@Test
	void 정상적으로_생성() {
		Course course = aCourse();
		service.createCourseOptimisticLocking_oneTryCatchBlock(course);
		assertNotNull(service.get(course.getId()));
	}

	@Test
	void 코스생성후_멤버서비스에_관리자생성하다_에러발생하면__생성한코스_롤백() {
		when(memberGateway.addManager(any(), any())).thenThrow(new RuntimeException("add member rest api failed!"));
		Course course = aCourse();
		try {
			service.createCourseOptimisticLocking_oneTryCatchBlock(course);
		}catch (Exception e) {
			assertEquals("Failed to create a course", e.getMessage());
			assertEquals("add member rest api failed!", e.getCause().getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("예외발생 안함");
	}
	
	@Test
	void 게시판서비스에_게시판생성하다_에러발생하면__생성한_관리자_삭제하고_코스도롤백() {
		when(boardGateway.addBoard(any(), any())).thenThrow(new RuntimeException("add board rest api failed!"));
		Course course = aCourse();
		try {
			service.createCourseOptimisticLocking_oneTryCatchBlock(course);
		} catch (Exception e) {
			verify(memberGateway).romoveManager(any(), any());
			assertEquals("Failed to create a course", e.getMessage());
			assertEquals("add board rest api failed!", e.getCause().getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("예외발생 안함");
	}

	@Test
	void 게시판생성하다_에러로_관리자_생성_보상트랜잭션_중에_에러발생하면__적절히_로그남기고_나머지_롤백() {
		when(boardGateway.addBoard(any(), any())).thenThrow(new RuntimeException("add board rest api failed!"));
		doThrow(new RuntimeException("Compensation Tx Failed")).when(memberGateway).romoveManager(any(), any());
		
		Course course = aCourse();
		try {
			service.createCourseOptimisticLocking_oneTryCatchBlock(course);
		} catch (Exception e) {
			verify(memberGateway).addManager(any(), any());
			assertEquals("Failed to create a course", e.getMessage());
			assertEquals("add board rest api failed!", e.getCause().getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("예외발생 안함");
	}
	
	@Test
	void 코스_커밋중에_에러가발생하면_게시판과_관리자_삭제() {
		PlatformTransactionManager tx = new PlatformTransactionManager() {
			public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
				return transactionManager.getTransaction(definition);
			}
			public void commit(TransactionStatus status) throws TransactionException {
				throw new RuntimeException("Failed to commit!");
			}
			public void rollback(TransactionStatus status) throws TransactionException {
				transactionManager.rollback(status);
			}
		};
		service.setTransactionManager(tx);

		Course course = aCourse();
		try {
			service.createCourseOptimisticLocking_oneTryCatchBlock(course);
		} catch (Exception e) {
			verify(memberGateway).romoveManager(any(), any());
			verify(boardGateway).removeBoard(any(), any());
			assertEquals("Failed to create a course", e.getMessage());
			assertEquals("Failed to commit!", e.getCause().getMessage());
			assertNull(service.get(course.getId()));
			return;
		}
		fail("예외발생 안함");
	}

	private Course aCourse() {

		Course c = new Course();
		Instant now = Instant.now();
		c.setCreated(now);
		c.setUpdated(now);
		c.setId(UUID.randomUUID().toString());
		Book book = f.book();
		c.setTitle(book.title());
		c.setDescription(book.genre());
		return c;
	}

}
