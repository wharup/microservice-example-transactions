package microservices.examples.tx.course;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface CourseJPARepository  extends CrudRepository<Course, String> {

	
}

